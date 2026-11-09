package com.himalayasoni.ledger.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * The web app's line icons, drawn from the same SVG path data (24×24, round caps),
 * so both apps use identical icons. Circles and rounded rectangles are written as arcs.
 */
object LedgerIcons {
    private fun icon(name: String, stroke: Float = 2f, vararg paths: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            for (d in paths) addPath(
                pathData = PathParser().parsePathString(d).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black), // tinted by Icon(tint = …)
                strokeLineWidth = stroke,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }.build()

    private fun circle(cx: Float, cy: Float, r: Float) =
        "M${cx - r},$cy a$r,$r 0 1,0 ${2 * r},0 a$r,$r 0 1,0 ${-2 * r},0"

    val Home = icon("home", 2f, "M4 10.5 12 4l8 6.5V20a1 1 0 0 1-1 1h-5v-6h-4v6H5a1 1 0 0 1-1-1z")
    val People = icon("people", 2f, circle(9f, 8f, 3.5f), "M2.5 20c.8-3.6 3.4-5.5 6.5-5.5s5.7 1.9 6.5 5.5", "M16 4.6a3.5 3.5 0 0 1 0 6.8M18 14.8c2 .7 3.2 2.4 3.6 5.2")
    val Plus = icon("plus", 2.6f, "M12 5v14M5 12h14")
    val Search = icon("search", 2f, circle(11f, 11f, 6.5f), "m20 20-4.2-4.2")
    val Insights = icon("insights", 2f, "M5 20V12M12 20V5M19 20v-9")
    val Settings = icon("settings", 2f, "M4 7h10M18 7h2M4 17h4M12 17h8", circle(16f, 7f, 2f), circle(10f, 17f, 2f))
    val Close = icon("close", 2.2f, "M6 6l12 12M18 6 6 18")
    val Mic = icon("mic", 2f, "M12 3a3 3 0 0 1 3 3v5a3 3 0 0 1-6 0V6a3 3 0 0 1 3-3z", "M5.5 11a6.5 6.5 0 0 0 13 0M12 17.5V21")
    val Group = People
    val Out = icon("out", 2.4f, "M7 17 17 7M9 7h8v8")
    val In = icon("in", 2.4f, "M17 7 7 17M15 17H7V9")
    val Backspace = icon("backspace", 2f, "M21 5H9l-6 7 6 7h12zM17 9l-6 6M11 9l6 6")
    val ChevronDown = icon("chevron-down", 2.2f, "M6 9l6 6 6-6")
    val ChevronUp = icon("chevron-up", 2.2f, "M6 15l6-6 6 6")
    val ChevronLeft = icon("chevron-left", 2.2f, "M15 6l-6 6 6 6")
    val ChevronRight = icon("chevron-right", 2.2f, "M9 6l6 6-6 6")
    val Card = icon("card", 2f, "M5 6h14a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2zM3 10h18M7 15h4")
    val Check = icon("check", 2.6f, "M5 12.5l4.5 4.5L19 7.5")
}
