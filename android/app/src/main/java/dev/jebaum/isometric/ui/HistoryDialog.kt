package dev.jebaum.isometric.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.jebaum.isometric.WeightedCompletion
import java.time.DayOfWeek
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

@Composable
fun HistoryDialog(
    historyVersion: Int,
    nowMillis: Long,
    zone: ZoneId,
    locale: Locale,
    completionsBetween: (Long, Long) -> List<Long>,
    weightHistory: () -> List<WeightedCompletion>,
    onDismiss: () -> Unit,
) {
    val today = remember(nowMillis, zone) {
        Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    }
    var year by rememberSaveable { mutableIntStateOf(today.year) }
    var monthNumber by rememberSaveable { mutableIntStateOf(today.monthValue) }
    val month = YearMonth.of(year, monthNumber)
    val firstDayOfWeek = WeekFields.of(locale).firstDayOfWeek

    val range = monthRange(month, zone)
    val completions = remember(month, zone, historyVersion) {
        completionsBetween(range.startInclusive, range.endExclusive)
    }
    val counts = remember(completions, zone) {
        completionCountsByDate(completions, zone)
    }
    val weightChart = remember(historyVersion, zone) {
        weightChart(weightHistory(), zone)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Palette.SurfaceRaised,
            contentColor = Palette.Text,
            modifier = Modifier
                .padding(16.dp)
                .widthIn(max = 512.dp),
        ) {
            Column(
                Modifier
                    // The weight section can push the calendar past a short
                    // window, e.g. landscape or a large system font.
                    .verticalScroll(rememberScrollState())
                    .padding(22.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text("HISTORY", color = Palette.Accent, style = MetaLabelStyle)
                        Spacer(Modifier.height(4.dp))
                        Text("Routine calendar", fontSize = 23.sp, fontWeight = FontWeight.Bold)
                    }
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.semantics { contentDescription = "Close history" },
                    ) {
                        CloseIcon(tint = Palette.Muted, modifier = Modifier.size(22.dp))
                    }
                }

                Spacer(Modifier.height(12.dp))

                MonthHeader(
                    month = month,
                    locale = locale,
                    onPrevious = {
                        val previous = month.minusMonths(1)
                        year = previous.year
                        monthNumber = previous.monthValue
                    },
                    onNext = {
                        val next = month.plusMonths(1)
                        year = next.year
                        monthNumber = next.monthValue
                    },
                )

                WeekdayHeader(firstDayOfWeek = firstDayOfWeek, locale = locale)
                MonthGrid(
                    month = month,
                    today = today,
                    firstDayOfWeek = firstDayOfWeek,
                    counts = counts,
                )

                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HistoryMark(1)
                    Text(" one routine", color = Palette.Muted, fontSize = 12.sp)
                    Spacer(Modifier.size(18.dp))
                    HistoryMark(2)
                    Text(" two or more", color = Palette.Muted, fontSize = 12.sp)
                }

                if (weightChart != null) {
                    Spacer(Modifier.height(20.dp))
                    WeightSection(chart = weightChart, locale = locale)
                }
            }
        }
    }
}

@Composable
private fun MonthHeader(
    month: YearMonth,
    locale: Locale,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        TextButton(
            onClick = onPrevious,
            modifier = Modifier.semantics { contentDescription = "Previous month" },
        ) {
            Text("‹", fontSize = 28.sp)
        }
        Text(
            text = month.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale)),
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
        )
        TextButton(
            onClick = onNext,
            modifier = Modifier.semantics { contentDescription = "Next month" },
        ) {
            Text("›", fontSize = 28.sp)
        }
    }
}

@Composable
private fun WeekdayHeader(firstDayOfWeek: DayOfWeek, locale: Locale) {
    Row(Modifier.fillMaxWidth()) {
        repeat(7) { offset ->
            val day = firstDayOfWeek.plus(offset.toLong())
            Text(
                text = day.getDisplayName(TextStyle.NARROW, locale),
                color = Palette.Muted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    today: java.time.LocalDate,
    firstDayOfWeek: DayOfWeek,
    counts: Map<java.time.LocalDate, Int>,
) {
    val first = month.atDay(1)
    val leading = (first.dayOfWeek.value - firstDayOfWeek.value + 7) % 7
    val populatedCells = leading + month.lengthOfMonth()
    val cellCount = ((populatedCells + 6) / 7) * 7

    repeat(cellCount / 7) { week ->
        Row(Modifier.fillMaxWidth()) {
            repeat(7) { weekday ->
                val cell = week * 7 + weekday
                val dayNumber = cell - leading + 1
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                        .padding(2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (dayNumber in 1..month.lengthOfMonth()) {
                        val date = month.atDay(dayNumber)
                        DayCell(
                            dayNumber = dayNumber,
                            count = counts[date] ?: 0,
                            isToday = date == today,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(dayNumber: Int, count: Int, isToday: Boolean) {
    val cappedCount = count.coerceAtMost(2)
    val fill = when (cappedCount) {
        1 -> Palette.Accent.copy(alpha = 0.14f)
        2 -> Palette.Accent
        else -> Color.Transparent
    }
    val textColor = if (cappedCount == 2) Palette.OnAccent else Palette.Text
    val shape = RoundedCornerShape(10.dp)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .clip(shape)
            .background(fill)
            .then(if (isToday) Modifier.border(1.dp, Palette.Accent, shape) else Modifier)
            .semantics {
                contentDescription = when (count) {
                    0 -> "Day $dayNumber, no routines"
                    1 -> "Day $dayNumber, one routine"
                    else -> "Day $dayNumber, $count routines"
                }
            }
            .padding(vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(dayNumber.toString(), color = textColor, fontSize = 13.sp)
        Spacer(Modifier.height(1.dp))
        HistoryMark(cappedCount, color = if (cappedCount == 2) Palette.OnAccent else Palette.Accent)
    }
}

@Composable
private fun WeightSection(chart: WeightChart, locale: Locale) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("WEIGHT", color = Palette.Accent, style = MetaLabelStyle)
        Text(
            formatWeightLb(chart.latestWeightLb),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
        )
    }
    Spacer(Modifier.height(10.dp))
    Row {
        WeightChartCanvas(
            chart = chart,
            modifier = Modifier
                .weight(1f)
                .height(72.dp),
        )
        Column(
            Modifier
                .height(72.dp)
                .padding(start = 10.dp),
            verticalArrangement = if (chart.isFlat) Arrangement.Center else Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.End,
        ) {
            Text(formatWeightLb(chart.maxWeightLb), color = Palette.Muted, fontSize = 11.sp)
            if (!chart.isFlat) {
                Text(formatWeightLb(chart.minWeightLb), color = Palette.Muted, fontSize = 11.sp)
            }
        }
    }
    Spacer(Modifier.height(6.dp))
    val dateFormat = remember(locale) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
    }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(chart.firstDate.format(dateFormat), color = Palette.Muted, fontSize = 11.sp)
        if (chart.spansMultipleDays) {
            Text(chart.lastDate.format(dateFormat), color = Palette.Muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun WeightChartCanvas(chart: WeightChart, modifier: Modifier = Modifier) {
    val description = "Weight progression from " +
        "${formatWeightLb(chart.earliestWeightLb)} to " +
        formatWeightLb(chart.latestWeightLb)
    Spacer(
        modifier
            .semantics { contentDescription = description }
            // Geometry depends only on the chart and the canvas size, so it is
            // cached rather than rebuilt on every scroll frame of the dialog.
            .drawWithCache {
                // Keeps dots and line caps from clipping at the chart's extremes.
                val inset = 5.dp.toPx()
                val positions = chart.points.map { point ->
                    Offset(
                        x = inset + (size.width - 2 * inset) * point.xFraction,
                        y = inset + (size.height - 2 * inset) * (1f - point.yFraction),
                    )
                }
                // A single point strokes nothing; its dot below still draws.
                val path = Path().apply {
                    moveTo(positions.first().x, positions.first().y)
                    for (index in 1 until positions.size) {
                        lineTo(positions[index].x, positions[index].y)
                    }
                }
                val stroke = Stroke(
                    width = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                )
                val dotRadius = 3.dp.toPx()
                onDrawBehind {
                    drawPath(path, color = Palette.Accent, style = stroke)
                    positions.forEach { drawCircle(Palette.Accent, radius = dotRadius, center = it) }
                }
            },
    )
}

@Composable
private fun HistoryMark(count: Int, color: Color = Palette.Accent) {
    Text(
        text = when (count) {
            1 -> "•"
            2 -> "••"
            else -> " "
        },
        color = color,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 10.sp,
    )
}
