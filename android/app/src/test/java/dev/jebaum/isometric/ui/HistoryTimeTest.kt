package dev.jebaum.isometric.ui

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryTimeTest {

    @Test
    fun `the same stored UTC instant is displayed on the date in the current timezone`() {
        val stored = Instant.parse("2026-08-16T00:30:00Z").toEpochMilli()

        val denver = completionCountsByDate(listOf(stored), ZoneId.of("America/Denver"))
        val tokyo = completionCountsByDate(listOf(stored), ZoneId.of("Asia/Tokyo"))

        assertEquals(mapOf(LocalDate.of(2026, 8, 15) to 1), denver)
        assertEquals(mapOf(LocalDate.of(2026, 8, 16) to 1), tokyo)
    }

    @Test
    fun `calendar month queries use UTC boundaries for the current display timezone`() {
        val august = YearMonth.of(2026, 8)

        val denver = monthRange(august, ZoneId.of("America/Denver"))
        val tokyo = monthRange(august, ZoneId.of("Asia/Tokyo"))

        assertEquals(Instant.parse("2026-08-01T06:00:00Z").toEpochMilli(), denver.startInclusive)
        assertEquals(Instant.parse("2026-09-01T06:00:00Z").toEpochMilli(), denver.endExclusive)
        assertEquals(Instant.parse("2026-07-31T15:00:00Z").toEpochMilli(), tokyo.startInclusive)
        assertEquals(Instant.parse("2026-08-31T15:00:00Z").toEpochMilli(), tokyo.endExclusive)
    }
}
