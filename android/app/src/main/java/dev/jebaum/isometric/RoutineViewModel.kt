package dev.jebaum.isometric

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import dev.jebaum.isometric.cues.Cue
import dev.jebaum.isometric.cues.CuePlayer
import dev.jebaum.isometric.timer.Routine
import dev.jebaum.isometric.timer.RoutineStatus
import dev.jebaum.isometric.timer.Settings
import dev.jebaum.isometric.timer.Snapshot
import dev.jebaum.isometric.timer.buildSchedule
import dev.jebaum.isometric.timer.isValid
import dev.jebaum.isometric.timer.underway

/**
 * Holds the routine across configuration changes, so a rotation mid-hold does
 * not restart the timer.
 *
 * Every dependency arrives through the constructor — including the clock — so
 * the cue state machine below can be driven deterministically from a plain JVM
 * test. It is the only stateful logic in the app that a device would otherwise
 * be needed to exercise.
 */
class RoutineViewModel(
    private val store: SettingsStore,
    private val player: CuePlayer,
    private val now: () -> Double,
    private val history: CompletionHistoryStore = EmptyCompletionHistoryStore,
    private val wallNow: () -> Long = System::currentTimeMillis,
    private val failures: FailureReporter = SilentFailureReporter,
) : ViewModel() {

    /**
     * How much of the routine has already been announced.
     *
     * [index] uses -1 to mean "nothing cued yet", which is what makes the first
     * Start emit an opening cue. Bundling it with [warning] keeps the two from
     * being updated apart: setting the index without clearing the warning, or
     * resyncing one and not the other, is what previously swallowed cues.
     */
    private data class CueState(val index: Int = -1, val warning: Int = 0) {
        companion object {
            /**
             * Treat everything up to [snapshot] as already announced. A routine
             * that has not started has nothing to catch up on, and must keep the
             * -1 sentinel or its opening cue is lost.
             */
            fun syncedTo(snapshot: Snapshot): CueState =
                if (snapshot.status.underway) {
                    CueState(snapshot.index, snapshot.secondsLeft)
                } else {
                    CueState()
                }
        }
    }

    /**
     * Every read of persistent state goes through here, so none of them can
     * crash the app.
     *
     * The two that initialize state below run inside the Activity's view-model
     * factory, before the first frame; [weightHistory] and [completionsBetween]
     * run inside `remember` blocks while the history dialog composes. A throw
     * from either place is not an exception the UI can catch — it is a launch
     * that never completes, or a dialog that takes composition down with it —
     * and neither is recoverable without clearing app data. Degrading to the
     * fallback shows less than the truth, but it shows something, and the
     * failure is reported rather than silently absorbed.
     */
    private fun <T> readSafely(operation: String, fallback: T, read: () -> T): T =
        runCatching(read).getOrElse {
            failures.reportSafely(operation, it)
            fallback
        }

    /**
     * The saved state, held as the one value it is saved as. Exposed field by
     * field below so callers read what they need, but written only through
     * [persist] — there is no way to update one part and leave the rest behind.
     */
    private var preferences: RoutinePreferences by mutableStateOf(
        // Defaults are the right fallback: they are a valid schedule, so the app
        // opens on a routine the user can run and re-save over. Re-saving is
        // also the cost — the next Save writes these defaults over whatever is
        // on disk, which is recovery for the file that could not be read and
        // silent loss if the read failure was transient. Only a file that
        // refuses to be read gets here, so recovery is the case worth serving.
        readSafely("loading preferences", RoutinePreferences()) { store.load() },
    )

    val settings: Settings get() = preferences.settings

    val cuesEnabled: Boolean get() = preferences.cuesEnabled

    /** Hold weight in pounds; 0 means bodyweight only. */
    val weightLb: Double get() = preferences.weightLb

    // Null is what an empty history reads as anyway, which is the limit of this
    // fallback's honesty: an unreadable log costs the spacing warning, and the
    // screen then says what it would say for a log that is genuinely empty.
    var lastCompletionAt: Long? by mutableStateOf(
        readSafely<Long?>("reading the latest completion", fallback = null) { history.latest() },
    )
        private set

    /** Invalidates calendar queries after a new row is inserted. */
    var historyVersion: Int by mutableIntStateOf(0)
        private set

    private var routine = newRoutine()

    /** Changes at most once a second, so reading it is cheap to recompose on. */
    var snapshot: Snapshot by mutableStateOf(routine.snapshot())
        private set

    /** Changes every frame. Kept out of [snapshot] for that reason. */
    var progress: Float by mutableFloatStateOf(routine.progress())
        private set

    private var cueState = CueState()

    /** A routine is in progress, running or paused. Keeps the screen awake. */
    val active: Boolean
        get() = snapshot.status.underway

    /** The frame loop's condition: only RUNNING needs a tick every frame. */
    val running: Boolean
        get() = snapshot.status == RoutineStatus.RUNNING

    private fun newRoutine() = Routine(buildSchedule(settings), now = now)

    /** Recomputes the snapshot and emits whatever cue that transition implies. */
    private fun publish() {
        val previous = snapshot
        // One clock read feeds both, so the countdown and the bar can never
        // describe phases either side of a mark.
        val elapsed = routine.elapsed()
        val next = routine.snapshotAt(elapsed)
        snapshot = next
        progress = routine.progressAt(elapsed)

        val completed = next.status == RoutineStatus.COMPLETE &&
            previous.status != RoutineStatus.COMPLETE
        // How far past the end this frame landed. Taken from the same `elapsed`
        // the snapshot was built from, so the recorded time cannot describe a
        // different instant than the screen does.
        if (completed) rememberCompletion(overshootSeconds = elapsed - routine.total)

        // Completion history is independent of audible cues, so this return
        // deliberately comes after the transition is recorded.
        if (!cuesEnabled) return

        when (next.status) {
            // Nothing has been announced yet, and the -1 sentinel is what makes
            // the next Start open with a cue.
            RoutineStatus.READY -> cueState = CueState()

            // A routine skipped off the end without ever starting completes
            // silently: no cue opened it, so none closes it.
            RoutineStatus.COMPLETE ->
                if (completed && previous.status != RoutineStatus.READY) emit(Cue.Done)

            RoutineStatus.RUNNING, RoutineStatus.PAUSED -> when {
                // Covers both the first Start and every later phase boundary.
                // Keyed off the index rather than secondsLeft so a long frame
                // gap cannot fire a cue twice. A gap spanning an entire phase
                // announces only the phase actually landed on, which is the
                // intent.
                next.index != cueState.index -> {
                    cueState = CueState(next.index, 0)
                    emit(Cue.Enter(next.phase.kind))
                }

                next.warning && next.secondsLeft != cueState.warning -> {
                    cueState = cueState.copy(warning = next.secondsLeft)
                    emit(Cue.Warn)
                }
            }
        }
    }

    /**
     * A misbehaving vibrator HAL or a released tone generator must not take out
     * the frame loop that is driving the countdown. It does leave a log: a cue
     * that never sounds is otherwise indistinguishable from a phone on mute.
     */
    private fun emit(cue: Cue) {
        runCatching { player.play(cue) }
            .onFailure { failures.reportSafely("playing cue $cue", it) }
    }

    /**
     * A storage failure must not stop the frame loop at the finish line.
     *
     * [overshootSeconds] is how long ago the routine actually ended, measured on
     * the monotonic clock. It is normally a fraction of a frame, but the frame
     * loop parks while the app is backgrounded, so a routine that finished in
     * your pocket is first *observed* on the frame after you unlock the phone —
     * potentially hours later. Stamping that observation would file the routine
     * on the wrong calendar day across midnight and restart the eight-hour
     * spacing window from the moment you looked at the screen, so the two clock
     * domains are reconciled here: monotonic seconds converted to wall-clock
     * milliseconds and subtracted from now.
     */
    private fun rememberCompletion(overshootSeconds: Double) {
        // Never negative at the transition — `elapsed >= total` is what defines
        // it — but the floor states the intent: a same-frame finish records
        // `wallNow()` exactly, and no completion is ever stamped in the future.
        val completedAt = wallNow() - (overshootSeconds.coerceAtLeast(0.0) * 1_000).toLong()
        // The in-memory view of history moves only on success, so a failed write
        // leaves nothing half-recorded to reconcile — and is reported once, on
        // the transition, rather than on every frame that follows it.
        runCatching { history.record(completedAt, weightLb) }
            .onSuccess {
                lastCompletionAt = completedAt
                historyVersion++
            }
            .onFailure { failures.reportSafely("recording a completion in history", it) }
    }

    /** Called once per frame while the routine is running. */
    fun tick() = publish()

    fun toggle() {
        if (routine.status() == RoutineStatus.COMPLETE) {
            // Announce first: the routine can cross the finish line with no tick
            // in between (the frame clock parks while backgrounded), and
            // resetting straight through would swallow the completion cue — the
            // one cue that matters most when you are not watching the screen.
            publish()
            reset()
            return
        }
        routine.togglePause()
        publish()
    }

    fun skip() {
        routine.skip()
        publish()
    }

    fun reset() {
        routine = newRoutine()
        cueState = CueState()
        snapshot = routine.snapshot()
        progress = routine.progress()
    }

    /** The only way saved state changes: in memory and on disk together. */
    private fun persist(next: RoutinePreferences) {
        // In memory first, then best-effort to disk: a store that throws must not
        // leave the user staring at a dialog that refused a valid Save, or take
        // out the routine. The SharedPreferences implementation writes through
        // `apply()`, which reports no failures anyway.
        preferences = next
        // The value itself never reaches the log — the operation and the
        // exception are what a failed write needs explaining, and settings are
        // the user's, not a diagnostic.
        runCatching { store.save(next) }
            .onFailure { failures.reportSafely("saving preferences", it) }
    }

    /**
     * The settings dialog's Save, applied as one transaction. The dialog stages
     * both fields and commits them together, so a partial application — cues
     * saved and settings rejected — is a state the user never asked for.
     */
    fun updatePreferences(settings: Settings, cuesEnabled: Boolean) {
        // Checked before anything is mutated or persisted: `reset()` would throw
        // out of buildSchedule *after* the bad value had already reached disk.
        require(settings.isValid()) { "settings outside the accepted range: $settings" }
        val settingsChanged = settings != preferences.settings
        persist(preferences.copy(settings = settings, cuesEnabled = cuesEnabled))

        // A finished routine resets too, even when nothing about the schedule
        // moved: saving from the completion screen is how you get back to READY,
        // and leaving it done would strand the Save behind a "Done" button.
        if (settingsChanged || snapshot.status == RoutineStatus.COMPLETE) {
            // Once. `reset()` clears the cue state itself, so there is nothing
            // left to resynchronize afterwards.
            reset()
        } else if (cuesEnabled) {
            // The screen disables settings while a routine is running, but the
            // view model does not depend on that: turning cues on part way
            // through must not re-announce what has already gone by.
            cueState = CueState.syncedTo(snapshot)
        }
    }

    /**
     * Unlike [updatePreferences] this must not rebuild the routine: the weight
     * does not change the schedule, only what a completion records. It is the
     * one preference editable mid-routine.
     */
    fun updateWeight(valueLb: Double) {
        require(isValidWeightLb(valueLb)) { "weight outside the accepted range: $valueLb" }
        persist(preferences.copy(weightLb = quantizeWeightLb(valueLb)))
    }

    fun weightHistory(): List<WeightedCompletion> =
        readSafely("reading the weight history", emptyList()) { history.weightHistory() }

    fun completionsBetween(startInclusiveMillis: Long, endExclusiveMillis: Long): List<Long> {
        // Checked here so the guard below cannot absorb it. A reversed range is
        // a caller building the wrong month, not a device fault: swallowing it
        // would draw an empty calendar and file the bug under storage failures.
        require(startInclusiveMillis <= endExclusiveMillis) { "history range is reversed" }
        return readSafely("reading completions in a date range", emptyList()) {
            history.between(startInclusiveMillis, endExclusiveMillis)
        }
    }

    /** The eight-hour mark, but only while it is still in the future. */
    fun spacingWarningAt(atMillis: Long = wallNow()): Long? = lastCompletionAt
        ?.plus(MINIMUM_COMPLETION_GAP_MILLIS)
        ?.takeIf { atMillis < it }

    fun currentWallTimeMillis(): Long = wallNow()

    override fun onCleared() {
        // Both halves are guarded independently, and that independence is the
        // point: teardown is the one path with nothing left to protect, so a
        // handle that refuses to let go must neither crash out of it — the
        // framework is clearing the view model, and there is no screen left to
        // explain it — nor skip the release that comes after it.
        runCatching { player.release() }
            .onFailure { failures.reportSafely("releasing the cue player", it) }
        runCatching { history.close() }
            .onFailure { failures.reportSafely("closing the history database", it) }
    }

    companion object {
        const val MINIMUM_COMPLETION_GAP_MILLIS = 8L * 60L * 60L * 1_000L
    }
}
