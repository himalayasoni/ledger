package com.himalayasoni.ledger.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.himalayasoni.ledger.ui.theme.Bricolage
import com.himalayasoni.ledger.ui.theme.LedgerType
import com.himalayasoni.ledger.ui.theme.LocalLedgerColors

/*
 * Ledger's building blocks. Each one mirrors a CSS class in the web app (named in its comment),
 * so screens are assembled from the same parts on both platforms.
 */

/** A press that shrinks slightly, like the web's :active styles. Ripple-free on purpose. */
@Composable
fun Modifier.pressable(enabled: Boolean = true, role: Role = Role.Button, shrink: Boolean = true, onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    return this
        // Scaling re-rasterises text, which looks soft on chips; those dim slightly instead.
        .then(if (shrink) Modifier.scale(if (pressed) 0.97f else 1f) else Modifier.alpha(if (pressed) 0.85f else 1f))
        .clickable(interactionSource = source, indication = null, enabled = enabled, role = role, onClick = onClick)
}

/** `.chip`: a pill. Selected chips turn ink-coloured. [swatch] adds the category dot. [soft] is the marigold "usual" style. */
@Composable
fun LedgerChip(
    text: String,
    selected: Boolean = false,
    swatch: Color? = null,
    soft: Boolean = false,
    trailing: String? = null,
    onClick: () -> Unit,
) {
    val c = LocalLedgerColors.current
    val bg = when { selected -> c.ink; soft -> c.accentSoft; else -> c.surface }
    val fg = if (selected) c.bg else c.ink
    Row(
        Modifier
            .height(36.dp)
            .clip(CircleShape)
            .background(bg)
            .then(if (selected || soft) Modifier else Modifier.border(1.dp, c.line, CircleShape))
            .pressable(shrink = false, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (swatch != null) Box(Modifier.size(8.dp).clip(CircleShape).background(swatch))
        Text(text, style = LedgerType.body.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold), color = fg, maxLines = 1)
        if (trailing != null) Text(trailing, style = LedgerType.body.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"), color = fg)
    }
}

/** `.ic`: the rounded square at the start of a row, tinted by category or type. */
@Composable
fun IconTile(tint: Color, letter: String? = null, icon: ImageVector? = null, size: Dp = 38.dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = 0.16f)), Alignment.Center) {
        if (icon != null) Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
        else Text(letter.orEmpty(), color = tint, fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
    }
}

/** `.icon-btn`: a 40dp square button with a hairline border. */
@Composable
fun SquareIconButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    val c = LocalLedgerColors.current
    Box(
        Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(c.surface)
            .border(1.dp, c.line, RoundedCornerShape(12.dp)).pressable(onClick = onClick),
        Alignment.Center,
    ) { Icon(icon, description, tint = c.ink2, modifier = Modifier.size(20.dp)) }
}

/** `.quick-h` / `.fld > span`: small uppercase label above a group. */
@Composable
fun FieldLabel(text: String, modifier: Modifier = Modifier) =
    Text(text.uppercase(), style = LedgerType.label, color = LocalLedgerColors.current.ink2, modifier = modifier.padding(bottom = 6.dp))

/** `.inp`: a text box on the page background with a hairline border. */
@Composable
fun LedgerTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
    imeAction: ImeAction = ImeAction.Next,
    onDone: () -> Unit = {},
    keyboardType: androidx.compose.ui.text.input.KeyboardType = androidx.compose.ui.text.input.KeyboardType.Text,
    leading: (@Composable () -> Unit)? = null,
) {
    val c = LocalLedgerColors.current
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = singleLine,
        minLines = minLines,
        textStyle = LedgerType.body.copy(fontSize = 16.sp, color = c.ink),
        cursorBrush = SolidColor(c.accent),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = imeAction, keyboardType = keyboardType),
        keyboardActions = KeyboardActions(onDone = { onDone() }, onGo = { onDone() }),
        modifier = modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Box(
                Modifier.fillMaxWidth().defaultMinSize(minHeight = 44.dp).clip(RoundedCornerShape(12.dp)).background(c.bg)
                    .border(1.dp, c.line, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 11.dp),
                // multi-line boxes start at the top, so the hint sits where the cursor is
                if (singleLine) Alignment.CenterStart else Alignment.TopStart,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (leading != null) { leading(); Spacer(Modifier.width(10.dp)) }
                    Box(Modifier.weight(1f)) {
                        if (value.isEmpty()) Text(placeholder, style = LedgerType.body.copy(fontSize = 16.sp), color = c.ink2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        inner()
                    }
                }
            }
        },
    )
}

/** `.btn.primary` / `.save`: the marigold button. */
@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, height: Dp = 54.dp, enabled: Boolean = true, onClick: () -> Unit) {
    val c = LocalLedgerColors.current
    Box(
        modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(14.dp))
            .background(if (enabled) c.accent else c.accent.copy(alpha = 0.5f)).pressable(enabled, onClick = onClick),
        Alignment.Center,
    ) { Text(text, color = c.accentInk, fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp) }
}

/** `.btn`: the quiet grey button. */
@Composable
fun QuietButton(text: String, modifier: Modifier = Modifier, color: Color? = null, onClick: () -> Unit) {
    val c = LocalLedgerColors.current
    Box(
        modifier.height(44.dp).clip(RoundedCornerShape(12.dp)).background(color?.copy(alpha = 0.14f) ?: c.surface2)
            .pressable(onClick = onClick).padding(horizontal = 18.dp),
        Alignment.Center,
    ) { Text(text, color = color ?: c.ink, fontWeight = FontWeight.Bold, style = LedgerType.body) }
}

/** `.list` / `.card`: white card with a hairline border. */
@Composable
fun LedgerCard(modifier: Modifier = Modifier, radius: Dp = 16.dp, content: @Composable () -> Unit) {
    val c = LocalLedgerColors.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(radius),
        color = c.surface,
        border = BorderStroke(1.dp, c.line),
        content = content,
    )
}

/** A thin rule between rows. */
@Composable
fun Hairline(modifier: Modifier = Modifier) =
    Box(modifier.fillMaxWidth().height(1.dp).background(LocalLedgerColors.current.line))

/** A square checkbox row, like `.check`. */
@Composable
fun CheckRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = LocalLedgerColors.current
    Row(
        Modifier.pressable(role = Role.Checkbox) { onChange(!checked) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(20.dp).clip(RoundedCornerShape(5.dp))
                .background(if (checked) c.accent else c.surface)
                .then(if (checked) Modifier else Modifier.border(1.5.dp, c.ink2, RoundedCornerShape(5.dp))),
            Alignment.Center,
        ) { if (checked) Icon(LedgerIcons.Check, null, tint = c.accentInk, modifier = Modifier.size(14.dp)) }
        Spacer(Modifier.width(8.dp))
        Text(text, fontWeight = FontWeight.SemiBold, style = LedgerType.body, color = c.ink)
    }
}

/** Vertical space shorthand. */
@Composable
fun Gap(h: Dp) = Spacer(Modifier.height(h))

/** Column with web-like defaults. */
@Composable
fun Stack(modifier: Modifier = Modifier, gap: Dp = 0.dp, content: @Composable () -> Unit) =
    Column(modifier, verticalArrangement = Arrangement.spacedBy(gap)) { content() }
