package com.himalayasoni.ledger.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.himalayasoni.ledger.R
import com.himalayasoni.ledger.ui.quickadd.QuickAdd

/** The small widget (2×1): two big buttons, + (type it) and mic (say it). Both open the quick add pop-up. */
class SmallWidget : GlanceAppWidget() {
    /** Exact: we're told the real size, so the labels can hide when it's narrow. */
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { SmallContent() }
    }
}

@Composable
private fun SmallContent() {
    val context = LocalContext.current
    val labels = LocalSize.current.width >= 140.dp
    Row(
        GlanceModifier.fillMaxSize().appWidgetBackground().background(WidgetColors.surface).cornerRadius(22.dp).padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WideButton(
            R.drawable.ic_widget_plus, "Add", WidgetColors.accent, WidgetColors.accentInk, labels,
            actionStartActivity(QuickAdd.intent(context)), GlanceModifier.defaultWeight(),
        )
        Spacer(GlanceModifier.width(6.dp))
        WideButton(
            R.drawable.ic_widget_mic, "Say it", WidgetColors.deep, WidgetColors.accent, labels,
            actionStartActivity(QuickAdd.intent(context, listen = true)), GlanceModifier.defaultWeight(),
        )
    }
}

@Composable
private fun WideButton(icon: Int, label: String, bg: ColorProvider, tint: ColorProvider, showLabel: Boolean, onClick: Action, modifier: GlanceModifier) {
    Row(
        modifier.fillMaxHeight().background(bg).cornerRadius(16.dp).clickable(onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(ImageProvider(icon), contentDescription = label, modifier = GlanceModifier.size(22.dp), colorFilter = ColorFilter.tint(tint))
        if (showLabel) {
            Spacer(GlanceModifier.width(6.dp))
            Text(label, style = TextStyle(color = tint, fontSize = 14.sp, fontWeight = FontWeight.Bold), maxLines = 1)
        }
    }
}
