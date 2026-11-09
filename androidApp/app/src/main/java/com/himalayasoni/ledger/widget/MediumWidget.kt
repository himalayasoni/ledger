package com.himalayasoni.ledger.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.himalayasoni.ledger.OpenRequest
import com.himalayasoni.ledger.R
import com.himalayasoni.ledger.core.data.EntryRepository
import com.himalayasoni.ledger.core.data.SettingsRepository
import com.himalayasoni.ledger.core.domain.WidgetChip
import com.himalayasoni.ledger.core.domain.WidgetSummary
import com.himalayasoni.ledger.data.prefs.ReminderPrefs
import com.himalayasoni.ledger.ui.quickadd.QuickAdd
import kotlinx.coroutines.flow.first
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * The medium widget (4×2): this month's and today's spending, + and mic, and your usuals as
 * one-tap chips. Tapping a chip logs it at once (see [LogUsualAction]) and shows
 * "Logged Chai ₹20 · Undo" for a few seconds.
 */
class MediumWidget : GlanceAppWidget(), KoinComponent {
    override val sizeMode: SizeMode = SizeMode.Exact

    private val repository: EntryRepository by inject()
    private val settings: SettingsRepository by inject()
    private val prefs: ReminderPrefs by inject()

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = LedgerWidgets.summaries(repository, settings, prefs)
        val first = data.first() // read once up front, so the widget never draws empty
        provideContent {
            // while the widget is "live" (a few seconds after an update), new entries redraw it straight away
            val summary by data.collectAsState(first)
            // this widget's own little state: the entry it can still undo
            MediumContent(summary, currentState(UNDO_ID), currentState(UNDO_TEXT))
        }
    }

    companion object {
        val UNDO_ID = stringPreferencesKey("undoId")
        val UNDO_TEXT = stringPreferencesKey("undoText")
    }
}

private val label = TextStyle(color = WidgetColors.ink2, fontSize = 11.sp, fontWeight = FontWeight.Bold)

@Composable
private fun MediumContent(s: WidgetSummary, undoId: String?, undoText: String?) {
    val context = LocalContext.current
    val size = LocalSize.current
    Column(GlanceModifier.fillMaxSize().appWidgetBackground().background(WidgetColors.surface).cornerRadius(22.dp).padding(12.dp)) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // the totals open the app
            Column(GlanceModifier.defaultWeight().clickable(actionStartActivity(OpenRequest.intent(context, null)))) {
                Text("SPENT THIS MONTH", style = label, maxLines = 1)
                Text(s.month, style = TextStyle(color = WidgetColors.ink, fontSize = 24.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                Text("Today ${s.today}", style = TextStyle(color = WidgetColors.ink2, fontSize = 12.sp), maxLines = 1)
            }
            SquareButton(R.drawable.ic_widget_mic, "Say it", WidgetColors.deep, WidgetColors.accent, actionStartActivity(QuickAdd.intent(context, listen = true)))
            Spacer(GlanceModifier.width(8.dp))
            SquareButton(R.drawable.ic_widget_plus, "Add", WidgetColors.accent, WidgetColors.accentInk, actionStartActivity(QuickAdd.intent(context)))
        }
        Spacer(GlanceModifier.height(10.dp))
        when {
            undoId != null && undoText != null -> UndoBar(undoText, undoId)
            s.chips.isEmpty() -> Text(
                "Things you log often show up here for one tap.",
                style = TextStyle(color = WidgetColors.ink2, fontSize = 12.sp), maxLines = 2,
            )
            // two rows of chips when there's room, else one
            else -> Chips(s.chips, rows = if (size.height >= 150.dp) 2 else 1, perRow = if (size.width >= 330.dp) 3 else 2)
        }
    }
}

@Composable
private fun SquareButton(icon: Int, description: String, bg: ColorProvider, tint: ColorProvider, onClick: Action) {
    Box(
        GlanceModifier.size(46.dp).background(bg).cornerRadius(14.dp).clickable(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Image(ImageProvider(icon), contentDescription = description, modifier = GlanceModifier.size(22.dp), colorFilter = ColorFilter.tint(tint))
    }
}

@Composable
private fun Chips(chips: List<WidgetChip>, rows: Int, perRow: Int) {
    Column(GlanceModifier.fillMaxWidth()) {
        chips.take(rows * perRow).chunked(perRow).forEachIndexed { i, row ->
            if (i > 0) Spacer(GlanceModifier.height(6.dp))
            Row(GlanceModifier.fillMaxWidth()) {
                for (j in 0 until perRow) {
                    if (j > 0) Spacer(GlanceModifier.width(6.dp))
                    val chip = row.getOrNull(j)
                    if (chip != null) Chip(chip, GlanceModifier.defaultWeight())
                    else Box(GlanceModifier.defaultWeight().height(38.dp)) {} // keeps the chips the same width
                }
            }
        }
    }
}

@Composable
private fun Chip(chip: WidgetChip, modifier: GlanceModifier) {
    Row(
        modifier.height(38.dp).background(WidgetColors.surface2).cornerRadius(12.dp).padding(horizontal = 10.dp)
            .clickable(actionRunCallback<LogUsualAction>(actionParametersOf(LogUsualAction.USUAL to chip.key))),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            chip.label, modifier = GlanceModifier.defaultWeight(),
            style = TextStyle(color = WidgetColors.ink, fontSize = 13.sp, fontWeight = FontWeight.Medium), maxLines = 1,
        )
        if (chip.amount.isNotEmpty()) {
            Spacer(GlanceModifier.width(4.dp))
            Text(chip.amount, style = TextStyle(color = WidgetColors.ink2, fontSize = 12.sp, fontWeight = FontWeight.Bold), maxLines = 1)
        }
    }
}

/** "Logged Chai ₹20 · Undo": the whole bar is the Undo button, so it's easy to hit. */
@Composable
private fun UndoBar(text: String, id: String) {
    Row(
        GlanceModifier.fillMaxWidth().height(38.dp).background(WidgetColors.deep).cornerRadius(12.dp).padding(horizontal = 12.dp)
            .clickable(actionRunCallback<UndoUsualAction>(actionParametersOf(UndoUsualAction.ID to id))),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, modifier = GlanceModifier.defaultWeight(), style = TextStyle(color = WidgetColors.deepInk, fontSize = 13.sp, fontWeight = FontWeight.Medium), maxLines = 1)
        Text("Undo", style = TextStyle(color = WidgetColors.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold), maxLines = 1)
    }
}
