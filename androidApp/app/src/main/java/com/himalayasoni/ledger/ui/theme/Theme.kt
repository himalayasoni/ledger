package com.himalayasoni.ledger.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.himalayasoni.ledger.R

/**
 * Ledger's design tokens. The names and values match the CSS variables in the web app
 * (index.html, :root), so both apps look the same. Change a colour in both places.
 */
@Immutable
data class LedgerColors(
    val bg: Color, val surface: Color, val surface2: Color,
    val ink: Color, val ink2: Color, val line: Color,
    val accent: Color, val accentInk: Color, val accentSoft: Color,
    val deep: Color, val deep2: Color, val deepInk: Color, val deepInk2: Color,
    val spent: Color, val income: Color, val loan: Color,
    val scrim: Color,
)

private val Light = LedgerColors(
    bg = Color(0xFFECF1EE), surface = Color(0xFFFFFFFF), surface2 = Color(0xFFE1E8E4),
    ink = Color(0xFF12201B), ink2 = Color(0xFF55665F), line = Color(0xFFD2DCD7),
    accent = Color(0xFFF0A20F), accentInk = Color(0xFF2A1B00), accentSoft = Color(0xFFFBE7BD),
    deep = Color(0xFF0E3A33), deep2 = Color(0xFF175046), deepInk = Color(0xFFEAF4F0), deepInk2 = Color(0xFFA6C6BC),
    spent = Color(0xFFBF3F29), income = Color(0xFF1B8455), loan = Color(0xFF2C66AD),
    scrim = Color(0x730A1814),
)
private val Dark = LedgerColors(
    bg = Color(0xFF0C1412), surface = Color(0xFF151F1C), surface2 = Color(0xFF1E2A26),
    ink = Color(0xFFE6EFEB), ink2 = Color(0xFF97A9A2), line = Color(0xFF283632),
    accent = Color(0xFFF5B43A), accentInk = Color(0xFF231700), accentSoft = Color(0xFF3A2D10),
    deep = Color(0xFF113F37), deep2 = Color(0xFF1A5248), deepInk = Color(0xFFE8F4EF), deepInk2 = Color(0xFF9CC2B7),
    spent = Color(0xFFF0806A), income = Color(0xFF4CC48D), loan = Color(0xFF72A6EA),
    scrim = Color(0x99000000),
)

val LocalLedgerColors = staticCompositionLocalOf { Light }

/** Body text: Figtree. Same as the web app's --f-body. */
val Figtree = FontFamily(
    Font(R.font.figtree_regular, FontWeight.Normal),
    Font(R.font.figtree_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.figtree_medium, FontWeight.Medium),
    Font(R.font.figtree_semibold, FontWeight.SemiBold),
    Font(R.font.figtree_bold, FontWeight.Bold),
    Font(R.font.figtree_bold, FontWeight.ExtraBold),
)

/** Headings and numbers: Bricolage Grotesque. Same as the web app's --f-display. */
val Bricolage = FontFamily(
    Font(R.font.bricolage_semibold, FontWeight.SemiBold),
    Font(R.font.bricolage_semibold, FontWeight.Medium),
    Font(R.font.bricolage_bold, FontWeight.Bold),
    Font(R.font.bricolage_extrabold, FontWeight.ExtraBold),
)

/** Big numbers (the month total, the amount you're typing): Bricolage cut for large sizes. */
val BricolageDisplay = FontFamily(Font(R.font.bricolage_display, FontWeight.ExtraBold))

/** Shorthands for the handful of text styles the app uses, mirroring the web CSS. */
object LedgerType {
    val body = TextStyle(fontFamily = Figtree, fontSize = 15.sp, lineHeight = 21.sp)
    val small = TextStyle(fontFamily = Figtree, fontSize = 13.sp, lineHeight = 18.sp)
    /** Uppercase little labels: "WHAT WAS IT?", "TODAY". */
    val label = TextStyle(fontFamily = Figtree, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.05.em)
    val title = TextStyle(fontFamily = Bricolage, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    val brand = TextStyle(fontFamily = Bricolage, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.02).em)
    val amount = TextStyle(fontFamily = Bricolage, fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum")
}

@Composable
fun LedgerTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val c = if (dark) Dark else Light
    val scheme = (if (dark) darkColorScheme() else lightColorScheme()).copy(
        primary = c.accent, onPrimary = c.accentInk,
        primaryContainer = c.accentSoft, onPrimaryContainer = c.ink,
        secondary = c.deep, onSecondary = c.deepInk,
        secondaryContainer = c.surface2, onSecondaryContainer = c.ink,
        tertiary = c.loan,
        background = c.bg, onBackground = c.ink,
        surface = c.surface, onSurface = c.ink,
        surfaceVariant = c.surface2, onSurfaceVariant = c.ink2,
        surfaceContainerLow = c.surface, surfaceContainer = c.surface, surfaceContainerHigh = c.surface,
        surfaceContainerLowest = c.surface, surfaceContainerHighest = c.surface2,
        outline = c.line, outlineVariant = c.line,
        error = c.spent, scrim = c.scrim,
        surfaceTint = c.surface, // no Material tint: sheets and cards stay exactly the web's colours
    )
    val base = Typography()
    val typography = Typography(
        displayLarge = base.displayLarge.copy(fontFamily = Bricolage),
        headlineMedium = base.headlineMedium.copy(fontFamily = Bricolage),
        titleLarge = base.titleLarge.copy(fontFamily = Bricolage, fontWeight = FontWeight.Bold),
        titleMedium = base.titleMedium.copy(fontFamily = Figtree),
        bodyLarge = LedgerType.body.copy(fontSize = 16.sp),
        bodyMedium = LedgerType.body,
        bodySmall = LedgerType.small,
        labelLarge = base.labelLarge.copy(fontFamily = Figtree, fontWeight = FontWeight.Bold),
        labelMedium = base.labelMedium.copy(fontFamily = Figtree),
        labelSmall = base.labelSmall.copy(fontFamily = Figtree),
    )
    CompositionLocalProvider(LocalLedgerColors provides c) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}

private val CATEGORY_COLORS = mapOf(
    "Food" to 0xFFE0862A, "Groceries" to 0xFF4F9A3A, "Transport" to 0xFF3B7DC4, "Bills" to 0xFF8A5CC2,
    "Shopping" to 0xFFD0517E, "Rent" to 0xFFA0702E, "Health" to 0xFFD1453B, "Fun" to 0xFFB84FC4,
    "Travel" to 0xFF1E9AA0, "Gifts" to 0xFFE2683C, "Other" to 0xFF7A8A84, "Salary" to 0xFF1B8455,
    "Freelance" to 0xFF2E8F6E,
)
private val PALETTE = listOf(0xFFE0862A, 0xFF3B7DC4, 0xFF4F9A3A, 0xFF8A5CC2, 0xFFD0517E, 0xFF1E9AA0, 0xFFA0702E, 0xFFD1453B, 0xFFB84FC4)

/**
 * Same colour per category everywhere, and the same as the web app.
 * Custom categories get a colour from their name, using the web app's exact hash
 * (h = h*31 + char, 32-bit, then abs % palette size).
 */
fun categoryColor(name: String): Color {
    CATEGORY_COLORS[name]?.let { return Color(it) }
    var h = 0
    for (ch in name.ifEmpty { "x" }) h = h * 31 + ch.code
    return Color(PALETTE[Math.abs(h) % PALETTE.size])
}
