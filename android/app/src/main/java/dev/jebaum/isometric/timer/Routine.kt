package dev.jebaum.isometric.timer

import kotlin.math.ceil

/**
 * Everything about the routine that changes at most once a second.
 *
 * Progress is deliberately *not* here: it changes every frame, and a data class
 * that differs every frame defeats the structural-equality check that keeps
 * Compose from recomposing the whole screen at display refresh rate. Read it
 * from [Routine.progress] instead.
 */
data class Snapshot(
    val phase: Phase,
    /** Index into [Routine.phases]; the cue layer keys transitions off this. */
    val index: Int,
    val next: String,
    val secondsLeft: Int,
    val totalLeft: Int,
    val cycle: Int,
    val cycles: Int,
    val paused: Boolean,
    val started: Boolean,
    val done: Boolean,
) {
    /**
     * The closing seconds of a hold. Derived once here because the web version
     * computed it once too, and its two consumers — the amber countdown and the
     * warning cue — must not drift apart.
     */
    val warning: Boolean
        get() = !done && phase.kind == Kind.HOLD && secondsLeft in 1..WARNING_SECONDS
}

/**
 * Elapsed time is derived from a monotonic clock rather than accumulated per
 * tick, so a dropped frame or a trip through the background cannot make the
 * routine drift.
 */
class Routine(
    val phases: List<Phase>,
    private val now: () -> Double,
    startPaused: Boolean = true,
) {
    init {
        require(phases.isNotEmpty()) { "a routine needs at least one phase" }
    }

    val marks: List<Int> = cumulative(phases)
    val total: Int = marks.last()
    val cycles: Int = phases.last().cycle

    private val origin: Double = now()
    private var offset: Double = 0.0
    private var pausedAt: Double? = if (startPaused) origin else null

    var started: Boolean = !startPaused
        private set

    val paused: Boolean get() = pausedAt != null

    fun elapsed(): Double = (pausedAt ?: now()) - origin - offset

    fun done(): Boolean = elapsed() >= total

    fun indexAt(elapsed: Double): Int =
        minOf(bisectRight(marks, elapsed) - 1, phases.size - 1)

    fun togglePause() {
        if (done()) return

        val at = pausedAt
        if (at == null) {
            pausedAt = now()
        } else {
            offset += now() - at
            pausedAt = null
            started = true
        }
    }

    fun skip() {
        if (done()) return

        val elapsed = elapsed()
        val index = indexAt(elapsed)
        offset -= marks[index + 1] - elapsed
    }

    /**
     * How far through the current phase, 0..1. Changes every frame.
     *
     * Takes [elapsed] rather than reading the clock so a caller that needs both
     * this and a [Snapshot] can derive them from one instant — two independent
     * reads either side of a phase mark would describe different phases.
     */
    fun progressAt(elapsed: Double): Float {
        if (elapsed >= total) return 1f
        val index = indexAt(elapsed)
        val into = elapsed - marks[index]
        return (into / phases[index].seconds).coerceIn(0.0, 1.0).toFloat()
    }

    fun progress(): Float = progressAt(elapsed())

    fun snapshot(): Snapshot = snapshotAt(elapsed())

    fun snapshotAt(elapsed: Double): Snapshot {
        val done = elapsed >= total
        val index = indexAt(elapsed)
        val phase = phases[index]
        val into = if (done) phase.seconds.toDouble() else elapsed - marks[index]

        return Snapshot(
            phase = phase,
            index = index,
            next = if (done) "DONE" else phases.getOrNull(index + 1)?.label ?: "DONE",
            secondsLeft = if (done) 0 else maxOf(0, ceil(phase.seconds - into).toInt()),
            totalLeft = if (done) 0 else maxOf(0, ceil(total - elapsed).toInt()),
            cycle = phase.cycle,
            cycles = cycles,
            paused = paused,
            started = started,
            done = done,
        )
    }
}
