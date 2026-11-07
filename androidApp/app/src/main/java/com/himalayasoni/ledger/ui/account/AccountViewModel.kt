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
    /** For the account sheet: "Last synced 8:45 pm". */
    val detailLine: String get() = when (val s = status) {
        is SyncStatus.Synced -> "Last synced " + DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(s.at))
        is SyncStatus.Problem -> s.message
        else -> statusLine
    }

    /** One short line for the Home header. */
    val statusLine: String get() = when (val s = status) {
        SyncStatus.NotConfigured -> "On this phone"
        SyncStatus.SignedOut -> "Not backed up"
        SyncStatus.Syncing -> "Syncing…"
        is SyncStatus.Synced -> "Synced"
        is SyncStatus.Problem -> "Sync problem"
    }
}

/** The Privacy part of Settings. */
data class PrivacyUi(
    val showing: Boolean,                // private entries visible right now
    val privateCategories: List<String>,
    val categories: List<String>,        // every category you could make private
    val suggestions: List<String>,       // ones that look private ("Sutta", "Beer"…), not private yet
)

/** Same words the web app uses to suggest private categories. */
private val PRIVATE_WORDS = Regex(
    "\\b(sutta|cigarettes?|cigs?|smoke|smoking|gold flake|marlboro|classic milds|hookah|vape|beers?|daru|alcohol|liquor|whiske?y|vodka|rum|wine|pub|bar|theka|bira|kingfisher|budweiser|weed|ganja|gutkh?a|tobacco|pan masala|betting|casino|poker|rummy|dream11|lottery)\\b",
    RegexOption.IGNORE_CASE,
)

class AccountViewModel(
    private val auth: AuthRepository,
    private val sync: FirestoreSync,
    private val settings: com.himalayasoni.ledger.core.data.SettingsRepository,
    repository: com.himalayasoni.ledger.core.data.EntryRepository,
) : ViewModel() {
    val privacy: StateFlow<PrivacyUi> = combine(repository.observeEntries(), settings.privateCategories, settings.showPrivate) { all, priv, show ->
        val used = all.filter { !it.type.isDebt && it.category.isNotBlank() }.map { it.category }
        val cats = (used + com.himalayasoni.ledger.core.model.DEFAULT_CATEGORIES + priv).distinctBy { it.lowercase() }.sortedBy { it.lowercase() }
        val lower = priv.map { it.lowercase() }.toSet()
        PrivacyUi(show, priv.sortedBy { it.lowercase() }, cats, cats.filter { it.lowercase() !in lower && PRIVATE_WORDS.containsMatchIn(it) })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PrivacyUi(false, emptyList(), emptyList(), emptyList()))

    /** Lock or unlock a category. Only possible while private entries are showing. */
    fun togglePrivate(category: String) {
        val p = privacy.value
        if (!p.showing) return
        val cur = p.privateCategories.toSet()
        val next = if (cur.any { it.equals(category, true) }) cur.filterNot { it.equals(category, true) }.toSet() else cur + category
        sync.savePrivacy(next)
    }

    /** Call after the fingerprint check succeeds; hiding needs no check. */
    fun setShowPrivate(show: Boolean) { viewModelScope.launch { settings.setShowPrivate(show) } }

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
