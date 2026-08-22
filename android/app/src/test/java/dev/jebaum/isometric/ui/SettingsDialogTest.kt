package dev.jebaum.isometric.ui

import dev.jebaum.isometric.timer.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pins the settings dialog's field parsing and its preview line. Both are
 * presentation-only, but a dropped `toIntOrNull` guard, a swapped field, or a
 * reworded preview would compile and pass every routine test — and the parse
 * result is also what enables Save, so a regression silently refuses valid
 * input instead of failing loudly.
 */
class SettingsDialogTest {

    @Test
    fun `four numeric entries become the matching settings`() {
        assertEquals(Settings(4, 35, 5, 90), parse("4", "35", "5", "90"))
        assertEquals(Settings(1, 1, 0, 0), parse("1", "1", "0", "0"))
    }

    @Test
    fun `a blank or non-numeric entry in any field disables save`() {
        for (entries in withEachFieldReplaced(listOf("", " ", "abc", "3.5"))) {
            assertNull(
                "accepted $entries",
                parse(entries[0], entries[1], entries[2], entries[3]),
            )
        }
    }

    @Test
    fun `entries that parse but fall outside the accepted ranges disable save`() {
        assertNull(parse("0", "35", "5", "90"))
        assertNull(parse("100", "35", "5", "90"))
        assertNull(parse("4", "0", "5", "90"))
        assertNull(parse("4", "3601", "5", "90"))
        assertNull(parse("4", "35", "3601", "90"))
        assertNull(parse("4", "35", "5", "3601"))
        for (entries in withEachFieldReplaced(listOf("-1"))) {
            assertNull(
                "accepted $entries",
                parse(entries[0], entries[1], entries[2], entries[3]),
            )
        }
    }

    @Test
    fun `the preview line reports the routine or asks for valid durations`() {
        assertEquals("Enter valid whole-second durations", preview(null))
        assertEquals("4 cycles · 9:30 total", preview(Settings(4, 35, 5, 90)))
        assertEquals("1 cycle · 1:15 total", preview(Settings(1, 35, 5, 90)))
        assertEquals("2 cycles · 4:00 total", preview(Settings(2, 35, 5, 90)))
    }

    /** Every way of substituting one of [values] into one field of a valid set. */
    private fun withEachFieldReplaced(values: List<String>): List<List<String>> {
        val valid = listOf("4", "35", "5", "90")
        return valid.indices.flatMap { field ->
            values.map { value -> valid.toMutableList().also { it[field] = value } }
        }
    }
}
