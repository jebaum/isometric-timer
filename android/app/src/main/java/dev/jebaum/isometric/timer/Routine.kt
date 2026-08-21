package dev.jebaum.isometric.timer

import kotlin.math.ceil

/**
 * Where the routine stands in its lifecycle.
 *
 * One value rather than independent flags, because these four states are not
 * independent: PAUSED implies started, and COMPLETE outranks a pause. Separate
 * booleans would leave combinations the timer can never reach — "paused before
 * it ever started" — merely unreachable rather than unrepresentable.
 */
enum class RoutineStatus {
    /** Built, never started. The countdown shows the opening phase in full. */
    READY,

    /** Started, clock advancing. */
    RUNNING,

    /** Started, clock held. */
    PAUSED,

    /** The whole schedule has elapsed. */
    COMPLETE,
}

/**
 * A routine that is started and not yet finished — what keeps the screen awake,
 * the Skip button live, and the settings dialog shut. Derived from the status
 * rather than published alongside it, so the two cannot disagree.
 */
val RoutineStatus.underway: Boolean
    get() = when (this) {
        RoutineStatus.RUNNING, RoutineStatus.PAUSED -> true
        RoutineStatus.READY, RoutineStatus.COMPLETE -> false
    }

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
    /**
     * What follows this phase, or null when nothing does — the routine is
     * finished, or standing on its last phase. Null rather than a "DONE"
     * string: the timer core has no business deciding what the screen calls
     * the end of a routine, and a sentinel label is one the schedule could
     * also, in principle, produce.
     */
    val next: Phase?,
    val secondsLeft: Int,
    val totalLeft: Int,
    val cycle: Int,
    val cycles: Int,
    val status: RoutineStatus,
) {
    /**
     * The closing seconds of a hold. Derived once here because its two
     * consumers — the amber countdown and the warning cue — must not drift
     * apart.
     */
    val warning: Boolean
        get() = status != RoutineStatus.COMPLETE &&
            phase.kind == PhaseKind.HOLD &&
            secondsLeft in 1..WARNING_SECONDS
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
    private var hasStarted: Boolean = !startPaused

    fun elapsed(): Double = (pausedAt ?: now()) - origin - offset

    /** The one lifecycle fact this class publishes. */
    fun status(): RoutineStatus = statusAt(elapsed())

    /**
     * Completion is read off the elapsed value rather than the fields, so
     * [snapshotAt] can describe an instant the clock is not sitting on. The
     * arm order enforces the precedence documented on [RoutineStatus].
     */
    private fun statusAt(elapsed: Double): RoutineStatus = when {
        elapsed >= total -> RoutineStatus.COMPLETE
        !hasStarted -> RoutineStatus.READY
        pausedAt != null -> RoutineStatus.PAUSED
        else -> RoutineStatus.RUNNING
    }

    fun indexAt(elapsed: Double): Int =
        minOf(bisectRight(marks, elapsed) - 1, phases.size - 1)

    fun togglePause() {
        if (status() == RoutineStatus.COMPLETE) return

        val at = pausedAt
        if (at == null) {
            pausedAt = now()
        } else {
            offset += now() - at
            pausedAt = null
            hasStarted = true
        }
    }

    fun skip() {
        if (status() == RoutineStatus.COMPLETE) return

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
        val status = statusAt(elapsed)
        val done = status == RoutineStatus.COMPLETE
        val index = indexAt(elapsed)
        val phase = phases[index]
        val into = if (done) phase.seconds.toDouble() else elapsed - marks[index]

        return Snapshot(
            phase = phase,
            index = index,
            next = if (done) null else phases.getOrNull(index + 1),
            secondsLeft = if (done) 0 else maxOf(0, ceil(phase.seconds - into).toInt()),
            totalLeft = if (done) 0 else maxOf(0, ceil(total - elapsed).toInt()),
            cycle = phase.cycle,
            cycles = cycles,
            status = status,
        )
    }
}
