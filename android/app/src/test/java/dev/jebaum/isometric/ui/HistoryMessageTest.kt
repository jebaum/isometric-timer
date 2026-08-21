package dev.jebaum.isometric.ui

import dev.jebaum.isometric.RoutineViewModel
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryMessageTest {

    private val denver = ZoneId.of("America/Denver")
    private val tokyo = ZoneId.of("Asia/Tokyo")
    private val english = Locale.US
    private val gap = RoutineViewModel.MINIMUM_COMPLETION_GAP_MILLIS

    // 07:30 in Denver; the recommended gap therefore ends at 15:30 the same day.
    private val lastCompletionAt = Instant.parse("2026-08-21T13:30:00Z").toEpochMilli()
    private val recommendedAt = lastCompletionAt + gap

    // The localized time separator before AM/PM differs between JDK CLDR
    // releases, so assertions look for the clock reading and the marker apart.
    private fun assertShowsTime(message: String, clock: String, marker: String) {
        assertTrue(message, message.contains(clock))
        assertTrue(message, message.contains(marker))
    }

    @Test
    fun `no completions and no history at all reports an empty day`() {
        val message = historyMessage(
            completionsToday = 0,
            lastCompletionAt = null,
            nowMillis = lastCompletionAt,
            zone = denver,
            locale = english,
        )

        assertEquals("No routine completed today", message)
    }

    @Test
    fun `no completions today still names the next recommended time after a recent routine`() {
        // Yesterday evening's routine pushes the gap into today: 23:30 in
        // Denver on August 20, so the recommended gap ends at 7:30 today —
        // the only way "no completions today" coexists with a pending gap.
        val yesterdayEvening = Instant.parse("2026-08-21T05:30:00Z").toEpochMilli()
        val message = historyMessage(
            completionsToday = 0,
            lastCompletionAt = yesterdayEvening,
            // 01:00 in Denver on August 21, inside the gap.
            nowMillis = Instant.parse("2026-08-21T07:00:00Z").toEpochMilli(),
            zone = denver,
            locale = english,
        )

        assertTrue(message, message.startsWith("No routine today · Next after "))
        assertShowsTime(message, "7:30", "AM")
    }

    @Test
    fun `no completions today once the gap has passed reports an empty day`() {
        val message = historyMessage(
            completionsToday = 0,
            lastCompletionAt = lastCompletionAt,
            nowMillis = recommendedAt,
            zone = denver,
            locale = english,
        )

        assertEquals("No routine completed today", message)
    }

    @Test
    fun `one completion inside the gap names the next recommended time`() {
        val message = historyMessage(
            completionsToday = 1,
            lastCompletionAt = lastCompletionAt,
            nowMillis = recommendedAt - 1,
            zone = denver,
            locale = english,
        )

        assertTrue(message, message.startsWith("1 routine today · Next after "))
        assertShowsTime(message, "3:30", "PM")
    }

    @Test
    fun `the exact eight hour boundary is already ready for a second routine`() {
        val justBefore = historyMessage(
            completionsToday = 1,
            lastCompletionAt = lastCompletionAt,
            nowMillis = lastCompletionAt + gap - 1,
            zone = denver,
            locale = english,
        )
        val exactlyAt = historyMessage(
            completionsToday = 1,
            lastCompletionAt = lastCompletionAt,
            nowMillis = lastCompletionAt + gap,
            zone = denver,
            locale = english,
        )

        assertTrue(justBefore, justBefore.startsWith("1 routine today · Next after "))
        assertEquals("1 routine today · Ready for a second", exactlyAt)
    }

    @Test
    fun `a second completion reports the daily goal complete`() {
        val two = historyMessage(
            completionsToday = 2,
            lastCompletionAt = lastCompletionAt,
            nowMillis = lastCompletionAt + 1,
            zone = denver,
            locale = english,
        )
        val three = historyMessage(
            completionsToday = 3,
            lastCompletionAt = lastCompletionAt,
            nowMillis = lastCompletionAt + 1,
            zone = denver,
            locale = english,
        )

        // The goal wins over the gap: two routines never ask you to wait.
        assertEquals("2 routines today · Daily goal complete", two)
        assertEquals("3 routines today · Daily goal complete", three)
    }

    @Test
    fun `the recommended time is read in the timezone it is given`() {
        val inDenver = historyMessage(
            completionsToday = 1,
            lastCompletionAt = lastCompletionAt,
            nowMillis = lastCompletionAt + 1,
            zone = denver,
            locale = english,
        )
        val inTokyo = historyMessage(
            completionsToday = 1,
            lastCompletionAt = lastCompletionAt,
            nowMillis = lastCompletionAt + 1,
            zone = tokyo,
            locale = english,
        )

        assertShowsTime(inDenver, "3:30", "PM")
        assertShowsTime(inTokyo, "6:30", "AM")
    }

    @Test
    fun `a recommended time later today is announced as a time alone`() {
        val text = formatRecommendedAt(
            atMillis = recommendedAt,
            nowMillis = lastCompletionAt,
            zone = denver,
            locale = english,
        )

        assertTrue(text, text.startsWith("at "))
        assertShowsTime(text, "3:30", "PM")
    }

    @Test
    fun `a recommended time on another date is announced with that date`() {
        val text = formatRecommendedAt(
            atMillis = recommendedAt + Duration.ofDays(1).toMillis(),
            nowMillis = lastCompletionAt,
            zone = denver,
            locale = english,
        )

        assertTrue(text, text.startsWith("on "))
        assertTrue(text, text.contains("Aug 22, 2026"))
        assertShowsTime(text, "3:30", "PM")
    }

    @Test
    fun `whether a recommended time is today depends on the timezone`() {
        // 21:30 UTC is still August 21st in Denver but already the 22nd in Tokyo.
        val inDenver = formatRecommendedAt(recommendedAt, lastCompletionAt, denver, english)
        val inTokyo = formatRecommendedAt(recommendedAt, lastCompletionAt, tokyo, english)

        assertTrue(inDenver, inDenver.startsWith("at "))
        assertTrue(inTokyo, inTokyo.startsWith("on "))
    }
}
