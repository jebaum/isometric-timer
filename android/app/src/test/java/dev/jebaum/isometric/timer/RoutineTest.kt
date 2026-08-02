package dev.jebaum.isometric.timer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ported from `timer.test.js` in the repository root. The two implementations
 * are kept deliberately parallel, so a change to the routine logic should land
 * in both and be provable by both suites.
 *
 * The web suite's final case asserts on the web manifest and service-worker
 * cache list; it has no counterpart here and stays on the JavaScript side.
 */
class RoutineTest {

    @Test
    fun `the default schedule matches the web app`() {
        val phases = buildSchedule(DEFAULT_SETTINGS)
        assertEquals(15, phases.size)
        assertEquals(570, phases.sumOf { it.seconds })
        assertEquals(LABEL_LEFT, phases.last().label)
        assertEquals(Kind.HOLD, phases.last().kind)
        assertEquals(
            listOf(
                LABEL_RIGHT, LABEL_SWITCH, LABEL_LEFT, LABEL_REST,
                LABEL_RIGHT, LABEL_SWITCH, LABEL_LEFT, LABEL_REST,
                LABEL_RIGHT, LABEL_SWITCH, LABEL_LEFT, LABEL_REST,
                LABEL_RIGHT, LABEL_SWITCH, LABEL_LEFT,
            ),
            phases.map { it.label },
        )
    }

    @Test
    fun `custom durations are exact and the final cycle has no rest`() {
        val phases = buildSchedule(Settings(cycles = 2, hold = 5, switch = 2, rest = 4))
        assertEquals(listOf(5, 2, 5, 4, 5, 2, 5), phases.map { it.seconds })
        assertEquals(2, phases.last().cycle)
    }

    @Test
    fun `zero switch and rest durations omit those phases`() {
        val phases = buildSchedule(Settings(cycles = 2, hold = 5, switch = 0, rest = 0))
        assertEquals(
            listOf(LABEL_RIGHT, LABEL_LEFT, LABEL_RIGHT, LABEL_LEFT),
            phases.map { it.label },
        )
    }

    @Test
    fun `invalid schedule values are rejected`() {
        // The web suite's fractional-cycles case is absent by construction: Int
        // cannot hold 2.5.
        val invalid = listOf(
            Settings(cycles = 0, hold = 35, switch = 5, rest = 90),
            Settings(cycles = 4, hold = 0, switch = 5, rest = 90),
            Settings(cycles = 4, hold = 35, switch = -1, rest = 90),
            Settings(cycles = 4, hold = 35, switch = 5, rest = -1),
        )
        for (settings in invalid) {
            assertThrows(IllegalArgumentException::class.java) { buildSchedule(settings) }
        }
    }

    @Test
    fun `cumulative marks and clock formatting line up`() {
        val phases = buildSchedule(DEFAULT_SETTINGS)
        assertEquals(570, cumulative(phases).last())
        assertEquals("9:30", clock(570))
        assertEquals("1:15", clock(75))
        assertEquals("0:00", clock(-2))
    }

    @Test
    fun `a routine waits for the first start`() {
        var time = 10.0
        val routine = Routine(buildSchedule(DEFAULT_SETTINGS), now = { time })
        time = 110.0
        assertEquals(0.0, routine.elapsed(), TOLERANCE)
        assertEquals(35, routine.snapshot().secondsLeft)
        assertFalse(routine.snapshot().started)

        routine.togglePause()
        time = 111.0
        assertEquals(1.0, routine.elapsed(), TOLERANCE)
        assertTrue(routine.snapshot().started)
    }

    @Test
    fun `pause, resume, and skip preserve monotonic timing`() {
        var time = 0.0
        val routine = Routine(
            buildSchedule(DEFAULT_SETTINGS),
            now = { time },
            startPaused = false,
        )

        time = 10.0
        routine.togglePause()
        time = 110.0
        assertEquals(10.0, routine.elapsed(), TOLERANCE)

        routine.skip()
        assertEquals(35.0, routine.elapsed(), TOLERANCE)
        assertEquals(LABEL_SWITCH, routine.snapshot().phase.label)

        routine.togglePause()
        time = 115.0
        assertEquals(40.0, routine.elapsed(), TOLERANCE)
        assertEquals(LABEL_LEFT, routine.snapshot().phase.label)
    }

    @Test
    fun `skipping through every phase completes at zero`() {
        var time = 0.0
        val routine = Routine(
            buildSchedule(DEFAULT_SETTINGS),
            now = { time },
            startPaused = false,
        )
        repeat(routine.phases.size + 5) { routine.skip() }

        val snapshot = routine.snapshot()
        assertTrue(snapshot.done)
        assertEquals(0, snapshot.secondsLeft)
        assertEquals(0, snapshot.totalLeft)
        assertEquals("DONE", snapshot.next)
    }

    @Test
    fun `a routine needs at least one phase`() {
        assertThrows(IllegalArgumentException::class.java) {
            Routine(emptyList(), now = { 0.0 })
        }
    }

    private companion object {
        const val TOLERANCE = 1e-9
    }
}
