package com.myapp.motrava.utils

import kotlin.math.roundToLong

fun Double.formatDecimal(decimals: Int = 1): String {
    if (this.isNaN() || this.isInfinite()) return "0"
    var multiplier = 1.0
    repeat(decimals) { multiplier *= 10.0 }
    val rounded = (this * multiplier).roundToLong() / multiplier
    return if (decimals == 0) rounded.toLong().toString() else rounded.toString()
}

fun Float.formatDecimal(decimals: Int = 1): String = this.toDouble().formatDecimal(decimals)
