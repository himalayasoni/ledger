package com.himalayasoni.ledger

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.himalayasoni.ledger.data.sync.FirestoreSync
import org.koin.android.ext.android.inject
import com.himalayasoni.ledger.ui.AppShell
import com.himalayasoni.ledger.ui.theme.LedgerTheme

/** The single activity. Everything visible is Compose, starting from [AppShell]. */
/** A FragmentActivity (not plain ComponentActivity) because the fingerprint prompt needs one. */
class MainActivity : FragmentActivity() {
    private val sync: FirestoreSync by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { LedgerTheme { AppShell() } }
    }

    override fun onResume() {
        super.onResume()
        sync.syncNow()
    }
}
