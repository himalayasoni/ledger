package com.himalayasoni.ledger

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import com.himalayasoni.ledger.data.sync.FirestoreSync
import org.koin.android.ext.android.inject
import com.himalayasoni.ledger.ui.AppShell
import com.himalayasoni.ledger.ui.theme.LedgerTheme

/** The single activity. Everything visible is Compose, starting from [AppShell]. */
/** A FragmentActivity (not plain ComponentActivity) because the fingerprint prompt needs one. */
class MainActivity : FragmentActivity() {
    private val sync: FirestoreSync by inject()
    /** Something a notification asked to open (an entry, a card), until AppShell has opened it. */
    private val open = mutableStateOf<OpenRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) open.value = OpenRequest.from(intent)
        setContent { LedgerTheme { AppShell(openRequest = open.value, onOpened = { open.value = null }) } }
    }

    /** A notification tapped while the app was already open. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        OpenRequest.from(intent)?.let { open.value = it }
    }

    override fun onResume() {
        super.onResume()
        sync.syncNow()
    }
}

/** "Open the app on this": an entry (a refund reminder) or a card's page (a bill reminder). */
data class OpenRequest(val entry: String? = null, val card: String? = null) {
    companion object {
        private const val EXTRA_ENTRY = "openEntry"
        private const val EXTRA_CARD = "openCard"

        fun intent(context: Context, open: OpenRequest?): Intent =
            Intent(context, MainActivity::class.java)
                // bring the app's existing screen forward (or start it), rather than stacking a second copy
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .apply {
                    open?.entry?.let { putExtra(EXTRA_ENTRY, it) }
                    open?.card?.let { putExtra(EXTRA_CARD, it) }
                }

        fun from(intent: Intent?): OpenRequest? {
            val r = OpenRequest(intent?.getStringExtra(EXTRA_ENTRY), intent?.getStringExtra(EXTRA_CARD))
            return if (r.entry == null && r.card == null) null else r
        }
    }
}
