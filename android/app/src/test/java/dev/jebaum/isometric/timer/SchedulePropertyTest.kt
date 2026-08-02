package dev.jebaum.isometric.timer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The settings dialog now previews the routine length with the closed form
 * [Settings.totalSeconds] instead of summing [buildSchedule]. If the two ever
 * disagree the preview lies about a routine the user is about to save, so this
 * checks the whole domain the dialog can produce, plus the boundaries around it.
 */
class SchedulePropertyTest {

    private fun scheduleTotal(settings: Settings) = buildSchedule(settings).sumOf { it.seconds }

    @Test
    fun `totalSeconds equals the built schedule across the whole valid domain`() {
        val cycleValues = listOf(1, 2, 3, 4, 5, 17, 98, 99)
        val secondValues = listOf(0, 1, 2, 3, 7, 59, 60, 61, 3599, 3600)
        val holdValues = secondValues.filter { it >= 1 }

        var checked = 0
        for (cycles in cycleValues) {
            for (hold in holdValues) {
                for (switch in secondValues) {
                    for (rest in secondValues) {
                        val settings = Settings(cycles, hold, switch, rest)
                        assertTrue("$settings should be valid", settings.isValid())
                        assertEquals(
                            "closed form disagreed for $settings",
                            scheduleTotal(settings),
                            settings.totalSeconds(),
                        )
                        assertEquals(
                            "cumulative disagreed for $settings",
                            settings.totalSeconds(),
                            cumulative(buildSchedule(settings)).last(),
                        )
                        checked += 1
                    }
                }
            }
        }
        assertTrue(checked > 5000)
    }

    @Test
    fun `totalSeconds is exact for the degenerate corners`() {
        for (settings in listOf(
            Settings(cycles = 1, hold = 1, switch = 0, rest = 0),
            Settings(cycles = 1, hold = 1, switch = 0, rest = 3600), // rest never used
            Settings(cycles = 1, hold = 3600, switch = 3600, rest = 3600),
            Settings(cycles = 99, hold = 1, switch = 0, rest = 0),
            Settings(cycles = 99, hold = 3600, switch = 3600, rest = 3600),
        )) {
            assertEquals(settings.toString(), scheduleTotal(settings), settings.totalSeconds())
        }
    }

    @Test
    fun `the largest routine the dialog accepts does not overflow Int`() {
        val biggest = Settings(cycles = 99, hold = 3600, switch = 3600, rest = 3600)
        assertTrue(biggest.totalSeconds() > 0)
        assertEquals(1_422_000, biggest.totalSeconds())
        // clock() must still render it rather than wrapping negative.
        assertEquals("23700:00", clock(biggest.totalSeconds()))
    }

    @Test
    fun `isValid is strictly narrower than what buildSchedule will build`() {
        // Anything isValid() accepts, buildSchedule must accept.
        for (cycles in listOf(1, 50, 99)) {
            for (hold in listOf(1, 1800, 3600)) {
                for (switch in listOf(0, 3600)) {
                    for (rest in listOf(0, 3600)) {
                        val settings = Settings(cycles, hold, switch, rest)
                        assertTrue(settings.isValid())
                        buildSchedule(settings) // must not throw
                    }
                }
            }
        }
        // And the bounds themselves.
        assertFalse(Settings(cycles = 0).isValid())
        assertFalse(Settings(cycles = 100).isValid())
        assertFalse(Settings(hold = 0).isValid())
        assertFalse(Settings(hold = 3601).isValid())
        assertFalse(Settings(switch = -1).isValid())
        assertFalse(Settings(switch = 3601).isValid())
        assertFalse(Settings(rest = -1).isValid())
        assertFalse(Settings(rest = 3601).isValid())
    }

    @Test
    fun `cumulative still produces one more mark than there are phases`() {
        for (settings in listOf(
            Settings(cycles = 1, hold = 1, switch = 0, rest = 0),
            DEFAULT_SETTINGS,
            Settings(cycles = 3, hold = 9, switch = 0, rest = 4),
            Settings(cycles = 3, hold = 9, switch = 4, rest = 0),
        )) {
            val phases = buildSchedule(settings)
            val marks = cumulative(phases)
            assertEquals(phases.size + 1, marks.size)
            assertEquals(0, marks.first())
            assertEquals(marks, marks.sorted())
        }
    }
}
