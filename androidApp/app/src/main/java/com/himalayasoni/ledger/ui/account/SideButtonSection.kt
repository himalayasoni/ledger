package com.himalayasoni.ledger.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.himalayasoni.ledger.LaunchPrefs
import com.himalayasoni.ledger.ui.components.LedgerCard
import com.himalayasoni.ledger.ui.components.LedgerChip
import com.himalayasoni.ledger.ui.theme.LedgerType
import com.himalayasoni.ledger.ui.theme.LocalLedgerColors

/** Settings > Side button: what opening Ledger does (see MainActivity and LaunchPrefs). */
@Composable
fun SideButtonSection() {
    val context = LocalContext.current
    var mode by remember { mutableStateOf(LaunchPrefs.mode(context)) }
    val last = remember { LaunchPrefs.lastOpener(context) }
    SideButtonContent(mode, last, onMode = { LaunchPrefs.setMode(context, it); mode = it })
}

/** The section with no storage: what the screenshot test draws. [last]: who opened Ledger last, and whether that counted as the icon. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SideButtonContent(mode: LaunchPrefs.Mode, last: Pair<String, Boolean>?, onMode: (LaunchPrefs.Mode) -> Unit) {
    val c = LocalLedgerColors.current
    LedgerCard {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Side button", style = LedgerType.title, color = c.ink)
            Text(
                "Set Settings › Advanced features › Side button › Double press › Open app › Ledger β. " +
                    "Then a double press does what you pick here, over whatever you're doing.",
                style = LedgerType.body, color = c.ink2,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    LaunchPrefs.Mode.SIDE to "Quick add",
                    LaunchPrefs.Mode.SCAN to "Scan screen",
                    LaunchPrefs.Mode.APP to "Full app",
                    LaunchPrefs.Mode.QUICK to "Quick add, icon too",
                ).forEach { (m, label) -> LedgerChip(label, mode == m) { onMode(m) } }
            }
            Text(
                when (mode) {
                    LaunchPrefs.Mode.SIDE -> "The app icon still opens the full app."
                    LaunchPrefs.Mode.APP -> "The side button opens the full app, like the icon."
                    LaunchPrefs.Mode.QUICK -> "The icon opens the pop-up too. Its home button takes you to the full app."
                    LaunchPrefs.Mode.SCAN -> "A double press reads the payment on screen. Android asks first each time: tap Start. The icon still opens the full app."
                },
                style = LedgerType.small, color = c.ink2,
            )
            last?.let { (who, icon) ->
                Text(
                    "Last opened by $who, " + if (icon) "counted as the app icon." else "counted as the side button.",
                    style = LedgerType.small, color = c.ink2,
                )
            }
        }
    }
}
