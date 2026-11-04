package com.himalayasoni.ledger.core.money

import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Formats rupees the Indian way: ₹1,00,000 (lakh grouping), with paise only when there are any.
 * Pure function, so it's trivial to test and identical on every screen.
 */
fun formatRupees(amount: Double, withSymbol: Boolean = true): String {
    val paise = (abs(amount) * 100).roundToLong()
    val whole = paise / 100
    val fraction = paise % 100
    val grouped = groupIndian(whole)
    val body = if (fraction == 0L) grouped else "$grouped.${fraction.toString().padStart(2, '0').trimEnd('0')}"
    return (if (withSymbol) "₹" else "") + body
}

/** 1234567 → "12,34,567": the last three digits, then pairs. */
fun groupIndian(n: Long): String {
    val s = n.toString()
    if (s.length <= 3) return s
    val last3 = s.takeLast(3)
    val rest = s.dropLast(3)
    val pairs = rest.reversed().chunked(2).joinToString(",").reversed()
    return "$pairs,$last3"
}

/** Compact form for chart labels: ₹950, ₹4.2k, ₹1.5L, ₹2Cr. */
fun formatShort(amount: Double): String {
    val n = abs(amount)
    fun trim(v: Double) = ((v * 10).roundToLong() / 10.0).let { if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString() }
    return when {
        n >= 1e7 -> "₹${trim(n / 1e7)}Cr"
        n >= 1e5 -> "₹${trim(n / 1e5)}L"
        n >= 1e3 -> "₹${trim(n / 1e3)}k"
        else -> "₹${n.roundToLong()}"
    }
}
