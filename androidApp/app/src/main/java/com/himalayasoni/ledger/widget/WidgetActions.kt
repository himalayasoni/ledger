package com.himalayasoni.ledger.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import com.himalayasoni.ledger.core.domain.widgetLogged
import com.himalayasoni.ledger.data.QuickLogger
import com.himalayasoni.ledger.data.prefs.ReminderPrefs
import kotlinx.coroutines.delay
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/*
 * What happens when a widget chip is tapped. An ActionCallback is Glance's way to run our code
 * on a tap: Android wakes the app (a broadcast), Glance calls onAction, and we can save things
 * and redraw the widget. Glance creates these classes by name, so each needs an empty constructor
 * (and a keep rule for R8 in proguard-rules.pro).
 */

/** How long "Logged Chai ₹20 · Undo" stays on the widget. */
private const val UNDO_MS = 5_000L

/** Logs the tapped usual straight away, then offers Undo for a few seconds. */
class LogUsualAction : ActionCallback, KoinComponent {
    private val logger: QuickLogger by inject()
    private val prefs: ReminderPrefs by inject()

    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val key = parameters[USUAL] ?: return
        val (usual, entry) = logger.logUsual(key) ?: return
        val text = usual.widgetLogged(prefs.current().hideWidgetAmounts)
        setUndo(context, glanceId, entry.id, text)
        delay(UNDO_MS)
        clearUndo(context, glanceId, entry.id)
    }

    companion object {
        val USUAL = ActionParameters.Key<String>("usual")
    }
}

/** Undo on the widget: deletes the entry just logged. */
class UndoUsualAction : ActionCallback, KoinComponent {
    private val logger: QuickLogger by inject()

    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[ID] ?: return
        logger.undo(id)
        clearUndo(context, glanceId, id)
    }

    companion object {
        val ID = ActionParameters.Key<String>("id")
    }
}

private suspend fun setUndo(context: Context, glanceId: GlanceId, id: String, text: String) {
    updateAppWidgetState(context, glanceId) { it[MediumWidget.UNDO_ID] = id; it[MediumWidget.UNDO_TEXT] = text }
    MediumWidget().update(context, glanceId)
}

/** Clears the Undo bar, unless a newer tap has replaced it already. */
private suspend fun clearUndo(context: Context, glanceId: GlanceId, id: String) {
    updateAppWidgetState(context, glanceId) {
        if (it[MediumWidget.UNDO_ID] == id) { it.remove(MediumWidget.UNDO_ID); it.remove(MediumWidget.UNDO_TEXT) }
    }
    MediumWidget().update(context, glanceId)
}
