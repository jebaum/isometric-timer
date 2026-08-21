package dev.jebaum.isometric.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeightTest {

    @Test
    fun `formatting drops trailing zeros but keeps meaningful decimals`() {
        assertEquals("0 lb", formatWeightLb(0.0))
        assertEquals("12 lb", formatWeightLb(12.0))
        assertEquals("12.5 lb", formatWeightLb(12.5))
        assertEquals("12.25 lb", formatWeightLb(12.25))
        assertEquals("12.05 lb", formatWeightLb(12.05))
        assertEquals("500 lb", formatWeightLb(500.0))
    }

    @Test
    fun `formatting survives a value that lost exactness in floating point`() {
        assertEquals("12.1 lb", formatWeightLb(12.100000381469727))
    }

    @Test
    fun `entry text round-trips through parsing`() {
        for (weight in listOf(0.0, 5.0, 12.5, 17.25, 500.0)) {
            assertEquals(weight, parseWeightLb(weightNumberText(weight))!!, 0.0)
        }
    }

    @Test
    fun `keystroke filter accepts partial entries and rejects malformed ones`() {
        for (partial in listOf("", "1", "12", "123", "12.", "12.5", "12.25", ".", ".5")) {
            assertTrue("rejected \"$partial\"", isWeightEntry(partial))
        }
        for (bad in listOf("1234", "12.345", "1.2.3", "-5", "5 lb", "a")) {
            assertFalse("accepted \"$bad\"", isWeightEntry(bad))
        }
    }

    @Test
    fun `parsing rejects entries the view model would refuse`() {
        assertNull(parseWeightLb(""))
        assertNull(parseWeightLb("."))
        assertNull(parseWeightLb("501"))
        assertNull(parseWeightLb("500.01"))
        assertEquals(500.0, parseWeightLb("500")!!, 0.0)
        assertEquals(0.5, parseWeightLb(".5")!!, 0.0)
        assertEquals(12.0, parseWeightLb("12.")!!, 0.0)
    }
}
