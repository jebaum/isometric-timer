package dev.jebaum.isometric.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** The single source of colour and type for the app. */
object Palette {
    val Background = Color(0xFF121016)
    val Surface = Color(0xFF1B1722)
    val SurfaceRaised = Color(0xFF231E2D)
    val Border = Color(0x1FE2D6FF)
    val Text = Color(0xFFF5F0FF)
    val Muted = Color(0xFF968EA3)
    val Accent = Color(0xFFC8B0FF)
    val AccentStrong = Color(0xFF9D79ED)
    val Warning = Color(0xFFF2BD4B)

    /** A rest phase swaps the accent out for these, draining the screen of colour. */
    val Rest = Color(0xFF9A94A0)
    val RestStrong = Color(0xFF77717D)

    /** Text drawn on top of an accent-filled surface. */
    val OnAccent = Color(0xFF18131F)

    /**
     * Gradient stops for the whole backdrop. Both are translucent and composite
     * over [Background] rather than standing alone, so the two must be changed
     * together to keep the resulting colours where they are.
     */
    val BackdropTop = Color(0xF5231E2D)
    val BackdropBottom = Color(0xFA18151F)

    /** Fill for secondary buttons and dialog action rows. */
    val SurfaceSubtle = Color(0x0BFFFFFF)

    /** The progress track's unfilled remainder. */
    val TrackFill = Color(0x14F5F0FF)

    /** Fill behind the next-phase cell. */
    val NextBackground = Color(0x06FFFFFF)
}

/** The small caps that label a cell or head a dialog section. */
val MetaLabelStyle = TextStyle(
    fontSize = 11.sp,
    fontWeight = FontWeight.ExtraBold,
    letterSpacing = 0.18.em,
)

/**
 * The routine status line. A size larger than [MetaLabelStyle], because it
 * heads the three lines the eye actually lands on.
 */
val StatusLabelStyle = MetaLabelStyle.copy(fontSize = 13.sp)

/** Tabular figures, so counting digits do not jitter. */
val TabularStyle = TextStyle(fontFeatureSettings = "tnum")

private val ColorScheme = darkColorScheme(
    primary = Palette.Accent,
    onPrimary = Palette.OnAccent,
    background = Palette.Background,
    onBackground = Palette.Text,
    surface = Palette.Surface,
    onSurface = Palette.Text,
    surfaceContainerHigh = Palette.SurfaceRaised,
    outline = Palette.Border,
    error = Palette.Warning,
)

@Composable
fun IsometricTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = ColorScheme, content = content)
}
