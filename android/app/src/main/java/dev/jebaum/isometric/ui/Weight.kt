package dev.jebaum.isometric.ui

import dev.jebaum.isometric.isValidWeightLb
import java.util.Locale

/**
 * Matches partial entries too, so it can filter keystrokes as they land. The
 * regex constrains only shape (digits, at most two decimals); the range is
 * [isValidWeightLb]'s job.
 */
private val WeightEntry = Regex("""\d{0,3}(\.\d{0,2})?""")

internal fun isWeightEntry(entry: String): Boolean = WeightEntry.matches(entry)

/** Returns null when the entry is not a weight the view model will accept. */
internal fun parseWeightLb(entry: String): Double? = entry
    .takeIf { isWeightEntry(it) }
    ?.toDoubleOrNull()
    ?.takeIf { isValidWeightLb(it) }

/** The bare number without trailing zeros — "12", "12.5", "12.25". */
internal fun weightNumberText(weightLb: Double): String =
    "%.2f".format(Locale.ROOT, weightLb).trimEnd('0').trimEnd('.')

internal fun formatWeightLb(weightLb: Double): String = "${weightNumberText(weightLb)} lb"
