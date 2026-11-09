package com.himalayasoni.ledger.core.sync

import com.himalayasoni.ledger.core.model.Entry
import com.himalayasoni.ledger.core.model.EntryType
import java.time.LocalDate

/*
 * Translation between an Entry and the Firestore document the web app reads and writes
 * (users/{uid}/entries/{id}). Field names here are the shared contract; spec/remote-entries.json
 * holds real examples that both apps' tests check against.
 */

/** What a document in the entries collection turned out to be. */
sealed interface RemoteDoc {
    data class EntryDoc(val entry: Entry) : RemoteDoc
    /** A deletion made on another device. */
    data class Tombstone(val id: String, val updatedAt: Long) : RemoteDoc
    /** The web app's private-categories setting (cfg-private). */
    data class Privacy(val privateCategories: Set<String>, val updatedAt: Long) : RemoteDoc
    /** The web app's own categories (cfg-cats): ones you added, and defaults you removed. */
    data class Categories(val custom: List<String>, val removed: List<String>, val updatedAt: Long) : RemoteDoc

    /** Anything else (other settings, future entry types): left alone. */
    data object Other : RemoteDoc
}

fun decodeRemote(id: String, data: Map<String, Any?>): RemoteDoc {
    val updatedAt = data.long("updatedAt")
    if (id == "cfg-private") {
        val cats = (data["privateCats"] as? List<*>)?.filterIsInstance<String>()?.toSet() ?: emptySet()
        return RemoteDoc.Privacy(cats, updatedAt)
    }
    if (id == "cfg-cats") {
        fun list(k: String) = (data[k] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
        return RemoteDoc.Categories(list("custom"), list("removed"), updatedAt)
    }
    if (data["deleted"] == true) return RemoteDoc.Tombstone(id, updatedAt)
    val type = EntryType.fromKey(data["type"] as? String ?: return RemoteDoc.Other) ?: return RemoteDoc.Other
    val date = (data["date"] as? String)?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return RemoteDoc.Other
    val amount = (data["amount"] as? Number)?.toDouble() ?: return RemoteDoc.Other
    val paidBack = (data["paidBack"] as? Map<*, *>)
        ?.mapNotNull { (k, v) -> (k as? String)?.let { key -> (v as? Number)?.toDouble()?.let { key to it } } }
        ?.toMap() ?: emptyMap()
    return RemoteDoc.EntryDoc(
        Entry(
            id = id,
            type = type,
            amount = amount,
            date = date,
            description = data.str("desc"),
            person = data.str("person"),
            category = data.str("cat"),
            mode = data.str("mode").ifBlank { "UPI" },
            note = data.str("note"),
            withPeople = (data["with"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
            includesMe = data["incMe"] as? Boolean ?: true,
            paidBack = paidBack,
            prior = (data["prior"] as? Number)?.toDouble() ?: 0.0,
            createdAt = data.long("createdAt"),
            updatedAt = updatedAt,
        ),
    )
}

/** The document to upload. Deleted entries upload as tombstones, exactly like the web app's. */
fun Entry.encodeRemote(): Map<String, Any?> {
    if (deleted) return mapOf("id" to id, "deleted" to true, "updatedAt" to updatedAt)
    return buildMap {
        put("id", id)
        put("type", type.key)
        put("amount", amount)
        put("desc", description)
        put("person", person)
        put("cat", category)
        put("mode", mode)
        put("date", date.toString())
        put("note", note)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
        if (withPeople.isNotEmpty()) { put("with", withPeople); put("incMe", includesMe) }
        if (paidBack.isNotEmpty()) put("paidBack", paidBack)
        if (prior > 0) put("prior", prior)
    }
}

/** Conflict rule shared with the web app: the most recent edit wins. */
fun shouldApplyRemote(localUpdatedAt: Long?, remoteUpdatedAt: Long): Boolean =
    localUpdatedAt == null || remoteUpdatedAt > localUpdatedAt

private fun Map<String, Any?>.str(key: String): String = this[key] as? String ?: ""
private fun Map<String, Any?>.long(key: String): Long = (this[key] as? Number)?.toLong() ?: 0L
