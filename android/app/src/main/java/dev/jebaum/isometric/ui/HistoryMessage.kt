package dev.jebaum.isometric.ui

import dev.jebaum.isometric.RoutineViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Pure readiness/history wording for the timer screen. */
internal fun historyMessage(
    completionsToday: Int,
    lastCompletionAt: Long?,
    nowMillis: Long,
    zone: ZoneId,
    locale: Locale,
): String {
    val nextRecommendedAt = lastCompletionAt?.plus(RoutineViewModel.MINIMUM_COMPLETION_GAP_MILLIS)
    return when {
        completionsToday >= 2 -> "$completionsToday routines today · Daily goal complete"
        completionsToday == 1 && nextRecommendedAt != null && nowMillis < nextRecommendedAt ->
            "1 routine today · Next after ${formatTime(nextRecommendedAt, zone, locale)}"
        completionsToday == 1 -> "1 routine today · Ready for a second"
        nextRecommendedAt != null && nowMillis < nextRecommendedAt ->
            "No routine today · Next after ${formatTime(nextRecommendedAt, zone, locale)}"
        else -> "No routine completed today"
    }
}

private fun formatTime(atMillis: Long, zone: ZoneId, locale: Locale): String =
    Instant.ofEpochMilli(atMillis)
        .atZone(zone)
        .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale))

internal fun formatRecommendedAt(
    atMillis: Long,
    nowMillis: Long,
    zone: ZoneId,
    locale: Locale,
): String {
    val target = Instant.ofEpochMilli(atMillis).atZone(zone)
    val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    return if (target.toLocalDate() == today) {
        "at ${target.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale))}"
    } else {
        "on ${target.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale))}"
    }
}
