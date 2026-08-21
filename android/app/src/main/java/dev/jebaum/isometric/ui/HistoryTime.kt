package dev.jebaum.isometric.ui

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** A local calendar range expressed as the UTC instants persisted by history. */
internal data class EpochMillisRange(val startInclusive: Long, val endExclusive: Long)

internal fun monthRange(month: YearMonth, zone: ZoneId): EpochMillisRange = EpochMillisRange(
    startInclusive = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli(),
    endExclusive = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli(),
)

internal fun dayRange(day: LocalDate, zone: ZoneId): EpochMillisRange = EpochMillisRange(
    startInclusive = day.atStartOfDay(zone).toInstant().toEpochMilli(),
    endExclusive = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
)

/** UTC timestamps are assigned to dates only at the display boundary. */
internal fun localDateOf(epochMillis: Long, zone: ZoneId): LocalDate =
    Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()

internal fun completionCountsByDate(
    completionTimestamps: List<Long>,
    zone: ZoneId,
): Map<LocalDate, Int> = completionTimestamps
    .map { localDateOf(it, zone) }
    .groupingBy { it }
    .eachCount()

/**
 * The month laid out as whole weeks of seven cells. A `null` cell pads the
 * leading and trailing edges, so the row count follows where the month starts
 * under [firstDayOfWeek] — which the caller reads from the display locale
 * rather than this function reaching for a device global.
 */
internal fun monthWeeks(month: YearMonth, firstDayOfWeek: DayOfWeek): List<List<LocalDate?>> {
    val leading = (month.atDay(1).dayOfWeek.value - firstDayOfWeek.value + 7) % 7
    val length = month.lengthOfMonth()
    val weekCount = (leading + length + 6) / 7
    return List(weekCount) { week ->
        List(7) { weekday ->
            val dayOfMonth = week * 7 + weekday - leading + 1
            if (dayOfMonth in 1..length) month.atDay(dayOfMonth) else null
        }
    }
}
