package com.himalayasoni.ledger.ui.insights

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.himalayasoni.ledger.core.money.formatRupees
import com.himalayasoni.ledger.core.money.formatShort
import com.himalayasoni.ledger.ui.components.EntryRowItem
import com.himalayasoni.ledger.ui.components.Hairline
import com.himalayasoni.ledger.ui.components.LedgerCard
import com.himalayasoni.ledger.ui.components.LedgerChip
import com.himalayasoni.ledger.ui.components.LedgerIcons
import com.himalayasoni.ledger.ui.components.PageHeader
import com.himalayasoni.ledger.ui.components.pressable
import com.himalayasoni.ledger.ui.theme.Bricolage
import com.himalayasoni.ledger.ui.theme.BricolageDisplay
import com.himalayasoni.ledger.ui.theme.Figtree
import com.himalayasoni.ledger.ui.theme.LedgerType
import com.himalayasoni.ledger.ui.theme.LocalLedgerColors
import com.himalayasoni.ledger.ui.theme.categoryColor
import kotlin.math.roundToInt

/** The Insights tab (web: viewInsights). Stateless: state in, taps out through [InsightsActions]. */
@Composable
fun InsightsTab(
    state: InsightsUiState?,
    actions: InsightsActions,
    onSettings: () -> Unit,
    onOpen: (String) -> Unit,
    onPerson: (String) -> Unit,
    onSeeCategory: (String) -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 140.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { PageHeader("Insights", onSettings) }
        if (state == null) return@LazyColumn
        val s = state
        item { Segmented(listOf("Month", "Year"), if (s.selection.yearView) 1 else 0) { actions.yearView(it == 1) } }
        item { PeriodNav(s.title, s.canGoNext, onPrev = { actions.step(-1) }, onNext = { actions.step(1) }) }
        item { HeroCard(s.hero) }
        s.pace?.let { p -> item { Card("Spending pace") { PaceChart(p) } } }
        s.heat?.let { h -> item { Card("Day by day") { Heatmap(h, actions::pickDay, onOpen) } } }
        item { Card("Where it went") { CategoryBars(s.categories, actions::toggleCategory, onSeeCategory) } }
        s.trend?.let { t -> item { Card("Category trends") { TrendCard(t, actions) } } }
        item {
            Card(s.columns.title) {
                Columns(s.columns.bars, height = 120, onTap = { b -> b.month?.let(actions::openMonth) })
                s.columns.caption?.let { Caption(it) }
            }
        }
        s.weekdays?.let { w ->
            item {
                Card("By day of the week") {
                    Columns(
                        w.averages.mapIndexed { i, v -> Bar(listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")[i], v, if (v > 0) formatShort(v) else "", i == w.top, null) },
                        height = 100, onTap = null,
                    )
                    Caption(w.caption)
                }
            }
        }
        if (s.regulars.isNotEmpty()) item { Card("Your regulars") { s.regulars.forEach { KeyValue(it) } } }
        s.othersTitle?.let { title -> item { Card(title) { s.others.forEach { o -> KeyValue(o) { onPerson(o.name) } } } } }
        if (s.biggest.isNotEmpty()) item {
            Card("Biggest spends") {
                LedgerCard {
                    Column { s.biggest.forEachIndexed { i, r -> if (i > 0) Hairline(); EntryRowItem(r) { onOpen(r.id) } } }
                }
            }
        }
        if (s.paidWith.isNotEmpty()) item { Card("Paid with") { s.paidWith.forEach { KeyValue(it) } } }
    }
}

/* ---------- building blocks (web: .card, .seg, .mnav, .kv, .cap) ---------- */

@Composable
private fun Card(title: String, content: @Composable () -> Unit) {
    val c = LocalLedgerColors.current
    LedgerCard(radius = 18.dp) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, fontFamily = Bricolage, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = c.ink)
            content()
        }
    }
}

@Composable
private fun Caption(text: String) = Text(text, style = LedgerType.small, color = LocalLedgerColors.current.ink2)

@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val c = LocalLedgerColors.current
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.surface2).padding(4.dp)) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier.weight(1f).height(38.dp).clip(RoundedCornerShape(9.dp)).background(if (on) c.surface else Color.Transparent)
                    .pressable(shrink = false) { onSelect(i) },
                Alignment.Center,
            ) { Text(label, fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold, color = if (on) c.ink else c.ink2, style = LedgerType.body) }
        }
    }
}

@Composable
private fun PeriodNav(title: String, canNext: Boolean, onPrev: () -> Unit, onNext: () -> Unit) {
    val c = LocalLedgerColors.current
    LedgerCard(radius = 14.dp) {
        Row(Modifier.padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).pressable(onClick = onPrev), Alignment.Center) { Icon(LedgerIcons.ChevronLeft, "Previous", tint = c.ink) }
            Text(title, fontFamily = Bricolage, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = c.ink, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            Box(Modifier.size(44.dp).alpha(if (canNext) 1f else 0.3f).pressable(enabled = canNext, onClick = onNext), Alignment.Center) {
                Icon(LedgerIcons.ChevronRight, "Next", tint = c.ink)
            }
        }
    }
}

@Composable
private fun HeroCard(h: Hero) {
    val c = LocalLedgerColors.current
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(c.deep).padding(20.dp)) {
        Text(h.label.uppercase(), color = c.deepInk2, fontWeight = FontWeight.SemiBold, letterSpacing = 0.04.em, style = LedgerType.small)
        Text(h.spent, color = c.deepInk, fontFamily = BricolageDisplay, fontWeight = FontWeight.ExtraBold, fontSize = 40.sp, letterSpacing = (-0.03).em,
            style = LedgerType.body.copy(fontFeatureSettings = "tnum"), modifier = Modifier.padding(top = 4.dp))
        Text(h.vs, color = c.deepInk2, style = LedgerType.body.copy(fontSize = 14.sp), modifier = Modifier.padding(top = 4.dp))
        Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            h.minis.forEach { m ->
                Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(c.deep2).padding(10.dp)) {
                    Text(m.label, color = c.deepInk2, style = LedgerType.small.copy(fontSize = 12.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(m.value, color = c.deepInk, style = LedgerType.amount.copy(fontSize = 17.sp), maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun KeyValue(l: Line, onClick: (() -> Unit)? = null) {
    val c = LocalLedgerColors.current
    Column {
        Row(
            Modifier.fillMaxWidth().then(if (onClick != null) Modifier.pressable(shrink = false, onClick = onClick) else Modifier).padding(vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                buildAnnotatedString {
                    append(l.name)
                    l.note?.let { withStyle(SpanStyle(color = c.ink2)) { append(" · $it") } }
                },
                style = LedgerType.body, color = c.ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(l.amount, style = LedgerType.amount, color = c.ink)
        }
        Hairline()
    }
}

/* ---------- spending pace: two running totals (web: paceChart) ---------- */

@Composable
private fun PaceChart(p: Pace) {
    val c = LocalLedgerColors.current
    val measurer = rememberTextMeasurer()
    var day by remember(p) { mutableStateOf<Int?>(null) }
    val label = TextStyle(fontFamily = Figtree, fontSize = 10.sp, color = c.ink2)

    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        LegendLine(p.curName, c.deep, dashed = false)
        LegendLine(p.prevName, c.ink2, dashed = true)
    }
    // geometry in the web's 340×170 viewBox units, scaled to the canvas
    val W = 340f; val H = 170f; val L = 44f; val R = 12f; val T = 12f; val B = 24f
    fun dayAt(x: Float, width: Float): Int {
        val vx = x / width * W
        return ((1 + (vx - L) / (W - L - R) * (p.days - 1)).roundToInt()).coerceIn(1, p.days)
    }
    Canvas(
        Modifier.fillMaxWidth().aspectRatio(W / H)
            .pointerInput(p) {
                detectTapGestures { day = dayAt(it.x, size.width.toFloat()) }
            }
            .pointerInput(p) {
                detectDragGestures(onDragStart = { day = dayAt(it.x, size.width.toFloat()) }) { ch, _ -> day = dayAt(ch.position.x, size.width.toFloat()) }
            },
    ) {
        val k = size.width / W
        fun sx(d: Int) = (L + (d - 1f) / (p.days - 1f) * (W - L - R)) * k
        fun sy(v: Double) = (T + (1 - (v / p.ymax).toFloat()) * (H - T - B)) * k
        listOf(0.0, p.ymax / 2, p.ymax).forEach { t ->
            drawLine(c.line, Offset(L * k, sy(t)), Offset((W - R) * k, sy(t)), strokeWidth = 1.dp.toPx())
            val txt = if (t > 0) formatShort(t) else "₹0"
            val m = measurer.measure(txt, label)
            drawText(m, topLeft = Offset((L - 6) * k - m.size.width, sy(t) - m.size.height / 2))
        }
        listOf(1, 8, 15, 22, p.days).distinct().forEach { d ->
            val m = measurer.measure("$d", label)
            drawText(m, topLeft = Offset(sx(d) - m.size.width / 2, (H - 4) * k - m.size.height))
        }
        fun line(a: List<Double>, n: Int): Path = Path().apply {
            for (i in 1..n) { val x = sx(i); val y = sy(a[i]); if (i == 1) moveTo(x, y) else lineTo(x, y) }
        }
        val stroke = 2.5.dp.toPx()
        drawPath(line(p.prev, p.prev.size - 1), c.ink2.copy(alpha = 0.7f),
            style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))))
        val upto = p.cur.size - 1
        drawPath(line(p.cur, upto), c.deep, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(c.income, radius = 4.5.dp.toPx(), center = Offset(sx(upto), sy(p.cur[upto])))
        day?.let { d -> drawLine(c.ink2, Offset(sx(d), T * k), Offset(sx(d), (H - B) * k), strokeWidth = 1.dp.toPx()) }
    }
    val cap = day?.let { d ->
        val cv = p.cur.getOrNull(d); val pv = p.prev.getOrNull(d)
        "Day $d: " + (if (cv != null) "${formatRupees(cv)} in ${p.curShort}" else "not reached yet in ${p.curShort}") +
            (if (pv != null) " · ${formatRupees(pv)} in ${p.prevShort}" else "")
    } ?: p.caption
    Caption(cap)
    Caption("Touch or drag across the chart to compare any day.")
}

@Composable
private fun LegendLine(name: String, color: Color, dashed: Boolean) {
    val c = LocalLedgerColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(18.dp, 4.dp)) {
            drawLine(color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = 2.5.dp.toPx(),
                pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(6f, 4f)) else null)
        }
        Spacer(Modifier.width(6.dp))
        Text(name, style = LedgerType.small, color = c.ink2)
    }
}

/* ---------- day by day (web: heatmap) ---------- */

@Composable
private fun Heatmap(h: Heat, onDay: (java.time.LocalDate) -> Unit, onOpen: (String) -> Unit) {
    val c = LocalLedgerColors.current
    fun shade(l: Int) = when (l) { 0 -> c.surface2; 1 -> c.accent.copy(alpha = 0.28f); 2 -> c.accent.copy(alpha = 0.5f); 3 -> c.accent.copy(alpha = 0.75f); else -> c.accent }
    Row { listOf("M", "T", "W", "T", "F", "S", "S").forEach { Text(it, style = LedgerType.small, color = c.ink2, textAlign = TextAlign.Center, modifier = Modifier.weight(1f)) } }
    val cells: List<HeatDay?> = List(h.lead) { null } + h.days
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        cells.chunked(7).forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (i in 0 until 7) {
                    val d = week.getOrNull(i)
                    Box(Modifier.weight(1f).aspectRatio(1f)) {
                        if (d != null) Box(
                            Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)).background(shade(d.level))
                                .then(if (d.selected) Modifier.border(2.dp, c.ink, RoundedCornerShape(8.dp)) else Modifier)
                                .alpha(if (d.future) 0.4f else 1f)
                                .pressable(enabled = !d.future, shrink = false) { onDay(d.date) },
                            Alignment.Center,
                        ) {
                            Text("${d.day}", style = LedgerType.small.copy(fontWeight = FontWeight.SemiBold), color = if (d.level >= 3) c.accentInk else c.ink)
                        }
                    }
                }
            }
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
        Caption("Less"); Spacer(Modifier.width(6.dp))
        (0..4).forEach { Box(Modifier.padding(horizontal = 2.dp).size(12.dp).clip(RoundedCornerShape(3.dp)).background(shade(it))) }
        Spacer(Modifier.width(6.dp)); Caption("More")
    }
    Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Text(h.dayLabel.uppercase(), style = LedgerType.label, color = c.ink2, modifier = Modifier.weight(1f))
        h.dayTotal?.let { Text(it.uppercase(), style = LedgerType.label, color = c.ink2) }
    }
    if (h.rows.isEmpty()) Caption("Nothing logged this day.")
    else LedgerCard { Column { h.rows.forEachIndexed { i, r -> if (i > 0) Hairline(); EntryRowItem(r) { onOpen(r.id) } } } }
}

/* ---------- where it went (web: catBreakdown + catDetail) ---------- */

@Composable
private fun CategoryBars(rows: List<CatRow>, onToggle: (String) -> Unit, onSeeAll: (String) -> Unit) {
    val c = LocalLedgerColors.current
    if (rows.isEmpty()) { Caption("Nothing spent in this period yet."); return }
    rows.forEach { r ->
        val color = categoryColor(r.name)
        Column(Modifier.fillMaxWidth().pressable(shrink = false) { onToggle(r.name) }.padding(vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(color))
                Spacer(Modifier.width(8.dp))
                Text(r.name, fontWeight = FontWeight.SemiBold, style = LedgerType.body, color = c.ink, modifier = Modifier.weight(1f))
                Text("${r.amount} · ${r.pct}%", style = LedgerType.small.copy(fontFeatureSettings = "tnum"), color = c.ink2)
            }
            Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(8.dp).clip(CircleShape).background(c.surface2)) {
                Box(Modifier.fillMaxWidth(r.fraction.coerceIn(0.01f, 1f)).height(8.dp).clip(CircleShape).background(color))
            }
        }
        if (r.open) Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.bg).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val mx = maxOf(1.0, r.points.maxOf { it.value })
            Row(Modifier.fillMaxWidth().height(72.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
                r.points.forEach { pt ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.width(14.dp).height(maxOf(2.0, pt.value / mx * 56).dp).clip(RoundedCornerShape(3.dp))
                            .background(color.copy(alpha = if (pt.current || r.points.none { it.current }) 1f else 0.45f)))
                        Text(pt.label, style = LedgerType.small.copy(fontSize = 10.sp), color = c.ink2)
                    }
                }
            }
            Caption(r.caption)
            r.items.forEach { KeyValue(Line(it.name, if (it.count > 1) "× ${it.count}" else null, formatRupees(it.total))) }
            Text(
                "See all ${r.name} entries", style = LedgerType.small, fontWeight = FontWeight.Bold, color = c.ink,
                textDecoration = TextDecoration.Underline, modifier = Modifier.pressable { onSeeAll(r.name) }.padding(top = 4.dp),
            )
        }
    }
    Caption("Tap a category to see its trend and what you spent on.")
}

/* ---------- category trends (web: trendCard) ---------- */

@Composable
private fun TrendCard(t: Trend, actions: InsightsActions) {
    val c = LocalLedgerColors.current
    val color = if (t.selected.isEmpty()) c.deep else categoryColor(t.selected)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LedgerChip("All spending", t.selected.isEmpty()) { actions.trendCategory("") }
        t.categories.forEach { k -> LedgerChip(k, t.selected == k, swatch = categoryColor(k)) { actions.trendCategory(k) } }
    }
    Segmented(listOf("Months", "Years"), if (t.years) 1 else 0) { actions.trendYears(it == 1) }
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        LegendBlock(if (t.years) "Year total" else "Last 12 months", color)
        if (t.groups.any { it.previous != null }) LegendBlock(if (t.years) "Last year to the same date" else "Same month a year before", c.ink2.copy(alpha = 0.4f))
    }
    val measurer = rememberTextMeasurer()
    val label = TextStyle(fontFamily = Figtree, fontSize = 10.sp, color = c.ink2)
    val labelOn = label.copy(color = c.ink, fontWeight = FontWeight.Bold)
    val W = 340f; val H = 180f; val L = 40f; val R = 6f; val T = 14f; val B = 24f
    val n = t.groups.size
    Canvas(
        Modifier.fillMaxWidth().aspectRatio(W / H).pointerInput(t) {
            detectTapGestures { o ->
                val vx = o.x / size.width * W
                val i = ((vx - L) / ((W - L - R) / n)).toInt()
                if (i in 0 until n) actions.trendBar(i)
            }
        },
    ) {
        val k = size.width / W
        val gw = (W - L - R) / n
        fun sy(v: Double) = (T + (1 - (v / t.ymax).toFloat()) * (H - T - B)) * k
        val base = (H - B) * k
        listOf(0.0, t.ymax / 2, t.ymax).forEach { v ->
            drawLine(c.line, Offset(L * k, sy(v)), Offset((W - R) * k, sy(v)), strokeWidth = 1.dp.toPx())
            val m = measurer.measure(if (v > 0) formatShort(v) else "₹0", label)
            drawText(m, topLeft = Offset((L - 6) * k - m.size.width, sy(v) - m.size.height / 2))
        }
        t.groups.forEachIndexed { i, g ->
            val x = L + i * gw
            val hasPrev = g.previous != null
            val bw = minOf(26f, gw * (if (hasPrev) 0.46f else 0.6f)); val pw = minOf(10f, gw * 0.2f)
            val cx = x + gw / 2
            val bx = if (hasPrev) cx - (bw + pw + 2) / 2 else cx - bw / 2
            val on = i == t.index
            if (hasPrev) {
                val ph = maxOf(if (g.previous!! > 0) 2f * k else 0f, base - sy(g.previous!!))
                roundBar(c.ink2.copy(alpha = 0.3f), (bx + bw + 2) * k, base - ph, pw * k, ph, 2f * k)
            }
            val h = maxOf(if (g.value > 0) 2f * k else 0f, base - sy(g.value))
            roundBar(color.copy(alpha = if (on) 1f else 0.55f), bx * k, base - h, bw * k, h, 3f * k)
            val m = measurer.measure(g.label, if (on) labelOn else label)
            drawText(m, topLeft = Offset(cx * k - m.size.width / 2, (H - 4) * k - m.size.height))
        }
    }
    Text(
        buildAnnotatedString {
            val first = t.caption.substringBefore(" · ")
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = c.ink)) { append(first) }
            if (t.caption.length > first.length) append(t.caption.substring(first.length))
        },
        style = LedgerType.small, color = c.ink2,
    )
    Caption("Tap a bar to compare it.")
}

private fun DrawScope.roundBar(color: Color, x: Float, y: Float, w: Float, h: Float, r: Float) {
    if (h <= 0) return
    drawRoundRect(color, topLeft = Offset(x, y), size = Size(w, h), cornerRadius = CornerRadius(r, r))
}

@Composable
private fun LegendBlock(name: String, color: Color) {
    val c = LocalLedgerColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(14.dp, 6.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Spacer(Modifier.width(6.dp))
        Text(name, style = LedgerType.small, color = c.ink2)
    }
}

/* ---------- simple columns: last 6 months, month by month, weekdays (web: .cols) ---------- */

@Composable
private fun Columns(bars: List<Bar>, height: Int, onTap: ((Bar) -> Unit)?) {
    val c = LocalLedgerColors.current
    val mx = maxOf(1.0, bars.maxOf { it.value })
    Row(Modifier.fillMaxWidth().height((height + 44).dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
        bars.forEach { b ->
            Column(
                Modifier.weight(1f).then(if (onTap != null) Modifier.pressable(shrink = false) { onTap(b) } else Modifier),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(b.top, style = LedgerType.small.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold), color = c.ink2, maxLines = 1)
                Box(
                    Modifier.padding(top = 4.dp).fillMaxWidth(0.72f).height(maxOf(if (b.value > 0) 3.0 else 2.0, b.value / mx * height).dp)
                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)).background(if (b.highlight) c.accent else c.line),
                )
                Text(b.label, style = LedgerType.small.copy(fontSize = 12.sp, fontWeight = if (b.highlight) FontWeight.Bold else FontWeight.Normal),
                    color = if (b.highlight) c.ink else c.ink2, modifier = Modifier.padding(top = 6.dp), maxLines = 1)
            }
        }
    }
}
