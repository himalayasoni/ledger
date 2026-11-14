package com.himalayasoni.ledger.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import com.himalayasoni.ledger.R
import com.himalayasoni.ledger.ui.quickadd.QuickAdd

/** The 1×1 widget: a marigold square with a "+", one tap to the mini quick add (like the "Ledger +" icon). */
class TinyWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { TinyContent() }
    }
}

@Composable
private fun TinyContent() {
    val context = LocalContext.current
    val size = LocalSize.current
    val roomy = minOf(size.width, size.height) >= 64.dp
    Box(
        GlanceModifier.fillMaxSize().appWidgetBackground().background(WidgetColors.accent).cornerRadius(20.dp)
            .clickable(actionStartActivity(QuickAdd.intent(context))),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            ImageProvider(R.drawable.ic_widget_plus), contentDescription = "Add to Ledger",
            modifier = GlanceModifier.size(if (roomy) 32.dp else 24.dp), colorFilter = ColorFilter.tint(WidgetColors.deep),
        )
        // the small rupee mark in the corner, when there's room for it
        if (roomy) Box(GlanceModifier.fillMaxSize().padding(9.dp), contentAlignment = Alignment.TopStart) {
            Image(
                ImageProvider(R.drawable.ic_stat), contentDescription = null,
                modifier = GlanceModifier.size(13.dp), colorFilter = ColorFilter.tint(WidgetColors.deep),
            )
        }
    }
}
