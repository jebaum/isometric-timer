package dev.jebaum.isometric.ui

import dev.jebaum.isometric.RoutineViewModel
import kotlin.math.roundToInt

/** Matches partial entries too, so it can filter keystrokes as they land. */
private val WeightEntry = Regex("""\d{0,3}(\.\d{0,2})?""")

internal fun isWeightEntry(entry: String): Boolean = WeightEntry.matches(entry)

/** Returns null when the entry is not a weight the view model will accept. */
internal fun parseWeightLb(entry: String): Double? = entry
    .takeIf { isWeightEntry(it) }
    ?.toDoubleOrNull()
    ?.takeIf { it <= RoutineViewModel.MAX_WEIGHT_LB }

/** The bare number without trailing zeros — "12", "12.5", "12.25". */
internal fun weightNumberText(weightLb: Double): String {
    val hundredths = (weightLb * 100).roundToInt()
    val whole = hundredths / 100
    val cents = hundredths % 100
    return when {
        cents == 0 -> "$whole"
        cents % 10 == 0 -> "$whole.${cents / 10}"
        else -> "$whole.${cents.toString().padStart(2, '0')}"
    }
}

internal fun formatWeightLb(weightLb: Double): String = "${weightNumberText(weightLb)} lb"
