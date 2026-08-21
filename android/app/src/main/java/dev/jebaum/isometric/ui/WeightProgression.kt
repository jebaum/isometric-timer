package dev.jebaum.isometric.ui

import dev.jebaum.isometric.WeightedCompletion
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

internal data class WeightChartPoint(
    /** 0 at the first charted day, 1 at the last; 0.5 for a single day. */
    val xFraction: Float,
    /** 0 at the minimum charted weight, 1 at the maximum; 0.5 when flat. */
    val yFraction: Float,
    val date: LocalDate,
    val weightLb: Double,
)

internal data class WeightChart(
    val points: List<WeightChartPoint>,
    val minWeightLb: Double,
    val maxWeightLb: Double,
    val firstDate: LocalDate,
    val lastDate: LocalDate,
) {
    val latestWeightLb: Double get() = points.last().weightLb
}

/**
 * One point per local day, carrying the day's last recorded weight. Returns
 * null until some completion has a weight above zero: an all-bodyweight
 * history would draw as a flat line at zero and say nothing.
 */
internal fun weightChart(history: List<WeightedCompletion>, zone: ZoneId): WeightChart? {
    if (history.none { it.weightLb > 0.0 }) return null

    // associate keeps the last value per key, so ascending order makes each
    // day's final completion win.
    val weightByDay = history
        .sortedBy { it.completedAtMillis }
        .associate { completion ->
            val date = Instant.ofEpochMilli(completion.completedAtMillis)
                .atZone(zone)
                .toLocalDate()
            date to completion.weightLb
        }

    val dates = weightByDay.keys.sorted()
    val first = dates.first()
    val last = dates.last()
    val min = weightByDay.values.min()
    val max = weightByDay.values.max()
    val daySpan = ChronoUnit.DAYS.between(first, last).toFloat()
    val weightSpan = max - min

    val points = dates.map { date ->
        WeightChartPoint(
            xFraction = if (daySpan == 0f) {
                0.5f
            } else {
                ChronoUnit.DAYS.between(first, date) / daySpan
            },
            yFraction = if (weightSpan == 0.0) {
                0.5f
            } else {
                ((weightByDay.getValue(date) - min) / weightSpan).toFloat()
            },
            date = date,
            weightLb = weightByDay.getValue(date),
        )
    }
    return WeightChart(points, min, max, first, last)
}
