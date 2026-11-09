package com.himalayasoni.ledger.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.himalayasoni.ledger.core.model.Entry
import com.himalayasoni.ledger.core.model.EntryType
import com.himalayasoni.ledger.core.model.Refund
import com.himalayasoni.ledger.core.model.RefundStatus
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * How an entry is stored in the phone's database (a row in the "entries" table).
 * Kept separate from the core [Entry] on purpose: storage details (JSON columns,
 * the "dirty" flag) shouldn't leak into the rest of the app.
 */
@Entity(tableName = "entries", indices = [Index("date"), Index("dirty")])
data class EntryEntity(
    @PrimaryKey val id: String,
    val type: String,
    val amount: Double,
    val date: String,          // yyyy-MM-dd, same as the web app
    val description: String,
    val person: String,
    val category: String,
    val mode: String,
    val note: String,
    val withPeople: String,    // JSON array of names
    val includesMe: Boolean,
    val paidBack: String,      // JSON object: lowercase name → amount
    val prior: Double,
    val createdAt: Long,
    val updatedAt: Long,
    val deleted: Boolean,
    val dirty: Boolean,        // changed here and not uploaded yet (used by sync in Phase 2)
    @ColumnInfo(defaultValue = "") val card: String = "",     // added in database version 2
    @ColumnInfo(defaultValue = "") val refund: String = "",   // JSON object, "" when none (version 2)
)

private fun Refund.toJson(): String = JSONObject().apply {
    put("amount", amount); put("status", status.key); put("confirmed", confirmed.toString())
    received?.let { put("received", it.toString()) }
    put("to", to); put("card", card); put("from", from)
}.toString()

private fun refundFromJson(s: String): Refund? {
    if (s.isBlank()) return null
    val o = JSONObject(s)
    return Refund(
        amount = o.getDouble("amount"),
        status = RefundStatus.fromKey(o.optString("status")) ?: RefundStatus.AWAITED,
        confirmed = LocalDate.parse(o.getString("confirmed")),
        received = o.optString("received").takeIf { it.isNotBlank() }?.let(LocalDate::parse),
        to = o.optString("to"), card = o.optString("card"), from = o.optString("from"),
    )
}

fun EntryEntity.toEntry(): Entry? {
    val t = EntryType.fromKey(type) ?: return null
    return Entry(
        id = id, type = t, amount = amount, date = LocalDate.parse(date),
        description = description, person = person, category = category, mode = mode, note = note,
        withPeople = JSONArray(withPeople).let { a -> List(a.length()) { a.getString(it) } },
        includesMe = includesMe,
        paidBack = JSONObject(paidBack).let { o -> o.keys().asSequence().associateWith { o.getDouble(it) } },
        prior = prior, createdAt = createdAt, updatedAt = updatedAt, deleted = deleted,
        card = card, refund = refundFromJson(refund),
    )
}

fun Entry.toEntity(dirty: Boolean): EntryEntity = EntryEntity(
    id = id, type = type.key, amount = amount, date = date.toString(),
    description = description, person = person, category = category, mode = mode, note = note,
    withPeople = JSONArray(withPeople).toString(),
    includesMe = includesMe,
    paidBack = JSONObject(paidBack as Map<*, *>).toString(),
    prior = prior, createdAt = createdAt, updatedAt = updatedAt, deleted = deleted, dirty = dirty,
    card = card, refund = refund?.toJson().orEmpty(),
)
