package dev.jebaum.isometric.ui

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
