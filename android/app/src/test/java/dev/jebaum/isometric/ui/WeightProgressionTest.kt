package dev.jebaum.isometric.ui

import dev.jebaum.isometric.WeightedCompletion
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WeightProgressionTest {

    private val denver = ZoneId.of("America/Denver")

    private fun at(year: Int, month: Int, day: Int, hour: Int, zone: ZoneId = denver): Long =
        ZonedDateTime.of(year, month, day, hour, 0, 0, 0, zone).toInstant().toEpochMilli()

    @Test
    fun `no chart until some completion carries a weight above zero`() {
        assertNull(weightChart(emptyList(), denver))
        assertNull(
            weightChart(
                listOf(
                    WeightedCompletion(at(2026, 8, 1, 9), 0.0),
                    WeightedCompletion(at(2026, 8, 2, 9), 0.0),
                ),
                denver,
            ),
        )
    }

    @Test
    fun `a day's last completion provides that day's weight`() {
        val chart = weightChart(
            listOf(
                WeightedCompletion(at(2026, 8, 1, 7), 10.0),
                WeightedCompletion(at(2026, 8, 1, 19), 12.5),
            ),
            denver,
        )!!

        assertEquals(1, chart.points.size)
        assertEquals(12.5, chart.points.single().weightLb, 0.0)
        assertEquals(12.5, chart.latestWeightLb, 0.0)
    }

    @Test
    fun `fractions span the date and weight ranges`() {
        val chart = weightChart(
            listOf(
                WeightedCompletion(at(2026, 8, 1, 9), 10.0),
                WeightedCompletion(at(2026, 8, 3, 9), 11.0),
                WeightedCompletion(at(2026, 8, 5, 9), 14.0),
            ),
            denver,
        )!!

        assertEquals(LocalDate.of(2026, 8, 1), chart.firstDate)
        assertEquals(LocalDate.of(2026, 8, 5), chart.lastDate)
        assertEquals(10.0, chart.minWeightLb, 0.0)
        assertEquals(14.0, chart.maxWeightLb, 0.0)

        val (first, middle, last) = chart.points
        assertEquals(0.0f, first.xFraction, 1e-6f)
        assertEquals(0.5f, middle.xFraction, 1e-6f)
        assertEquals(1.0f, last.xFraction, 1e-6f)
        assertEquals(0.0f, first.yFraction, 1e-6f)
        assertEquals(0.25f, middle.yFraction, 1e-6f)
        assertEquals(1.0f, last.yFraction, 1e-6f)
    }

    @Test
    fun `a single day and a flat weight sit in the middle of both axes`() {
        val single = weightChart(
            listOf(WeightedCompletion(at(2026, 8, 1, 9), 12.5)),
            denver,
        )!!
        assertEquals(0.5f, single.points.single().xFraction, 0f)
        assertEquals(0.5f, single.points.single().yFraction, 0f)

        val flat = weightChart(
            listOf(
                WeightedCompletion(at(2026, 8, 1, 9), 12.5),
                WeightedCompletion(at(2026, 8, 4, 9), 12.5),
            ),
            denver,
        )!!
        assertEquals(listOf(0.5f, 0.5f), flat.points.map { it.yFraction })
        assertEquals(12.5, flat.minWeightLb, 0.0)
        assertEquals(12.5, flat.maxWeightLb, 0.0)
    }

    @Test
    fun `bodyweight days chart at zero once any weighted day exists`() {
        val chart = weightChart(
            listOf(
                WeightedCompletion(at(2026, 8, 1, 9), 0.0),
                WeightedCompletion(at(2026, 8, 2, 9), 10.0),
            ),
            denver,
        )!!

        assertEquals(2, chart.points.size)
        assertEquals(0.0, chart.minWeightLb, 0.0)
        assertEquals(0.0f, chart.points.first().yFraction, 0f)
    }

    @Test
    fun `days are assigned in the display timezone`() {
        // 00:30 UTC on the 16th is still the 15th in Denver.
        val stored = at(2026, 8, 16, 0, ZoneId.of("UTC")) + 30 * 60 * 1_000L
        val chart = weightChart(listOf(WeightedCompletion(stored, 10.0)), denver)!!

        assertEquals(LocalDate.of(2026, 8, 15), chart.points.single().date)
    }
}
