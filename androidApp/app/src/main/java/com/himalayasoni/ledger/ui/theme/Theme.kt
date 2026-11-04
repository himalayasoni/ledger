package com.himalayasoni.ledger.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Ledger's own colours on top of Material's, e.g. money in vs money out. */
@Immutable
data class LedgerColors(
    val deep: Color, val onDeep: Color, val onDeepMuted: Color, val deepRaised: Color,
    val accent: Color, val onAccent: Color,
    val spent: Color, val income: Color, val loan: Color,
    val muted: Color, val line: Color,
)

private val Light = LedgerColors(
    deep = Color(0xFF0E3A33), onDeep = Color(0xFFEAF4F0), onDeepMuted = Color(0xFFA6C6BC), deepRaised = Color(0xFF175046),
    accent = Color(0xFFF0A20F), onAccent = Color(0xFF2A1B00),
    spent = Color(0xFFBF3F29), income = Color(0xFF1B8455), loan = Color(0xFF2C66AD),
    muted = Color(0xFF55665F), line = Color(0xFFD2DCD7),
)
private val Dark = LedgerColors(
    deep = Color(0xFF113F37), onDeep = Color(0xFFE8F4EF), onDeepMuted = Color(0xFF9CC2B7), deepRaised = Color(0xFF1A5248),
    accent = Color(0xFFF5B43A), onAccent = Color(0xFF231700),
    spent = Color(0xFFF0806A), income = Color(0xFF4CC48D), loan = Color(0xFF72A6EA),
    muted = Color(0xFF97A9A2), line = Color(0xFF283632),
)

val LocalLedgerColors = staticCompositionLocalOf { Light }

@Composable
fun LedgerTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val ledger = if (dark) Dark else Light
    val scheme = if (dark) darkColorScheme(
        primary = ledger.accent, onPrimary = ledger.onAccent,
        secondary = ledger.deepRaised, onSecondary = ledger.onDeep,
        background = Color(0xFF0C1412), onBackground = Color(0xFFE6EFEB),
        surface = Color(0xFF151F1C), onSurface = Color(0xFFE6EFEB),
        surfaceVariant = Color(0xFF1E2A26), onSurfaceVariant = ledger.muted, outline = ledger.line,
    ) else lightColorScheme(
        primary = ledger.deep, onPrimary = ledger.onDeep,
        secondary = ledger.accent, onSecondary = ledger.onAccent,
        background = Color(0xFFECF1EE), onBackground = Color(0xFF12201B),
        surface = Color(0xFFFFFFFF), onSurface = Color(0xFF12201B),
        surfaceVariant = Color(0xFFE1E8E4), onSurfaceVariant = ledger.muted, outline = ledger.line,
    )
    androidx.compose.runtime.CompositionLocalProvider(LocalLedgerColors provides ledger) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

/** Same colour per category everywhere (and the same as the web app). */
fun categoryColor(name: String): Color = when (name) {
    "Food" -> Color(0xFFE0862A); "Groceries" -> Color(0xFF4F9A3A); "Transport" -> Color(0xFF3B7DC4)
    "Bills" -> Color(0xFF8A5CC2); "Shopping" -> Color(0xFFD0517E); "Rent" -> Color(0xFFA0702E)
    "Health" -> Color(0xFFD1453B); "Fun" -> Color(0xFFB84FC4); "Travel" -> Color(0xFF1E9AA0)
    "Gifts" -> Color(0xFFE2683C); "Other" -> Color(0xFF7A8A84); "Salary" -> Color(0xFF1B8455)
    else -> listOf(0xFFE0862A, 0xFF3B7DC4, 0xFF4F9A3A, 0xFF8A5CC2, 0xFFD0517E, 0xFF1E9AA0, 0xFFA0702E, 0xFFD1453B, 0xFFB84FC4)
        .let { Color(it[Math.floorMod(name.hashCode(), it.size)]) }
}
