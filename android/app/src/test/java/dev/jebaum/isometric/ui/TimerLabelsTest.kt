package dev.jebaum.isometric.ui

import dev.jebaum.isometric.timer.PhaseId
import dev.jebaum.isometric.timer.RoutineStatus
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the exact words the timer screen shows for each phase and each routine
 * status. The mappings are presentation-only and file-private in spirit, but a
 * swapped RIGHT/LEFT arm — or a Pause button that says Resume — would compile
 * and pass every timer test, so the strings are asserted here.
 */
class TimerLabelsTest {

    @Test
    fun `every phase renders its own side or name`() {
        assertEquals(
            listOf("RIGHT SIDE", "SWITCH", "LEFT SIDE", "REST"),
            PhaseId.entries.map { it.label },
        )
    }

    @Test
    fun `a routine with nothing left says DONE`() {
        assertEquals("DONE", NOTHING_LEFT)
    }

    @Test
    fun `every status names itself in the state pill`() {
        assertEquals(
            listOf("READY", "IN PROGRESS", "PAUSED", "COMPLETE"),
            RoutineStatus.entries.map { stateLabel(it) },
        )
    }

    @Test
    fun `every status says what the primary button does next`() {
        assertEquals(
            listOf("Start", "Pause", "Resume", "Again"),
            RoutineStatus.entries.map { startLabel(it) },
        )
    }
}
