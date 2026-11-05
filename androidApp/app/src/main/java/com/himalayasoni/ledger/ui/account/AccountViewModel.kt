package com.himalayasoni.ledger.ui.account

import android.app.Activity
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.himalayasoni.ledger.data.FirebaseConfig
import com.himalayasoni.ledger.data.auth.AuthRepository
import com.himalayasoni.ledger.data.auth.SignInCancelled
import com.himalayasoni.ledger.data.sync.FirestoreSync
import com.himalayasoni.ledger.data.sync.SyncStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

data class AccountUiState(
    val configured: Boolean,
    val email: String?,
    val status: SyncStatus,
    val busy: Boolean = false,
    val message: String? = null,
) {
    /** One short line for the Home header. */
    val statusLine: String get() = when (val s = status) {
        SyncStatus.NotConfigured -> "On this phone"
        SyncStatus.SignedOut -> "Not backed up · sign in"
        SyncStatus.Syncing -> "Syncing…"
        is SyncStatus.Synced -> "Synced " + DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(s.at))
        is SyncStatus.Problem -> "Sync problem"
    }
}

class AccountViewModel(private val auth: AuthRepository, private val sync: FirestoreSync) : ViewModel() {
    private val busy = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)

    val state: StateFlow<AccountUiState> = combine(auth.account, sync.status, busy, message) { account, status, b, m ->
        AccountUiState(FirebaseConfig.isConfigured, account?.email, status, b, m)
    }.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000),
        AccountUiState(FirebaseConfig.isConfigured, auth.account.value?.email, sync.status.value),
    )

    /** Needs an Activity because the Google account picker is shown on top of it. */
    fun signIn(activity: Activity) {
        viewModelScope.launch {
            busy.value = true; message.value = null
            auth.signIn(activity).onFailure { if (it !is SignInCancelled) message.value = it.message }
            busy.value = false
        }
    }

    fun signOut(context: Context) { viewModelScope.launch { auth.signOut(context) } }
}
