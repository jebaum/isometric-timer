package dev.jebaum.isometric.timer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * The routine's agreed shape: how a schedule is built from settings, which
 * settings are rejected, and how a remaining duration reads on the clock.
 */
class RoutineTest {

    @Test
    fun `the default routine is four cycles of right, switch, left, rest`() {
        val phases = buildSchedule(DEFAULT_SETTINGS)
        assertEquals(15, phases.size)
        assertEquals(570, phases.sumOf { it.seconds })
        assertEquals(PhaseId.LEFT_HOLD, phases.last().id)
        assertEquals(PhaseKind.HOLD, phases.last().kind)
        assertEquals(
            listOf(
                PhaseId.RIGHT_HOLD, PhaseId.SWITCH, PhaseId.LEFT_HOLD, PhaseId.REST,
                PhaseId.RIGHT_HOLD, PhaseId.SWITCH, PhaseId.LEFT_HOLD, PhaseId.REST,
                PhaseId.RIGHT_HOLD, PhaseId.SWITCH, PhaseId.LEFT_HOLD, PhaseId.REST,
                PhaseId.RIGHT_HOLD, PhaseId.SWITCH, PhaseId.LEFT_HOLD,
            ),
            phases.map { it.id },
        )
    }

    @Test
    fun `a phase sounds like the phase it is`() {
        // The whole point of the typed identity: the cue category is read off
        // the phase rather than stored beside it, so there is no phase that
        // can be called one thing and sound like another. Both holds share a
        // cue; the other two do not.
        assertEquals(
            listOf(PhaseKind.HOLD, PhaseKind.SWITCH, PhaseKind.HOLD, PhaseKind.REST),
            PhaseId.entries.map { it.kind },
        )
    }

    @Test
    fun `the next phase is the one the schedule actually runs next`() {
        var time = 0.0
        val routine = Routine(buildSchedule(DEFAULT_SETTINGS), now = { time }, startPaused = false)
        time = 1.0
        assertEquals(routine.phases[1], routine.snapshot().next)
    }

    @Test
    fun `custom durations are exact and the final cycle has no rest`() {
        val phases = buildSchedule(Settings(cycles = 2, holdSeconds = 5, switchSeconds = 2, restSeconds = 4))
        assertEquals(listOf(5, 2, 5, 4, 5, 2, 5), phases.map { it.seconds })
        assertEquals(2, phases.last().cycle)
    }

    @Test
    fun `zero switch and rest durations omit those phases`() {
        val phases = buildSchedule(Settings(cycles = 2, holdSeconds = 5, switchSeconds = 0, restSeconds = 0))
        assertEquals(
            listOf(PhaseId.RIGHT_HOLD, PhaseId.LEFT_HOLD, PhaseId.RIGHT_HOLD, PhaseId.LEFT_HOLD),
            phases.map { it.id },
        )
    }

    @Test
    fun `invalid schedule values are rejected`() {
        val invalid = listOf(
            Settings(cycles = 0, holdSeconds = 35, switchSeconds = 5, restSeconds = 90),
            Settings(cycles = 4, holdSeconds = 0, switchSeconds = 5, restSeconds = 90),
            Settings(cycles = 4, holdSeconds = 35, switchSeconds = -1, restSeconds = 90),
            Settings(cycles = 4, holdSeconds = 35, switchSeconds = 5, restSeconds = -1),
        )
        for (settings in invalid) {
            assertThrows(IllegalArgumentException::class.java) { buildSchedule(settings) }
        }
    }

    @Test
    fun `cumulative marks and duration formatting line up`() {
        val phases = buildSchedule(DEFAULT_SETTINGS)
        assertEquals(570, cumulative(phases).last())
        assertEquals("9:30", formatDuration(570))
        assertEquals("1:15", formatDuration(75))
        assertEquals("0:00", formatDuration(-2))
    }

    @Test
    fun `a routine waits for the first start`() {
        var time = 10.0
        val routine = Routine(buildSchedule(DEFAULT_SETTINGS), now = { time })
        time = 110.0
        assertEquals(0.0, routine.elapsed(), TOLERANCE)
        assertEquals(35, routine.snapshot().secondsLeft)
        assertEquals(RoutineStatus.READY, routine.snapshot().status)

        routine.togglePause()
        time = 111.0
        assertEquals(1.0, routine.elapsed(), TOLERANCE)
        assertEquals(RoutineStatus.RUNNING, routine.snapshot().status)
    }

    /**
     * The lifecycle as one sequence. Every transition the timer can make is
     * asserted on the value the snapshot publishes, so a status that stopped
     * moving — or landed on one the routine cannot actually reach, such as
     * PAUSED before it ever started — fails here rather than in the UI.
     */
    @Test
    fun `status moves ready to running to paused to running to complete`() {
        var time = 0.0
        val routine = Routine(
            buildSchedule(Settings(cycles = 1, holdSeconds = 5, switchSeconds = 0, restSeconds = 0)),
            now = { time },
        )
        assertEquals(RoutineStatus.READY, routine.status())
        assertEquals(RoutineStatus.READY, routine.snapshot().status)

        routine.togglePause() // Start
        assertEquals(RoutineStatus.RUNNING, routine.snapshot().status)

        time = 2.0
        routine.togglePause() // Pause
        assertEquals(RoutineStatus.PAUSED, routine.snapshot().status)

        time = 60.0 // held: the clock moving must not finish a paused routine
        assertEquals(RoutineStatus.PAUSED, routine.snapshot().status)

        routine.togglePause() // Resume
        assertEquals(RoutineStatus.RUNNING, routine.snapshot().status)

        time = 68.5 // 10.5s of running time against a 10s schedule
        assertEquals(RoutineStatus.COMPLETE, routine.status())
        assertEquals(RoutineStatus.COMPLETE, routine.snapshot().status)

        // And COMPLETE is terminal: neither button can move it again.
        routine.togglePause()
        routine.skip()
        assertEquals(RoutineStatus.COMPLETE, routine.snapshot().status)
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
        assertEquals(PhaseId.SWITCH, routine.snapshot().phase.id)

        routine.togglePause()
        time = 115.0
        assertEquals(40.0, routine.elapsed(), TOLERANCE)
        assertEquals(PhaseId.LEFT_HOLD, routine.snapshot().phase.id)
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
        assertEquals(RoutineStatus.COMPLETE, snapshot.status)
        assertEquals(0, snapshot.secondsLeft)
        assertEquals(0, snapshot.totalLeft)
        assertNull("a finished routine has no next phase", snapshot.next)
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
