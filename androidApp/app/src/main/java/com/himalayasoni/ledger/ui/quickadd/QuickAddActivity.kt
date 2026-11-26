package com.himalayasoni.ledger.ui.quickadd

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.himalayasoni.ledger.core.model.EntryType
import com.himalayasoni.ledger.ui.add.AddEntrySheet
import com.himalayasoni.ledger.ui.add.AddEntryViewModel
import com.himalayasoni.ledger.ui.add.AddRequest
import com.himalayasoni.ledger.ui.theme.LedgerTheme
import com.himalayasoni.ledger.ui.scan.ScreenshotReader
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.core.qualifier.named

/**
 * The quick add pop-up: the add sheet on its own, over whatever is on screen (usually the home
 * screen), without opening the rest of Ledger. Opened by the widgets, the app icon's long-press
 * shortcuts ("Quick add", which Good Lock RegiStar can also open), the Quick Settings tile, the
 * reminder notification, and the side button (MainActivity sends it here; see LaunchPrefs).
 * Plain opens show the mini layout; see [QuickAdd.requestFrom].
 *
 * How it stays out of the way (see the manifest):
 * - its window is see-through (Theme.Ledger.QuickAdd), so only the sheet and its dim show;
 * - its own taskAffinity puts it in a separate task, so the main app isn't brought up behind it;
 * - excludeFromRecents keeps it out of the recent apps list once it closes.
 *
 * It works even when Ledger wasn't running: Android starts the app process first, and
 * LedgerApp.onCreate starts Koin before this activity is created.
 */
class QuickAddActivity : ComponentActivity() {
    private var request by mutableStateOf(AddRequest(mini = true))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        open(intent)
        setContent {
            LedgerTheme {
                AddEntrySheet(
                    request,
                    onDismiss = { finish() },
                    // the activity is about to close, so the toast belongs to the app, not to it
                    onSaved = { msg -> Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show() },
                    viewModel = koinViewModel<AddEntryViewModel>(qualifier = named(QuickAdd.VIEWMODEL)),
                    heightFraction = 0.88f,
                    // the pop-up's home button: on to the full app
                    onOpenApp = { startActivity(com.himalayasoni.ledger.MainActivity.appIntent(this)); finish() },
                )
            }
        }
    }

    /** Opened again while already showing (it's single-task): switch to what was asked for this time. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        open(intent)
    }

    /**
     * A shared screenshot: show the pop-up at once ("Reading…"), read it on the phone, then fill the
     * pop-up from what it says. Anything else: as the intent asks.
     */
    private fun open(intent: Intent?) {
        // a screen scan (ScreenScanActivity) passes the frame it saved; "" = the capture didn't work
        val screenFile = intent?.getStringExtra(QuickAdd.EXTRA_SCREEN_FILE)
        val shot = QuickAdd.sharedImage(intent) ?: screenFile?.takeIf { it.isNotEmpty() }?.let { android.net.Uri.fromFile(java.io.File(it)) }
        val source = if (screenFile != null) "screen" else "screenshot"
        if (shot == null) {
            request = if (screenFile != null) AddRequest(mini = true, scan = AddRequest.Scan.Unreadable, scanSource = source)
            else QuickAdd.requestFrom(intent)
            return
        }
        request = AddRequest(mini = true, scan = AddRequest.Scan.Reading, scanSource = source)
        lifecycleScope.launch {
            val scan = when (val r = ScreenshotReader.read(applicationContext, shot)) {
                is ScreenshotReader.Result.Read -> AddRequest.Scan.Read(r.payment)
                ScreenshotReader.Result.NotAPayment -> AddRequest.Scan.NotAPayment
                ScreenshotReader.Result.Unreadable -> AddRequest.Scan.Unreadable
            }
            screenFile?.let { java.io.File(it).delete() } // the frame was only needed for reading
            request = AddRequest(mini = true, scan = scan, scanSource = source)
        }
    }
}

/** How to open the pop-up, and what the extras mean. Shared by widgets, shortcuts, the tile and notifications. */
object QuickAdd {
    /** Koin name of the pop-up's AddEntryViewModel (one that never shows private categories). */
    const val VIEWMODEL = "quickAdd"

    /** An entry type's key, e.g. "cardbill" (see EntryType.key). */
    const val EXTRA_TYPE = "type"
    /** true: start voice input straight away ("Say it"). */
    const val EXTRA_LISTEN = "listen"
    /** A typed line to fill the form from (a notification reply that was missing something). */
    const val EXTRA_SAID = "said"

    fun intent(context: Context, type: EntryType? = null, listen: Boolean = false, said: String? = null): Intent =
        Intent(context, QuickAddActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .apply {
                type?.let { putExtra(EXTRA_TYPE, it.key) }
                if (listen) putExtra(EXTRA_LISTEN, true)
                said?.let { putExtra(EXTRA_SAID, it) }
            }

    /**
     * A PendingIntent is a "ticket" that lets another app (the launcher, the notification shade)
     * open our screen later, as us. [requestCode] keeps tickets with different extras apart.
     * FLAG_IMMUTABLE: nobody holding the ticket can change what it opens.
     */
    fun pendingIntent(context: Context, requestCode: Int, type: EntryType? = null, listen: Boolean = false, said: String? = null): PendingIntent =
        PendingIntent.getActivity(
            context, requestCode, intent(context, type, listen, said),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    /**
     * Reads the extras back. Shortcut XML may send "true" as text, so both forms are accepted.
     * No extras (the side button, the 1×1 widget, the + button, the Add shortcut): the mini quick add.
     * A type, "say it" or a line to finish: the full form, since those need more than an amount.
     */
    fun requestFrom(intent: Intent?): AddRequest {
        if (intent == null) return AddRequest(mini = true)
        val listen = intent.getBooleanExtra(EXTRA_LISTEN, false) || intent.getStringExtra(EXTRA_LISTEN) == "true"
        val type = intent.getStringExtra(EXTRA_TYPE)?.let(EntryType::fromKey)
        val said = intent.getStringExtra(EXTRA_SAID)?.takeIf { it.isNotBlank() }
        return AddRequest(type = type, listen = listen, said = said, mini = type == null && !listen && said == null)
    }

    /** A screen scan's saved frame (a file in Ledger's cache); "" when the capture failed. */
    const val EXTRA_SCREEN_FILE = "screenFile"

    fun screenIntent(context: Context, file: java.io.File?): Intent =
        Intent(context, QuickAddActivity::class.java).setAction(Intent.ACTION_VIEW)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(EXTRA_SCREEN_FILE, file?.absolutePath.orEmpty())

    /** The image in a "Share → Log in Ledger", if that's how we were opened. */
    fun sharedImage(intent: Intent?): android.net.Uri? {
        if (intent?.action != Intent.ACTION_SEND || intent.type?.startsWith("image/") != true) return null
        @Suppress("DEPRECATION")
        return if (android.os.Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, android.net.Uri::class.java)
        else intent.getParcelableExtra(Intent.EXTRA_STREAM)
    }
}
