package dev.jebaum.isometric.timer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Boundary probes for [Routine] and [Schedule]: exact marks, completion,
 * skip-while-paused, skip-on-the-last-phase, monotonicity, and clamping.
 */
class RoutineEdgeTest {

    private val short = Settings(cycles = 1, holdSeconds = 5, switchSeconds = 0, restSeconds = 0)

    @Test
    fun `elapsed exactly on a mark advances to the new phase with a full countdown`() {
        var time = 0.0
        val routine = Routine(buildSchedule(short), now = { time }, startPaused = false)
        time = 5.0
        val s = routine.snapshot()
        assertEquals(PhaseId.LEFT_HOLD, s.phase.id)
        assertEquals(5, s.secondsLeft)
        assertEquals(0f, routine.progress(), 1e-6f)
        assertEquals(RoutineStatus.RUNNING, s.status)
        // Still running, but standing on the last phase: nothing follows it.
        assertNull("the last phase has no next", s.next)
    }

    @Test
    fun `elapsed exactly on total is done and clamps to the last phase`() {
        var time = 0.0
        val routine = Routine(buildSchedule(short), now = { time }, startPaused = false)
        time = 10.0
        val s = routine.snapshot()
        assertEquals(RoutineStatus.COMPLETE, s.status)
        assertEquals(routine.phases.size - 1, s.index)
        assertEquals(0, s.secondsLeft)
        assertEquals(0, s.totalLeft)
        assertNull("a finished routine has no next phase", s.next)
        assertEquals(1f, routine.progress(), 1e-6f)
    }

    @Test
    fun `elapsed far past total stays clamped rather than indexing out of range`() {
        var time = 0.0
        val routine = Routine(buildSchedule(DEFAULT_SETTINGS), now = { time }, startPaused = false)
        time = 1_000_000.0
        val s = routine.snapshot()
        assertEquals(RoutineStatus.COMPLETE, s.status)
        assertEquals(routine.phases.size - 1, s.index)
        assertEquals(4, s.cycle)
    }

    @Test
    fun `skipping the final phase completes the routine`() {
        var time = 0.0
        val routine = Routine(buildSchedule(short), now = { time }, startPaused = false)
        routine.skip() // -> LEFT SIDE
        assertEquals(PhaseId.LEFT_HOLD, routine.snapshot().phase.id)
        routine.skip() // -> done
        assertEquals(RoutineStatus.COMPLETE, routine.status())
        assertEquals(RoutineStatus.COMPLETE, routine.snapshot().status)
    }

    @Test
    fun `skipping the final phase while paused completes the routine`() {
        var time = 0.0
        val routine = Routine(buildSchedule(short), now = { time }, startPaused = false)
        time = 6.0
        routine.togglePause() // paused inside LEFT SIDE
        routine.skip()
        // Completion outranks the hold: a routine carried off the end while
        // paused is finished, not paused, and the status says so once.
        assertEquals(
            "paused skip off the end should finish",
            RoutineStatus.COMPLETE,
            routine.status(),
        )
        val s = routine.snapshot()
        assertEquals(RoutineStatus.COMPLETE, s.status)
        assertEquals(0, s.secondsLeft)
    }

    @Test
    fun `a finished routine cannot be paused or skipped`() {
        var time = 0.0
        val routine = Routine(buildSchedule(short), now = { time }, startPaused = false)
        time = 20.0
        val before = routine.elapsed()
        routine.togglePause()
        routine.skip()
        assertEquals("the skip moved a finished routine", before, routine.elapsed(), 1e-9)
        assertEquals(RoutineStatus.COMPLETE, routine.status())

        // That the *pause* was refused needs a second clock read to show. A
        // pause takes hold by pinning elapsed to the instant it was taken, so a
        // routine that had quietly accepted one would still read 20 here.
        time = 30.0
        assertEquals("the pause took hold after the finish", 30.0, routine.elapsed(), 1e-9)
        assertEquals(RoutineStatus.COMPLETE, routine.status())
    }

    @Test
    fun `elapsed never goes negative across many pause resume skip cycles`() {
        var time = 12345.678
        val routine = Routine(buildSchedule(DEFAULT_SETTINGS), now = { time }, startPaused = false)
        var lowest = Double.MAX_VALUE
        repeat(200) { step ->
            time += 0.37
            if (step % 5 == 0) routine.togglePause()
            if (step % 11 == 0) routine.skip()
            lowest = minOf(lowest, routine.elapsed())
            routine.snapshot() // would throw if indexAt produced -1
        }
        assertTrue("elapsed went negative: $lowest", lowest >= 0.0)
    }

    @Test
    fun `secondsLeft and totalLeft never increase while the routine runs`() {
        var time = 500.0
        val routine = Routine(buildSchedule(DEFAULT_SETTINGS), now = { time }, startPaused = false)
        var lastTotal = Int.MAX_VALUE
        while (routine.status() != RoutineStatus.COMPLETE) {
            time += 0.05
            val s = routine.snapshot()
            assertTrue("totalLeft rose: $lastTotal -> ${s.totalLeft}", s.totalLeft <= lastTotal)
            lastTotal = s.totalLeft
        }
        assertEquals(0, routine.snapshot().totalLeft)
    }

    @Test
    fun `pause and resume do not drift the elapsed clock`() {
        var time = 900.0
        val routine = Routine(buildSchedule(DEFAULT_SETTINGS), now = { time }, startPaused = false)
        repeat(50) {
            time += 1.0 // one second of running
            routine.togglePause()
            time += 7.5 // paused, must not count
            routine.togglePause()
        }
        assertEquals(50.0, routine.elapsed(), 1e-6)
    }

    @Test
    fun `formatDuration renders minutes and seconds, clamping below zero`() {
        assertEquals("0:00", formatDuration(0))
        assertEquals("0:00", formatDuration(-1))
        assertEquals("0:09", formatDuration(9))
        assertEquals("1:00", formatDuration(60))
        assertEquals("9:30", formatDuration(570))
        assertEquals("60:00", formatDuration(3600))
        assertEquals("600:00", formatDuration(36000))
    }

    @Test
    fun `bisectRight boundaries land on the right side of every mark`() {
        val marks = listOf(0, 35, 40, 75)
        assertEquals(1, bisectRight(marks, 0.0))
        assertEquals(1, bisectRight(marks, 34.999))
        assertEquals(2, bisectRight(marks, 35.0))
        assertEquals(3, bisectRight(marks, 40.0))
        assertEquals(4, bisectRight(marks, 75.0))
        assertEquals(4, bisectRight(marks, 1e9))
        assertEquals(0, bisectRight(marks, -0.001))
    }

    /**
     * `skip()` is `offset -= marks[index + 1] - elapsed`, which only lands on the
     * mark if the double arithmetic round-trips. A short landing would show the
     * outgoing phase for one extra frame (and, on the final phase, would leave
     * the routine one frame short of done).
     */
    @Test
    fun `skip always lands exactly on the next mark for realistic clock values`() {
        val random = java.util.Random(20260802L)
        var short = 0
        repeat(20_000) {
            var time = 1_000.0 + random.nextDouble() * 4_000_000.0
            val routine = Routine(buildSchedule(DEFAULT_SETTINGS), now = { time }, startPaused = false)
            repeat(3) { time += random.nextDouble() * 40.0 }
            val index = routine.indexAt(routine.elapsed())
            routine.skip()
            if (routine.elapsed() < routine.marks[index + 1]) short += 1
        }
        assertEquals("skip landed short of the mark $short times", 0, short)
    }

    @Test
    fun `a single second hold still reports a full countdown at the start`() {
        var time = 0.0
        val routine = Routine(
            buildSchedule(Settings(cycles = 1, holdSeconds = 1, switchSeconds = 0, restSeconds = 0)),
            now = { time },
            startPaused = false,
        )
        assertEquals(1, routine.snapshot().secondsLeft)
        time = 0.999
        assertEquals(1, routine.snapshot().secondsLeft)
        time = 1.0
        assertEquals(PhaseId.LEFT_HOLD, routine.snapshot().phase.id)
        assertEquals(1, routine.snapshot().secondsLeft)
    }

    @Test
    fun `snapshot warning equals the reference predicate for every tick of a routine`() {
        var time = 4_223.456
        val routine = Routine(
            buildSchedule(Settings(cycles = 3, holdSeconds = 4, switchSeconds = 2, restSeconds = 3)),
            now = { time },
            startPaused = false,
        )
        var checked = 0
        while (time < 4_223.456 + 60) {
            val s = routine.snapshot()
            // The warning flag is exactly "a hold is within its closing seconds".
            val expected =
                s.phase.kind == PhaseKind.HOLD &&
                    s.secondsLeft <= WARNING_SECONDS &&
                    s.status != RoutineStatus.COMPLETE
            assertEquals("at t=$time snapshot=$s", expected, s.warning)
            checked += 1
            time += 0.017
        }
        assertTrue(checked > 3000)
    }

    /** Why `warning` can use `in 1..WARNING_SECONDS` rather than `<= `. */
    @Test
    fun `secondsLeft is never zero unless the routine is done`() {
        var time = 0.0
        val routine = Routine(
            buildSchedule(Settings(cycles = 2, holdSeconds = 1, switchSeconds = 1, restSeconds = 1)),
            now = { time },
            startPaused = false,
        )
        while (time < 12.0) {
            val s = routine.snapshot()
            if (s.status != RoutineStatus.COMPLETE) {
                assertTrue("secondsLeft=${s.secondsLeft} at t=$time", s.secondsLeft >= 1)
            }
            time += 0.001
        }
    }

    @Test
    fun `snapshot equality is not fooled by the derived warning flag`() {
        var time = 0.0
        val routine = Routine(
            buildSchedule(Settings(cycles = 1, holdSeconds = 5, switchSeconds = 0, restSeconds = 0)),
            now = { time },
            startPaused = false,
        )
        val a = routine.snapshot()
        time = 0.4
        val b = routine.snapshot()
        assertEquals(a, b)
        assertEquals(a.warning, b.warning)
        time = 3.5
        assertNotEquals(a, routine.snapshot())
    }

    /**
     * [Routine.snapshotAt] and [Routine.progressAt] exist so one elapsed value
     * can feed both. Driven from the same instant they must agree about which
     * phase is current, including exactly on a mark.
     */
    @Test
    fun `snapshotAt and progressAt agree when fed one elapsed value`() {
        val routine = Routine(
            buildSchedule(Settings(cycles = 2, holdSeconds = 5, switchSeconds = 2, restSeconds = 3)),
            now = { 0.0 },
            startPaused = false,
        )
        var elapsed = 0.0
        while (elapsed <= routine.total + 1.0) {
            val s = routine.snapshotAt(elapsed)
            val p = routine.progressAt(elapsed)
            if (s.status == RoutineStatus.COMPLETE) {
                assertEquals("progress at/after total", 1f, p, 0f)
            } else {
                // Compared in Double and only then narrowed: round-tripping
                // through Float back to a ceil() would flip by one on values
                // that land a hair under a mark, which says nothing about the
                // invariant under test — that both used the same phase index.
                val expected = ((elapsed - routine.marks[s.index]) / s.phase.seconds)
                    .coerceIn(0.0, 1.0)
                    .toFloat()
                assertEquals("at elapsed=$elapsed snapshot=$s", expected, p, 1e-6f)
            }
            elapsed += 0.013
        }
    }
}
