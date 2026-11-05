package com.himalayasoni.ledger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.himalayasoni.ledger.data.sync.FirestoreSync
import org.koin.android.ext.android.inject
import com.himalayasoni.ledger.ui.home.HomeScreen
import com.himalayasoni.ledger.ui.theme.LedgerTheme

/** The single activity. Everything visible is Compose, starting from [HomeScreen]. */
class MainActivity : ComponentActivity() {
    private val sync: FirestoreSync by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { LedgerTheme { HomeScreen() } }
    }

    override fun onResume() {
        super.onResume()
        sync.syncNow()
    }
}
