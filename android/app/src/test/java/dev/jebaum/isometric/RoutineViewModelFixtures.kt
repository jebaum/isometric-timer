package dev.jebaum.isometric

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.jebaum.isometric.cues.Cue
import dev.jebaum.isometric.cues.CuePlayer
import dev.jebaum.isometric.timer.PhaseKind

/**
 * One frame at 60 Hz. The real loop ticks on whatever vsync the display
 * provides, so cue dispatch must not depend on any particular cadence; the
 * coarse-cadence test passes a different [FakeClock.advanceBy] step to prove it.
 */
internal const val FRAME_SECONDS = 1.0 / 60.0

/**
 * The clock the view model reads, which moves only when a test says so.
 *
 * Nothing here advances on its own, so every cue assertion is reproducible: the
 * two ways time passes on a phone — frame by frame, or in one jump while the
 * app was backgrounded — are the two [advanceBy] overloads.
 */
internal class FakeClock(private var seconds: Double = 1_000.0) {

    /** The `now` to hand a [RoutineViewModel]; reads the current value lazily. */
    val now: () -> Double = { seconds }

    /** Jumps the clock with no ticks in between, as a background gap would. */
    fun advanceBy(seconds: Double) {
        this.seconds += seconds
    }

    /**
     * Runs [model] through [seconds] of [step]-sized frames.
     *
     * The `running` guard mirrors the real loop, so a routine that stops partway
     * through an advance stops being ticked. Pass [step] explicitly when a test
     * turns on where the frames land.
     */
    fun advanceBy(model: RoutineViewModel, seconds: Double, step: Double = FRAME_SECONDS) {
        repeat((seconds / step).toInt()) {
            advanceBy(step)
            if (model.running) model.tick()
        }
    }
}

/**
 * Stands in for the tone generator and vibrator, holding what would have been
 * played. [onPlay] makes the hardware misbehave on demand.
 */
internal class RecordingCuePlayer(private val onPlay: (Cue) -> Unit = {}) : CuePlayer {

    val played = mutableListOf<Cue>()

    var releases = 0
        private set

    /** The phases announced, in order. */
    val enters: List<PhaseKind> get() = played.filterIsInstance<Cue.Enter>().map { it.kind }

    val warnCount: Int get() = played.count { it is Cue.Warn }

    val doneCount: Int get() = played.count { it is Cue.Done }

    override fun play(cue: Cue) {
        played += cue
        onPlay(cue)
    }

    override fun release() {
        releases += 1
    }
}

/** Stands in for the completion database, holding the rows in memory. */
internal class FakeCompletionHistory(
    initial: List<Long> = emptyList(),
    /** Stands in for a locked or corrupted database. */
    private val recordFails: Boolean = false,
) : CompletionHistoryStore {

    private val completions = initial.map { WeightedCompletion(it, 0.0) }.toMutableList()

    var closes = 0
        private set

    /** Derived, so the two views of the log cannot fall out of sync. */
    val entries: List<Long> get() = completions.map { it.completedAtMillis }

    override fun record(completedAtMillis: Long, weightLb: Double) {
        if (recordFails) error("history database is locked")
        completions += WeightedCompletion(completedAtMillis, weightLb)
    }

    override fun latest(): Long? = entries.maxOrNull()

    override fun between(startInclusiveMillis: Long, endExclusiveMillis: Long): List<Long> =
        entries.filter { it in startInclusiveMillis until endExclusiveMillis }.sorted()

    override fun weightHistory(): List<WeightedCompletion> =
        completions.sortedBy { it.completedAtMillis }

    override fun close() {
        closes++
    }
}

/**
 * Ends [model]'s lifecycle the way the framework does.
 *
 * `onCleared()` is protected, so the only honest way to run it is to hand the
 * instance to a store and clear the store — which is exactly what the host
 * Activity's teardown does, and needs no reflection to reach.
 */
internal fun clearViewModel(model: RoutineViewModel) {
    val store = ViewModelStore()
    ViewModelProvider.create(
        store,
        viewModelFactory { initializer { model } },
    )[RoutineViewModel::class]
    store.clear()
}
