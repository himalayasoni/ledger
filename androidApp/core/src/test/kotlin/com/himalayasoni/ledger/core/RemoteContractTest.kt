package com.himalayasoni.ledger.core

import com.google.gson.Gson
import com.himalayasoni.ledger.core.domain.cost
import com.himalayasoni.ledger.core.domain.people
import com.himalayasoni.ledger.core.domain.share
import com.himalayasoni.ledger.core.sync.RemoteDoc
import com.himalayasoni.ledger.core.sync.decodeRemote
import com.himalayasoni.ledger.core.sync.encodeRemote
import com.himalayasoni.ledger.core.sync.shouldApplyRemote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * Contract test: decodes the real web-app documents in spec/remote-entries.json and checks
 * each means what it should, then checks that encoding gives the web app back the same fields.
 */
class RemoteContractTest {
    @Suppress("UNCHECKED_CAST")
    private val cases: List<Map<String, Any?>> by lazy {
        val file = listOf("../../spec/remote-entries.json", "spec/remote-entries.json").map(::File).first { it.exists() }
        (Gson().fromJson(file.readText(), Map::class.java)["cases"] as List<Map<String, Any?>>)
    }

    @Suppress("UNCHECKED_CAST")
    @Test fun `every case decodes to what it should mean`() {
        for (case in cases) {
            val name = case["name"] as String
            val remote = case["remote"] as Map<String, Any?>
            val expect = case["expect"] as Map<String, Any?>
            val doc = decodeRemote(case["id"] as String, remote)
            when (expect["kind"]) {
                "entry" -> {
                    val e = (doc as? RemoteDoc.EntryDoc)?.entry ?: fail("$name: expected an entry, got $doc") as Nothing
                    expect["type"]?.let { assertEquals(name, it, e.type.key) }
                    expect["amount"]?.let { assertEquals(name, (it as Number).toDouble(), e.amount, 0.001) }
                    expect["date"]?.let { assertEquals(name, it, e.date.toString()) }
                    expect["description"]?.let { assertEquals(name, it, e.description) }
                    expect["category"]?.let { assertEquals(name, it, e.category) }
                    expect["mode"]?.let { assertEquals(name, it, e.mode) }
                    expect["note"]?.let { assertEquals(name, it, e.note) }
                    expect["person"]?.let { assertEquals(name, it, e.person) }
                    expect["people"]?.let { assertEquals(name, it, e.people()) }
                    expect["includesMe"]?.let { assertEquals(name, it, e.includesMe) }
                    expect["share"]?.let { assertEquals(name, (it as Number).toDouble(), e.share(), 0.001) }
                    expect["cost"]?.let { assertEquals(name, (it as Number).toDouble(), e.cost(), 0.001) }
                    expect["prior"]?.let { assertEquals(name, (it as Number).toDouble(), e.prior, 0.001) }
                }
                "tombstone" -> assertEquals(name, (expect["updatedAt"] as Number).toLong(), (doc as RemoteDoc.Tombstone).updatedAt)
                "categories" -> {
                    val d = doc as RemoteDoc.Categories
                    assertEquals(name, expect["custom"], d.custom)
                    assertEquals(name, expect["removed"], d.removed)
                }
                "privacy" -> assertEquals(name, (expect["privateCategories"] as List<*>).toSet(), (doc as RemoteDoc.Privacy).privateCategories)
                "other" -> assertTrue(name, doc is RemoteDoc.Other)
                else -> fail("$name: unknown kind")
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    @Test fun `encoding gives the web app back the same fields`() {
        for (case in cases) {
            val remote = case["remote"] as Map<String, Any?>
            val doc = decodeRemote(case["id"] as String, remote) as? RemoteDoc.EntryDoc ?: continue
            val encoded = doc.entry.encodeRemote()
            for ((key, value) in remote) {
                if (key == "person" && doc.entry.withPeople.isEmpty() && value != "") continue // legacy field kept as-is
                val got = encoded[key]
                if (value is Number) assertEquals("${case["name"]}: $key", value.toDouble(), (got as Number).toDouble(), 0.001)
                else if (value is Map<*, *>) assertEquals("${case["name"]}: $key", value.mapValues { (it.value as Number).toDouble() }, (got as Map<*, *>).mapValues { (it.value as Number).toDouble() })
                else assertEquals("${case["name"]}: $key", value, got)
            }
        }
    }

    @Test fun `the most recent edit wins`() {
        assertTrue(shouldApplyRemote(null, 5))
        assertTrue(shouldApplyRemote(4, 5))
        assertTrue(!shouldApplyRemote(5, 5))
        assertTrue(!shouldApplyRemote(6, 5))
    }
}
