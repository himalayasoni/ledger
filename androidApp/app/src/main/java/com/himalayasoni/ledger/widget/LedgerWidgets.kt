package com.himalayasoni.ledger.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.updateAll
import androidx.glance.color.ColorProvider
import com.himalayasoni.ledger.core.data.EntryRepository
import com.himalayasoni.ledger.core.data.SettingsRepository
import com.himalayasoni.ledger.core.domain.WidgetSummary
import com.himalayasoni.ledger.core.domain.widgetSummary
import com.himalayasoni.ledger.data.prefs.ReminderPrefs
import com.himalayasoni.ledger.ui.theme.Dark
import com.himalayasoni.ledger.ui.theme.Light
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.time.LocalDate

/*
 * Home-screen widgets, made with Jetpack Glance. Glance lets you write a widget in a
 * Compose-like way (Row, Column, Text), then turns it into RemoteViews: the limited kind of
 * layout another app (the launcher) can draw for us. So no Material components, no custom fonts,
 * and taps are "actions" (open an activity, run an ActionCallback) instead of lambdas.
 */

/** The widget-side wiring: what the medium widget shows, and keeping it fresh. */
object LedgerWidgets {
    /** Live widget text from the database. Private categories are always left out (see core's Widget.kt). */
    fun summaries(repository: EntryRepository, settings: SettingsRepository, prefs: ReminderPrefs): Flow<WidgetSummary> =
        combine(repository.observeEntries(), settings.privateCategories, prefs.settings) { entries, private, s ->
            widgetSummary(entries, private, LocalDate.now(), s.hideWidgetAmounts)
        }

    /** Redraws every Ledger widget on the home screen (cheap when there are none). */
    suspend fun refresh(context: Context) {
        runCatching { MediumWidget().updateAll(context) }
    }

    /**
     * Started by LedgerApp: whenever what the widget would show changes (an entry saved here,
     * synced from the web, a setting), redraw it. Waits half a second so a burst of synced
     * entries redraws once.
     */
    @OptIn(FlowPreview::class)
    fun keepFresh(context: Context, scope: CoroutineScope, summaries: Flow<WidgetSummary>) {
        scope.launch { summaries.distinctUntilChanged().debounce(500).collect { refresh(context) } }
    }
}

/**
 * Ledger's colours for widgets, from the same tokens as the app (ui/theme/Theme.kt).
 * A day/night ColorProvider lets the launcher pick light or dark to match the phone.
 */
internal object WidgetColors {
    private fun pair(day: Color, night: Color) = ColorProvider(day = day, night = night)
    val surface = pair(Light.surface, Dark.surface)
    val surface2 = pair(Light.surface2, Dark.surface2)
    val ink = pair(Light.ink, Dark.ink)
    val ink2 = pair(Light.ink2, Dark.ink2)
    val accent = pair(Light.accent, Dark.accent)
    val accentInk = pair(Light.accentInk, Dark.accentInk)
    val deep = pair(Light.deep, Dark.deep)
    val deepInk = pair(Light.deepInk, Dark.deepInk)
}

/** Android finds widgets through these receivers (listed in the manifest with their sizes). */
class SmallWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = SmallWidget()
}

class MediumWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = MediumWidget()
}
