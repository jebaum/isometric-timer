package dev.jebaum.isometric.timer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The boundaries of what the settings dialog will accept: which durations are
 * valid, how long the routines at the edges of that range run, and that the
 * marks derived from them stay well formed. Routine length has one source of
 * truth — [Settings.totalSeconds] measures [buildSchedule] — so the expected
 * totals here are written out by hand rather than recomputed by a second formula.
 */
class ScheduleEdgeTest {

    /** Every field at its maximum: the longest routine the dialog accepts. */
    private val biggest = Settings(cycles = 99, hold = 3600, switch = 3600, rest = 3600)

    @Test
    fun `totalSeconds matches the routine the timer will run`() {
        val expected = listOf(
            DEFAULT_SETTINGS to 570,
            // Shortest routine there is: one hold per side, nothing between.
            Settings(cycles = 1, hold = 1, switch = 0, rest = 0) to 2,
            // A single cycle has no gap to rest in, so rest cannot lengthen it.
            Settings(cycles = 1, hold = 1, switch = 0, rest = 3600) to 2,
            Settings(cycles = 99, hold = 1, switch = 0, rest = 0) to 198,
            Settings(cycles = 1, hold = 3600, switch = 3600, rest = 3600) to 10_800,
            biggest to 1_422_000,
            // Mid-range rows: every phase kind present, then exactly one omitted,
            // so a dropped SWITCH or REST phase cannot slip past the extremes above.
            Settings(cycles = 2, hold = 1, switch = 1, rest = 1) to 7,
            Settings(cycles = 3, hold = 9, switch = 0, rest = 4) to 62,
            Settings(cycles = 3, hold = 9, switch = 4, rest = 0) to 66,
        )
        for ((settings, seconds) in expected) {
            assertTrue("$settings should be valid", settings.isValid())
            assertEquals(settings.toString(), seconds, settings.totalSeconds())
        }
    }

    @Test
    fun `totalSeconds rejects settings the schedule cannot be built from`() {
        assertThrows(IllegalArgumentException::class.java) {
            Settings(cycles = 0).totalSeconds()
        }
    }

    @Test
    fun `the largest routine the dialog accepts stays cheap to build and render`() {
        val phases = buildSchedule(biggest)
        // The settings preview rebuilds and sums this on every recomposition,
        // which is only reasonable while the schedule stays this small.
        assertEquals(395, phases.size)
        // Int seconds must not wrap, and clock() must render rather than go negative.
        assertTrue(biggest.totalSeconds() > 0)
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
