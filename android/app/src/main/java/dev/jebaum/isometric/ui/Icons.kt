package dev.jebaum.isometric.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap

/**
 * The two inline SVGs from `index.html`, redrawn against the same 24x24 grid so
 * the shapes stay identical to the web app rather than being approximated from
 * the Material icon set.
 */
private data class Segment(val x1: Float, val y1: Float, val x2: Float, val y2: Float)

// M4 7h10M18 7h2M4 17h2M10 17h10M14 4v6M10 14v6
private val SLIDERS = listOf(
    Segment(4f, 7f, 14f, 7f),
    Segment(18f, 7f, 20f, 7f),
    Segment(4f, 17f, 6f, 17f),
    Segment(10f, 17f, 20f, 17f),
    Segment(14f, 4f, 14f, 10f),
    Segment(10f, 14f, 10f, 20f),
)

// m6 6 12 12M18 6 6 18
private val CLOSE = listOf(
    Segment(6f, 6f, 18f, 18f),
    Segment(18f, 6f, 6f, 18f),
)

@Composable
private fun StrokeIcon(tint: Color, modifier: Modifier, segments: List<Segment>) {
    Canvas(modifier) {
        val unit = size.minDimension / 24f
        for (segment in segments) {
            drawLine(
                color = tint,
                start = Offset(segment.x1 * unit, segment.y1 * unit),
                end = Offset(segment.x2 * unit, segment.y2 * unit),
                strokeWidth = 1.8f * unit,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
fun SlidersIcon(tint: Color, modifier: Modifier = Modifier) =
    StrokeIcon(tint, modifier, SLIDERS)

@Composable
fun CloseIcon(tint: Color, modifier: Modifier = Modifier) =
    StrokeIcon(tint, modifier, CLOSE)
