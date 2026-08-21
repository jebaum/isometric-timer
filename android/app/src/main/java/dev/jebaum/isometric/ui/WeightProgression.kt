package dev.jebaum.isometric.ui

import dev.jebaum.isometric.WeightedCompletion
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

/** Everything the weight section renders, derived from the ordered [points]. */
internal data class WeightChart(val points: List<WeightChartPoint>) {
    val minWeightLb: Double get() = points.minOf { it.weightLb }
    val maxWeightLb: Double get() = points.maxOf { it.weightLb }
    val firstDate: LocalDate get() = points.first().date
    val lastDate: LocalDate get() = points.last().date
    val earliestWeightLb: Double get() = points.first().weightLb
    val latestWeightLb: Double get() = points.last().weightLb
    val isFlat: Boolean get() = minWeightLb == maxWeightLb
    val spansMultipleDays: Boolean get() = firstDate != lastDate
}

/**
 * One point per local day, carrying the day's last recorded weight. Bodyweight
 * (0) is a measurement like any other; only an empty history has no chart.
 */
internal fun weightChart(history: List<WeightedCompletion>, zone: ZoneId): WeightChart? {
    if (history.isEmpty()) return null

    // associate keeps the last value per key, so ascending order makes each
    // day's final completion win — and leaves the keys in date order.
    val weightByDay = history
        .sortedBy { it.completedAtMillis }
        .associate { localDateOf(it.completedAtMillis, zone) to it.weightLb }

    val first = weightByDay.keys.first()
    val last = weightByDay.keys.last()
    val min = weightByDay.values.min()
    val max = weightByDay.values.max()
    val daySpan = ChronoUnit.DAYS.between(first, last).toFloat()
    val weightSpan = max - min

    val points = weightByDay.map { (date, weightLb) ->
        WeightChartPoint(
            xFraction = if (daySpan == 0f) {
                0.5f
            } else {
                ChronoUnit.DAYS.between(first, date) / daySpan
            },
            yFraction = if (weightSpan == 0.0) {
                0.5f
            } else {
                ((weightLb - min) / weightSpan).toFloat()
            },
            date = date,
            weightLb = weightLb,
        )
    }
    return WeightChart(points)
}
