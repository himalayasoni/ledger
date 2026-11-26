package com.himalayasoni.ledger.core.domain

import com.himalayasoni.ledger.core.model.Entry
import com.himalayasoni.ledger.core.model.Part
import kotlin.math.abs

/*
 * Splitting one payment across categories. The entry stays one payment (one row, one UPI reference, one card
 * charge), and anything that adds up money by category asks [costByCategory], which hands each part its slice.
 *
 * Slices are proportional: a part that was 70% of the bill gets 70% of whatever the bill counts as. So a ₹500
 * order (Cigarettes ₹350 + Munchies ₹150) with ₹100 of money back counts Cigarettes ₹280 and Munchies ₹120,
 * and if it was shared, each part is shared the same way.
 */

/** Split across two or more categories. */
val Entry.isSplit: Boolean get() = parts.size >= 2

/** The categories this entry counts in: its parts', or its one category. */
fun Entry.categories(): List<String> =
    if (isSplit) parts.map { it.category }.filter { it.isNotBlank() }.distinctBy { it.lowercase() }
    else listOfNotNull(category.takeIf { it.isNotBlank() })

/** Does it count in [cat] at all (any part)? Case doesn't matter. */
fun Entry.countsIn(cat: String): Boolean = categories().any { it.equals(cat, true) }

/** What it really cost you, per category ("Other" when there's none). Sums to [cost]. */
fun Entry.costByCategory(): List<Pair<String, Double>> {
    val total = cost()
    if (!isSplit || amount <= 0) return listOf(category.ifBlank { "Other" } to total)
    return parts.groupBy { it.category.ifBlank { "Other" } }
        .map { (cat, ps) -> cat to total * ps.sumOf { it.amount } / amount }
}

/** What it cost you in [cat] alone. */
fun Entry.costIn(cat: String): Double = costByCategory().filter { it.first.equals(cat, true) }.sumOf { it.second }

/** The parts as you'd read them: "Cigarettes ₹350 · Munchies ₹150". */
fun Entry.partsLine(format: (Double) -> String): String =
    parts.joinToString(" · ") { "${it.category.ifBlank { "Other" }} ${format(it.amount)}" }

/**
 * Tidy parts before saving: drop empty ones, and keep a split only if two or more remain and they add up to
 * [amount] (within a paisa). Returns null when they don't add up, so the form can say so.
 */
fun cleanParts(parts: List<Part>, amount: Double): List<Part>? {
    val kept = parts.filter { it.amount > 0 }
    if (kept.size < 2) return emptyList()
    return if (abs(kept.sumOf { it.amount } - amount) < 0.01) kept.map { it.copy(amount = Math.round(it.amount * 100) / 100.0) } else null
}

/** The biggest part's category: what [Entry.category] holds for a split entry. */
fun mainCategory(parts: List<Part>): String = parts.maxByOrNull { it.amount }?.category.orEmpty()
