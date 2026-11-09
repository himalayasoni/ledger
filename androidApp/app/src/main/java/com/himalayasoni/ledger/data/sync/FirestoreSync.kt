package com.himalayasoni.ledger.data.sync

import com.google.firebase.Timestamp
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.himalayasoni.ledger.core.sync.RemoteDoc
import com.himalayasoni.ledger.core.sync.decodeRemote
import com.himalayasoni.ledger.core.sync.encodeRemote
import com.himalayasoni.ledger.core.sync.shouldApplyRemote
import com.himalayasoni.ledger.data.FirebaseConfig
import com.himalayasoni.ledger.data.auth.AuthRepository
import com.himalayasoni.ledger.data.local.EntryDao
import com.himalayasoni.ledger.data.local.EntryEntity
import com.himalayasoni.ledger.data.local.toEntity
import com.himalayasoni.ledger.data.local.toEntry
import com.himalayasoni.ledger.data.prefs.SyncPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import java.util.Date

sealed interface SyncStatus {
    data object NotConfigured : SyncStatus
    data object SignedOut : SyncStatus
    data object Syncing : SyncStatus
    data class Synced(val at: Long) : SyncStatus
    data class Problem(val message: String) : SyncStatus
}

/**
 * Two-way sync between the phone's database (the source of truth) and Firestore.
 *
 *  - **Up:** rows marked dirty are written to users/{uid}/entries/{id}, stamped with the
 *    server's time ("srv"), then marked clean.
 *  - **Down:** a live listener asks only for documents with srv newer than the last one we saw,
 *    and applies each if it's newer than our copy (most recent edit wins).
 *
 * It follows the signed-in account: sign in starts it, sign out (or a new account) restarts it.
 */
@OptIn(FlowPreview::class)
class FirestoreSync(
    private val dao: EntryDao,
    private val prefs: SyncPrefs,
    private val auth: AuthRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val applyLock = Mutex()
    private val uploadLock = Mutex()
    private var started = false

    private val _status = MutableStateFlow<SyncStatus>(if (FirebaseConfig.isConfigured) SyncStatus.SignedOut else SyncStatus.NotConfigured)
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    fun start() {
        if (started || !FirebaseConfig.isConfigured) return
        started = true
        scope.launch {
            auth.account.map { it?.uid }.distinctUntilChanged().collectLatest { uid ->
                if (uid == null) { _status.value = SyncStatus.SignedOut; return@collectLatest }
                runFor(uid) // cancelled automatically when the account changes
            }
        }
    }

    /** Pushes pending changes right away (e.g. when the app comes back to the foreground). */
    fun syncNow() {
        val uid = auth.account.value?.uid ?: return
        scope.launch { upload(collection(uid)) }
    }

    private suspend fun runFor(uid: String): Unit = coroutineScope {
        if (prefs.uid() != uid) {
            prefs.switchAccount(uid)
            dao.markAllDirty() // anything already on the phone joins this account
        }
        _status.value = SyncStatus.Syncing
        val col = collection(uid)
        // the app learned new fields or types: fetch everything once so nothing it skipped before is missing
        if (prefs.needsFullResync()) { prefs.setLastServerTime(0); prefs.markFormatCurrent() }
        val since = Timestamp(Date(prefs.lastServerTime()))
        val registration = col.whereGreaterThan("srv", since).addSnapshotListener { snap, err ->
            if (err != null) { _status.value = SyncStatus.Problem(friendly(err)); return@addSnapshotListener }
            if (snap == null) return@addSnapshotListener
            val docs = snap.documentChanges
                .filter { it.type != DocumentChange.Type.REMOVED && !it.document.metadata.hasPendingWrites() }
                .map { it.document }
            val fromServer = !snap.metadata.isFromCache
            scope.launch { applyRemote(docs, fromServer) }
        }
        try {
            // upload whenever there are local changes, waiting for a short pause in typing
            launch { dao.observeDirtyCount().debounce(800).filter { it > 0 }.collect { upload(col) } }
            awaitCancellation()
        } finally {
            registration.remove()
        }
    }

    private suspend fun applyRemote(docs: List<DocumentSnapshot>, fromServer: Boolean) = applyLock.withLock {
        var newest = prefs.lastServerTime()
        val rows = mutableListOf<EntryEntity>()
        for (doc in docs) {
            val data = doc.data ?: continue
            (data["srv"] as? Timestamp)?.toDate()?.time?.let { if (it > newest) newest = it }
            when (val remote = decodeRemote(doc.id, data - "srv")) {
                is RemoteDoc.EntryDoc ->
                    if (shouldApplyRemote(dao.updatedAt(remote.entry.id), remote.entry.updatedAt)) rows += remote.entry.toEntity(dirty = false)
                is RemoteDoc.Tombstone -> dao.applyTombstone(remote.id, remote.updatedAt)
                is RemoteDoc.Privacy -> prefs.setPrivateCategories(remote.privateCategories)
                is RemoteDoc.Categories -> prefs.setCategories(remote.custom, remote.removed)
                is RemoteDoc.Cards -> prefs.setCardSettings(remote.cards)
                RemoteDoc.Other -> Unit
            }
        }
        if (rows.isNotEmpty()) dao.upsertAll(rows)
        prefs.setLastServerTime(newest)
        if (fromServer && _status.value !is SyncStatus.Problem) {
            val now = clock(); prefs.markSynced(now); _status.value = SyncStatus.Synced(now)
        }
    }

    private suspend fun upload(col: CollectionReference) = uploadLock.withLock {
        val rows = dao.dirty()
        if (rows.isEmpty()) return@withLock
        _status.value = SyncStatus.Syncing
        try {
            for (chunk in rows.chunked(400)) { // Firestore allows 500 writes per batch
                val batch = FirebaseFirestore.getInstance().batch()
                for (row in chunk) {
                    val entry = row.toEntry() ?: continue
                    batch.set(col.document(entry.id), entry.encodeRemote() + ("srv" to FieldValue.serverTimestamp()))
                }
                batch.commit().await() // waits until online if needed
                chunk.forEach { dao.markClean(it.id, it.updatedAt) }
            }
            val now = clock(); prefs.markSynced(now); _status.value = SyncStatus.Synced(now)
        } catch (e: Exception) {
            _status.value = SyncStatus.Problem(friendly(e))
        }
    }

    /**
     * Changes which categories are private: on this phone straight away, and in your account so the
     * web app follows. It merges into the web's settings record, so the web PIN fields are untouched.
     */
    fun savePrivacy(categories: Set<String>) {
        scope.launch {
            prefs.setPrivateCategories(categories)
            val uid = auth.account.value?.uid ?: return@launch
            try {
                collection(uid).document("cfg-private").set(
                    mapOf("id" to "cfg-private", "privateCats" to categories.sorted(), "updatedAt" to clock(), "srv" to FieldValue.serverTimestamp()),
                    com.google.firebase.firestore.SetOptions.merge(),
                ).await()
            } catch (e: Exception) {
                _status.value = SyncStatus.Problem(friendly(e))
            }
        }
    }

    /** Adds a category of your own: here straight away, and to the web's list (merged, so nothing else changes). */
    fun addCategory(name: String) {
        scope.launch {
            val custom = (prefs.customCategories.first() + name).distinctBy { it.lowercase() }
            val removed = prefs.removedCategories.first().filterNot { it.equals(name, true) }
            prefs.setCategories(custom, removed)
            val uid = auth.account.value?.uid ?: return@launch
            try {
                collection(uid).document("cfg-cats").set(
                    mapOf("id" to "cfg-cats", "custom" to custom, "removed" to removed, "updatedAt" to clock(), "srv" to FieldValue.serverTimestamp()),
                    com.google.firebase.firestore.SetOptions.merge(),
                ).await()
            } catch (e: Exception) {
                _status.value = SyncStatus.Problem(friendly(e))
            }
        }
    }

    /** Sets a card's statement day (null clears it), here and in your account (merged, so nothing else changes). */
    fun setStatementDay(card: String, day: Int?) = updateCard(card) { it.copy(statementDay = day) }

    /** Sets the day a card's bill is due (for the reminder). */
    fun setDueDay(card: String, day: Int?) = updateCard(card) { it.copy(dueDay = day) }

    private fun updateCard(card: String, change: (com.himalayasoni.ledger.core.domain.CardSettings) -> com.himalayasoni.ledger.core.domain.CardSettings) {
        scope.launch {
            val cur = prefs.cardSettings.first()
            val old = cur.firstOrNull { it.name.equals(card, true) } ?: com.himalayasoni.ledger.core.domain.CardSettings(card)
            val next = cur.filterNot { it.name.equals(card, true) } + change(old)
            prefs.setCardSettings(next)
            val uid = auth.account.value?.uid ?: return@launch
            try {
                collection(uid).document("cfg-cards").set(
                    com.himalayasoni.ledger.core.sync.encodeCards(next, clock()) + ("srv" to FieldValue.serverTimestamp()),
                    com.google.firebase.firestore.SetOptions.merge(),
                ).await()
            } catch (e: Exception) {
                _status.value = SyncStatus.Problem(friendly(e))
            }
        }
    }

    private fun collection(uid: String) =
        FirebaseFirestore.getInstance().collection("users").document(uid).collection("entries")

    private fun friendly(e: Exception): String = when ((e as? FirebaseFirestoreException)?.code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED -> "This Google account isn't allowed by your Firebase rules."
        FirebaseFirestoreException.Code.UNAVAILABLE -> "Can't reach the server. Changes will sync when you're online."
        else -> e.message ?: "Sync hit a problem."
    }
}
