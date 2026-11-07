package com.himalayasoni.ledger.ui

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.himalayasoni.ledger.core.domain.Usual
import com.himalayasoni.ledger.core.domain.recentPeople
import com.himalayasoni.ledger.core.domain.usuals
import com.himalayasoni.ledger.core.model.Entry
import com.himalayasoni.ledger.core.model.EntryType
import com.himalayasoni.ledger.data.sync.SyncStatus
import com.himalayasoni.ledger.ui.account.AccountUiState
import com.himalayasoni.ledger.ui.add.AddEntryActions
import com.himalayasoni.ledger.ui.add.AddEntryContent
import com.himalayasoni.ledger.ui.add.AddEntryState
import com.himalayasoni.ledger.ui.home.HomeLayout
import com.himalayasoni.ledger.ui.home.buildHomeState
import com.himalayasoni.ledger.ui.theme.LedgerTheme
import androidx.compose.foundation.background
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Screenshot tests: each screen rendered on the JVM (no phone) to a PNG.
 * `./gradlew :app:recordPaparazziDebug` saves them to src/test/snapshots;
 * `verifyPaparazziDebug` fails if a screen changes by accident.
 */
class ScreenshotTest {
    @get:Rule val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_6,
        theme = "android:Theme.Material.Light.NoActionBar",
        maxPercentDifference = 0.1,
    )

    private val today = LocalDate.of(2026, 11, 7)

    private fun e(id: String, daysAgo: Long, type: EntryType, amount: Double, desc: String = "", cat: String = "", person: String = "",
                  with: List<String> = emptyList(), mode: String = "UPI", note: String = ""): Entry {
        val date = today.minusDays(daysAgo)
        val at = date.atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli() + id.hashCode() % 1000
        return Entry(id = id, type = type, amount = amount, date = date, description = desc, category = cat, person = person,
            withPeople = with, mode = mode, note = note, createdAt = at, updatedAt = at)
    }

    // roughly the web app's example entries
    private val entries = listOf(
        e("1", 0, EntryType.SPENT, 240.0, "Uber to office", "Transport"),
        e("2", 0, EntryType.SPENT, 20.0, "Chai", "Food", mode = "Cash"),
        e("3", 1, EntryType.SPENT, 1500.0, "Concert tickets", "Fun", with = listOf("Sneha")),
        e("4", 1, EntryType.SPENT, 1450.0, "DMart groceries", "Groceries", mode = "Card", note = "Monthly stock-up"),
        e("5", 2, EntryType.SPENT, 20.0, "Chai", "Food", mode = "Cash"),
        e("6", 3, EntryType.SPENT, 3200.0, "Dinner at Toit", "Food", with = listOf("Rahul", "Amit"), mode = "Card", note = "Birthday treat"),
        e("7", 5, EntryType.SPENT, 2340.0, "Electricity bill", "Bills"),
        e("8", 6, EntryType.RECEIVED, 12000.0, "Freelance payment", "Freelance", note = "Logo project"),
        e("9", 8, EntryType.BORROWED, 300.0, "Auto fare", person = "Priya", mode = "Cash"),
        e("10", 9, EntryType.SPENT, 600.0, "Movie tickets", "Fun", with = listOf("Priya")),
        e("11", 10, EntryType.GOT_BACK, 500.0, person = "Rahul", note = "Part of last month"),
        e("12", 14, EntryType.SPENT, 20.0, "Chai", "Food", mode = "Cash"),
        e("13", 15, EntryType.SPENT, 649.0, "Netflix", "Fun", mode = "Card"),
        e("14", 24, EntryType.SPENT, 18000.0, "Rent", "Rent"),
        e("15", 33, EntryType.SPENT, 2100.0, "DMart groceries", "Groceries", mode = "Card"),
        e("16", 41, EntryType.LENT, 2000.0, person = "Rahul", mode = "Cash"),
    ).sortedWith(compareByDescending<Entry> { it.date }.thenByDescending { it.createdAt })

    private val account = AccountUiState(configured = true, email = "you@gmail.com", status = SyncStatus.Synced(0))

    private object NoActions : AddEntryActions {
        override fun onType(t: EntryType) {}
        override fun onKey(key: String) {}
        override fun onDescription(v: String) {}
        override fun pickUsual(u: Usual) {}
        override fun onPerson(v: String) {}
        override fun addPerson(name: String) {}
        override fun removePerson(name: String) {}
        override fun onIncludesMe(v: Boolean) {}
        override fun onCategory(c: String) {}
        override fun onMode(m: String) {}
        override fun onDate(d: LocalDate) {}
        override fun onNote(v: String) {}
        override fun setKeypad(on: Boolean) {}
        override fun save() {}
        override fun delete() {}
    }

    private fun home(dark: Boolean) = paparazzi.snapshot {
        LedgerTheme(dark = dark) {
            HomeLayout(buildHomeState(entries, today, ZoneOffset.UTC), account, null, {}, {}, {}, {})
        }
    }

    private fun sheet(dark: Boolean, s: AddEntryState) = paparazzi.snapshot {
        LedgerTheme(dark = dark) {
            androidx.compose.foundation.layout.Box(
                androidx.compose.ui.Modifier.background(if (dark) androidx.compose.ui.graphics.Color(0xFF151F1C) else androidx.compose.ui.graphics.Color.White),
            ) { AddEntryContent(s, NoActions, typing = false, onFocus = {}, onClose = {}, onShowKeypad = {}, onSave = {}) }
        }
    }

    private val fresh = AddEntryState(date = today, allUsuals = entries.usuals(), recentPeople = entries.recentPeople())

    @Test fun home_light() = home(false)
    @Test fun home_dark() = home(true)
    @Test fun add_light() = sheet(false, fresh)
    @Test fun add_dark() = sheet(true, fresh)
    @Test fun add_typed() = sheet(false, fresh.copy(amountText = "1200", description = "Dinner", withPeople = listOf("Rahul", "Amit"), category = "Food"))
    @Test fun add_lent() = sheet(false, fresh.copy(type = EntryType.LENT, typeTouched = true, amountText = "500", person = "", keypadOn = false))
}
