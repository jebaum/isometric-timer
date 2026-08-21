package dev.jebaum.isometric.ui

import dev.jebaum.isometric.timer.PhaseId
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the exact words the timer screen shows for each phase. The mapping is
 * presentation-only and file-private in spirit, but a swapped RIGHT/LEFT arm
 * would compile and pass every timer test, so the strings are asserted here.
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
}
