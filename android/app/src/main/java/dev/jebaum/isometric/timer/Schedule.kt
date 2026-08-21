package dev.jebaum.isometric.timer

/**
 * Routine construction. Deliberately free of Android imports so it runs as a
 * plain JVM unit test.
 */

data class Settings(
    val cycles: Int = 4,
    val holdSeconds: Int = 35,
    val switchSeconds: Int = 5,
    val restSeconds: Int = 90,
)

val DEFAULT_SETTINGS = Settings()

/** Seconds of a hold that count as the closing warning. */
const val WARNING_SECONDS = 3

/** What the settings form will accept. */
val CYCLE_RANGE = 1..99
val SECONDS_RANGE = 0..3600

/**
 * Which of a cycle's four phases this is — the one thing a phase *is*.
 *
 * Everything else about a phase follows from it: [PhaseId.kind] below decides
 * what it sounds like, and the screen decides what it is called. Neither is
 * stored alongside this, so a phase that says REST cannot warn like a hold.
 */
enum class PhaseId {
    RIGHT_HOLD,
    SWITCH,
    LEFT_HOLD,
    REST,
}

/**
 * Phases that share a cue. Coarser than [PhaseId] on purpose: the two holds
 * are the same sound, because what the cue tells you is "start holding", not
 * which side you are on — you already know that.
 */
enum class PhaseKind { HOLD, SWITCH, REST }

val PhaseId.kind: PhaseKind
    get() = when (this) {
        PhaseId.RIGHT_HOLD, PhaseId.LEFT_HOLD -> PhaseKind.HOLD
        PhaseId.SWITCH -> PhaseKind.SWITCH
        PhaseId.REST -> PhaseKind.REST
    }

data class Phase(
    val id: PhaseId,
    val seconds: Int,
    val cycle: Int,
) {
    /** Derived rather than stored, so it cannot contradict [id]. */
    val kind: PhaseKind get() = id.kind
}

/**
 * Whether these durations describe a routine the UI will accept. Broader than
 * [buildSchedule]'s own guard, which only rejects what it cannot build at all;
 * this is the predicate for user input, so both the settings form and anything
 * restored from storage agree on one answer.
 */
fun Settings.isValid(): Boolean =
    cycles in CYCLE_RANGE &&
        holdSeconds in 1..SECONDS_RANGE.last &&
        switchSeconds in SECONDS_RANGE &&
        restSeconds in SECONDS_RANGE

/**
 * Total runtime, read off the last mark of the schedule the timer will actually
 * run — the same number [Routine.total] runs on, so the settings preview cannot
 * drift from it. The largest routine [isValid] accepts is under 400 phases, so
 * building it to measure it costs nothing worth saving.
 *
 * Throws whatever [buildSchedule] throws for durations it cannot build, so call
 * this on settings that already passed [isValid].
 */
fun Settings.totalSeconds(): Int =
    cumulative(buildSchedule(this)).last()

private fun requireAtLeast(name: String, value: Int, minimum: Int) {
    require(value >= minimum) {
        val qualifier = if (minimum == 0) "zero or greater" else "greater than zero"
        "$name must be a whole number $qualifier"
    }
}

fun buildSchedule(settings: Settings): List<Phase> {
    requireAtLeast("cycles", settings.cycles, 1)
    requireAtLeast("hold", settings.holdSeconds, 1)
    requireAtLeast("switch", settings.switchSeconds, 0)
    requireAtLeast("rest", settings.restSeconds, 0)

    return buildList {
        for (index in 0 until settings.cycles) {
            val cycle = index + 1
            add(Phase(PhaseId.RIGHT_HOLD, settings.holdSeconds, cycle))
            if (settings.switchSeconds > 0) {
                add(Phase(PhaseId.SWITCH, settings.switchSeconds, cycle))
            }
            add(Phase(PhaseId.LEFT_HOLD, settings.holdSeconds, cycle))
            if (index < settings.cycles - 1 && settings.restSeconds > 0) {
                add(Phase(PhaseId.REST, settings.restSeconds, cycle))
            }
        }
    }
}

fun cumulative(phases: List<Phase>): List<Int> =
    phases.runningFold(0) { total, phase -> total + phase.seconds }

fun formatDuration(seconds: Int): String {
    val rounded = maxOf(0, seconds)
    return "${rounded / 60}:${(rounded % 60).toString().padStart(2, '0')}"
}

internal fun bisectRight(values: List<Int>, target: Double): Int {
    var low = 0
    var high = values.size
    while (low < high) {
        val middle = (low + high) / 2
        if (target < values[middle]) high = middle else low = middle + 1
    }
    return low
}
