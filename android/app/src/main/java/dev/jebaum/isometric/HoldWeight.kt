package dev.jebaum.isometric

import kotlin.math.roundToInt

/**
 * The hold-weight domain rule in one place: pounds, 0 (bodyweight) through
 * [MAX_WEIGHT_LB], quantized to hundredths. Entry, view-model validation,
 * storage, and display all defer to these.
 */
const val MAX_WEIGHT_LB = 500.0

fun isValidWeightLb(valueLb: Double): Boolean = valueLb in 0.0..MAX_WEIGHT_LB

/** The storage grain: hundredths of a pound, kept exact as an integer. */
fun weightLbHundredths(valueLb: Double): Int = (valueLb * 100).roundToInt()

fun quantizeWeightLb(valueLb: Double): Double = weightLbHundredths(valueLb) / 100.0
