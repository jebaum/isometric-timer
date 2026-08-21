package dev.jebaum.isometric.timer

/**
 * Routine construction. Deliberately free of Android imports so it runs as a
 * plain JVM unit test.
 */

data class Settings(
    val cycles: Int = 4,
    val hold: Int = 35,
    val switch: Int = 5,
    val rest: Int = 90,
)

val DEFAULT_SETTINGS = Settings()

/** Seconds of a hold that count as the closing warning. */
const val WARNING_SECONDS = 3

/** What the settings form will accept. */
val CYCLE_RANGE = 1..99
val SECONDS_RANGE = 0..3600

enum class Kind { HOLD, SWITCH, REST }

data class Phase(
    val label: String,
    val seconds: Int,
    val kind: Kind,
    val cycle: Int,
)

const val LABEL_RIGHT = "RIGHT SIDE"
const val LABEL_SWITCH = "SWITCH"
const val LABEL_LEFT = "LEFT SIDE"
const val LABEL_REST = "REST"

/**
 * Whether these durations describe a routine the UI will accept. Broader than
 * [buildSchedule]'s own guard, which only rejects what it cannot build at all;
 * this is the predicate for user input, so both the settings form and anything
 * restored from storage agree on one answer.
 */
fun Settings.isValid(): Boolean =
    cycles in CYCLE_RANGE &&
        hold in 1..SECONDS_RANGE.last &&
        switch in SECONDS_RANGE &&
        rest in SECONDS_RANGE

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
    requireAtLeast("hold", settings.hold, 1)
    requireAtLeast("switch", settings.switch, 0)
    requireAtLeast("rest", settings.rest, 0)

    return buildList {
        for (index in 0 until settings.cycles) {
            val cycle = index + 1
            add(Phase(LABEL_RIGHT, settings.hold, Kind.HOLD, cycle))
            if (settings.switch > 0) {
                add(Phase(LABEL_SWITCH, settings.switch, Kind.SWITCH, cycle))
            }
            add(Phase(LABEL_LEFT, settings.hold, Kind.HOLD, cycle))
            if (index < settings.cycles - 1 && settings.rest > 0) {
                add(Phase(LABEL_REST, settings.rest, Kind.REST, cycle))
            }
        }
    }
}

fun cumulative(phases: List<Phase>): List<Int> =
    phases.runningFold(0) { total, phase -> total + phase.seconds }

fun clock(seconds: Int): String {
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
