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
import dev.jebaum.isometric.timer.Settings
import dev.jebaum.isometric.timer.Snapshot
import dev.jebaum.isometric.timer.buildSchedule
import dev.jebaum.isometric.timer.isValid

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
                if (snapshot.started && !snapshot.done) {
                    CueState(snapshot.index, snapshot.secondsLeft)
                } else {
                    CueState()
                }
        }
    }

    /**
     * The saved state, held as the one value it is saved as. Exposed field by
     * field below so callers read what they need, but written only through
     * [persist] — there is no way to update one part and leave the rest behind.
     */
    private var preferences: RoutinePreferences by mutableStateOf(store.load())

    val settings: Settings get() = preferences.settings

    val cuesEnabled: Boolean get() = preferences.cuesEnabled

    /** Hold weight in pounds; 0 means bodyweight only. */
    val weightLb: Double get() = preferences.weightLb

    var lastCompletionAt: Long? by mutableStateOf(history.latest())
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

    val active: Boolean
        get() = snapshot.started && !snapshot.done

    val running: Boolean
        get() = active && !snapshot.paused

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

        val completed = next.done && !previous.done
        if (completed) rememberCompletion()

        // Completion history is independent of audible cues, so this return
        // deliberately comes after the transition is recorded.
        if (!cuesEnabled) return

        if (!next.started) {
            cueState = CueState()
            return
        }

        when {
            next.done -> if (completed) emit(Cue.Done)

            // Covers both the first Start and every later phase boundary. Keyed
            // off the index rather than secondsLeft so a long frame gap cannot
            // fire a cue twice. A gap spanning an entire phase announces only
            // the phase actually landed on, which is the intent.
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

    /**
     * A misbehaving vibrator HAL or a released tone generator must not take out
     * the frame loop that is driving the countdown.
     */
    private fun emit(cue: Cue) {
        runCatching { player.play(cue) }
    }

    /** A storage failure must not stop the frame loop at the finish line. */
    private fun rememberCompletion() {
        val completedAt = wallNow()
        runCatching { history.record(completedAt, weightLb) }.onSuccess {
            lastCompletionAt = completedAt
            historyVersion++
        }
    }

    /** Called once per frame while the routine is running. */
    fun tick() = publish()

    fun toggle() {
        if (routine.done()) {
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
        runCatching { store.save(next) }
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
        if (settingsChanged || snapshot.done) {
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

    fun weightHistory(): List<WeightedCompletion> = history.weightHistory()

    fun completionsBetween(startInclusiveMillis: Long, endExclusiveMillis: Long): List<Long> =
        history.between(startInclusiveMillis, endExclusiveMillis)

    /** The eight-hour mark, but only while it is still in the future. */
    fun spacingWarningAt(atMillis: Long = wallNow()): Long? = lastCompletionAt
        ?.plus(MINIMUM_COMPLETION_GAP_MILLIS)
        ?.takeIf { atMillis < it }

    fun currentWallTimeMillis(): Long = wallNow()

    override fun onCleared() {
        player.release()
        history.close()
    }

    companion object {
        const val MINIMUM_COMPLETION_GAP_MILLIS = 8L * 60L * 60L * 1_000L
    }
}
