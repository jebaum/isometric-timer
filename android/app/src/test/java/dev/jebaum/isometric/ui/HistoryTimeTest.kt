package dev.jebaum.isometric.ui

import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.WeekFields
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryTimeTest {

    private val denver = ZoneId.of("America/Denver")

    @Test
    fun `the same stored UTC instant is displayed on the date in the current timezone`() {
        val stored = Instant.parse("2026-08-16T00:30:00Z").toEpochMilli()

        val denverCounts = completionCountsByDate(listOf(stored), denver)
        val tokyoCounts = completionCountsByDate(listOf(stored), ZoneId.of("Asia/Tokyo"))

        assertEquals(mapOf(LocalDate.of(2026, 8, 15) to 1), denverCounts)
        assertEquals(mapOf(LocalDate.of(2026, 8, 16) to 1), tokyoCounts)
    }

    @Test
    fun `calendar month queries use UTC boundaries for the current display timezone`() {
        val august = YearMonth.of(2026, 8)

        val denverRange = monthRange(august, denver)
        val tokyoRange = monthRange(august, ZoneId.of("Asia/Tokyo"))

        assertEquals(Instant.parse("2026-08-01T06:00:00Z").toEpochMilli(), denverRange.startInclusive)
        assertEquals(Instant.parse("2026-09-01T06:00:00Z").toEpochMilli(), denverRange.endExclusive)
        assertEquals(Instant.parse("2026-07-31T15:00:00Z").toEpochMilli(), tokyoRange.startInclusive)
        assertEquals(Instant.parse("2026-08-31T15:00:00Z").toEpochMilli(), tokyoRange.endExclusive)
    }

    @Test
    fun `an ordinary local day spans twenty four hours`() {
        val range = dayRange(LocalDate.of(2026, 8, 21), denver)

        assertEquals(Instant.parse("2026-08-21T06:00:00Z").toEpochMilli(), range.startInclusive)
        assertEquals(Instant.parse("2026-08-22T06:00:00Z").toEpochMilli(), range.endExclusive)
        assertEquals(24L, range.hours())
    }

    @Test
    fun `the day the clocks spring forward is only twenty three hours long`() {
        val range = dayRange(LocalDate.of(2026, 3, 8), denver)

        assertEquals(Instant.parse("2026-03-08T07:00:00Z").toEpochMilli(), range.startInclusive)
        assertEquals(Instant.parse("2026-03-09T06:00:00Z").toEpochMilli(), range.endExclusive)
        assertEquals(23L, range.hours())
    }

    @Test
    fun `the day the clocks fall back is twenty five hours long`() {
        val range = dayRange(LocalDate.of(2026, 11, 1), denver)

        assertEquals(Instant.parse("2026-11-01T06:00:00Z").toEpochMilli(), range.startInclusive)
        assertEquals(Instant.parse("2026-11-02T07:00:00Z").toEpochMilli(), range.endExclusive)
        assertEquals(25L, range.hours())
    }

    @Test
    fun `a completion in the repeated hour still counts toward its own local day`() {
        // 01:30 local happens twice on the fall-back day; both instants belong
        // to November 1st, and the hour after the day ends does not.
        val firstOneThirty = Instant.parse("2026-11-01T07:30:00Z").toEpochMilli()
        val repeatedOneThirty = Instant.parse("2026-11-01T08:30:00Z").toEpochMilli()
        val nextDay = Instant.parse("2026-11-02T07:30:00Z").toEpochMilli()
        val range = dayRange(LocalDate.of(2026, 11, 1), denver)

        assertTrue(firstOneThirty in range.startInclusive until range.endExclusive)
        assertTrue(repeatedOneThirty in range.startInclusive until range.endExclusive)
        assertTrue(nextDay >= range.endExclusive)
        assertEquals(
            mapOf(LocalDate.of(2026, 11, 1) to 2, LocalDate.of(2026, 11, 2) to 1),
            completionCountsByDate(listOf(firstOneThirty, repeatedOneThirty, nextDay), denver),
        )
    }

    @Test
    fun `a Sunday-first locale opens February 2026 in the very first cell`() {
        val firstDayOfWeek = WeekFields.of(Locale.US).firstDayOfWeek
        assertEquals(DayOfWeek.SUNDAY, firstDayOfWeek)

        val weeks = monthWeeks(YearMonth.of(2026, 2), firstDayOfWeek)

        assertTrue(weeks.all { it.size == 7 })
        assertEquals(4, weeks.size)
        assertEquals(LocalDate.of(2026, 2, 1), weeks.first().first())
        assertEquals(LocalDate.of(2026, 2, 28), weeks.last().last())
    }

    @Test
    fun `a Monday-first locale pushes February 2026 into a fifth row`() {
        val firstDayOfWeek = WeekFields.of(Locale.UK).firstDayOfWeek
        assertEquals(DayOfWeek.MONDAY, firstDayOfWeek)

        val weeks = monthWeeks(YearMonth.of(2026, 2), firstDayOfWeek)

        assertTrue(weeks.all { it.size == 7 })
        assertEquals(5, weeks.size)
        assertEquals(List(6) { null }, weeks.first().take(6))
        assertEquals(LocalDate.of(2026, 2, 1), weeks.first()[6])
        assertEquals(LocalDate.of(2026, 2, 28), weeks[4][5])
        assertEquals(null, weeks[4][6])
    }

    @Test
    fun `a month starting on a Saturday leaves a leading and a trailing gap`() {
        // August 1st 2026 is a Saturday, the widest leading gap a Sunday-first
        // calendar can have.
        val weeks = monthWeeks(YearMonth.of(2026, 8), DayOfWeek.SUNDAY)

        assertEquals(6, weeks.size)
        assertEquals(List(6) { null }, weeks.first().take(6))
        assertEquals(LocalDate.of(2026, 8, 1), weeks.first()[6])
        assertEquals(LocalDate.of(2026, 8, 31), weeks[5][1])
        assertEquals(List(5) { null }, weeks[5].drop(2))
    }

    @Test
    fun `every day of the month appears exactly once and in order`() {
        val month = YearMonth.of(2026, 8)

        for (firstDayOfWeek in DayOfWeek.entries) {
            val weeks = monthWeeks(month, firstDayOfWeek)

            assertEquals(
                "first day of week $firstDayOfWeek",
                (1..month.lengthOfMonth()).map { month.atDay(it) },
                weeks.flatten().filterNotNull(),
            )
            // Each column must hold the weekday its header names, which is
            // what pins the leading offset for every week start.
            for (week in weeks) {
                week.forEachIndexed { column, date ->
                    if (date != null) {
                        assertEquals(
                            "column $column under $firstDayOfWeek",
                            firstDayOfWeek.plus(column.toLong()),
                            date.dayOfWeek,
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `a leap February fills whole rows when it starts on the first weekday`() {
        // 29 days from the first cell needs a fifth row for the last day alone.
        val weeks = monthWeeks(YearMonth.of(2032, 2), DayOfWeek.SUNDAY)

        assertEquals(DayOfWeek.SUNDAY, LocalDate.of(2032, 2, 1).dayOfWeek)
        assertEquals(5, weeks.size)
        assertEquals(LocalDate.of(2032, 2, 29), weeks[4][0])
        assertEquals(List(6) { null }, weeks[4].drop(1))
    }

    private fun EpochMillisRange.hours(): Long =
        Duration.ofMillis(endExclusive - startInclusive).toHours()
}
