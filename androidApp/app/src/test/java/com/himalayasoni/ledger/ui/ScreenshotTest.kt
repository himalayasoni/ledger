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
import com.himalayasoni.ledger.ui.home.HomeTab
import com.himalayasoni.ledger.ui.components.BottomBar
import com.himalayasoni.ledger.ui.components.Tab
import com.himalayasoni.ledger.ui.people.PeopleTab
import com.himalayasoni.ledger.ui.people.PersonPage
import com.himalayasoni.ledger.ui.people.buildPeopleState
import com.himalayasoni.ledger.ui.people.buildPersonState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.himalayasoni.ledger.ui.theme.LocalLedgerColors
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
        e("18", 2, EntryType.SPENT, 1500.0, "Train to Jaipur", "Travel").copy(
            refund = com.himalayasoni.ledger.core.model.Refund(1320.0, com.himalayasoni.ledger.core.model.RefundStatus.AWAITED, today.minusDays(1))),
        e("19", 1, EntryType.SPENT, 2499.0, "Running shoes", "Shopping", mode = "Card").copy(card = "HDFC"),
        e("20", 0, EntryType.CARD_BILL, 2000.0, mode = "UPI").copy(card = "HDFC"),
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
        override fun applySaid(text: String) {}
        override fun addCategory(name: String) {}
        override fun onCard(name: String) {}
        override fun useAmount(amount: Double) {}
        override fun linkTo(id: String?) {}
        override fun onRefund(change: (AddEntryState) -> AddEntryState) {}
    }

    /** A tab inside the app frame (background + bottom bar), like AppShell draws it. */
    private fun framed(dark: Boolean, tab: Tab, content: @androidx.compose.runtime.Composable () -> Unit) = paparazzi.snapshot {
        LedgerTheme(dark = dark) {
            Box(Modifier.fillMaxSize().background(LocalLedgerColors.current.bg)) {
                content()
                BottomBar(tab, {}, {}, Modifier.align(Alignment.BottomCenter))
            }
        }
    }

    private fun home(dark: Boolean) = framed(dark, Tab.HOME) {
        HomeTab(buildHomeState(entries, today, ZoneOffset.UTC), account, {}, {}, {}, {}, {})
    }

    @Test fun people() = framed(false, Tab.PEOPLE) {
        PeopleTab(buildPeopleState(entries + kanika, entries + kanika, today), {}, {}, {}, {})
    }

    @Test fun person() = framed(false, Tab.PEOPLE) {
        PersonPage(buildPersonState("Rahul", entries, entries, today), {}, {}, {}, {}, {})
    }

    @Test fun search() = framed(false, Tab.SEARCH) {
        com.himalayasoni.ledger.ui.search.SearchTab(
            com.himalayasoni.ledger.ui.search.buildSearchState(entries, com.himalayasoni.ledger.core.domain.Filters(), today), {}, {}, {}, {},
        )
    }

    @Test fun search_filtered() = framed(false, Tab.SEARCH) {
        val f = com.himalayasoni.ledger.core.domain.Filters(query = "dinner", person = "Rahul", sort = com.himalayasoni.ledger.core.domain.SortBy.BIGGEST)
        com.himalayasoni.ledger.ui.search.SearchTab(com.himalayasoni.ledger.ui.search.buildSearchState(entries, f, today), {}, {}, {}, {})
    }

    private object NoInsights : com.himalayasoni.ledger.ui.insights.InsightsActions {
        override fun yearView(on: Boolean) {}
        override fun step(by: Int) {}
        override fun openMonth(m: java.time.YearMonth) {}
        override fun pickDay(d: LocalDate) {}
        override fun toggleCategory(c: String) {}
        override fun trendCategory(c: String) {}
        override fun trendYears(on: Boolean) {}
        override fun trendBar(i: Int) {}
    }

    /** Insights is long, so it's captured as several screens, each scrolled to a different card. */
    private fun insights(sel: com.himalayasoni.ledger.ui.insights.InsightsSelection, vararg firstItems: Int) {
        for (first in firstItems) paparazzi.snapshot("from$first") {
            LedgerTheme(dark = false) {
                Box(Modifier.fillMaxSize().background(LocalLedgerColors.current.bg)) {
                    com.himalayasoni.ledger.ui.insights.InsightsTab(
                        com.himalayasoni.ledger.ui.insights.buildInsights(entries, sel, today), NoInsights, {}, {}, {}, {},
                        listState = androidx.compose.foundation.lazy.rememberLazyListState(first),
                    )
                }
            }
        }
    }

    @Test fun insights_month() = insights(com.himalayasoni.ledger.ui.insights.InsightsSelection(
        month = java.time.YearMonth.of(2026, 11), openCategory = "Food"), 0, 4, 5, 6, 7, 9)

    @Test fun insights_year() = insights(com.himalayasoni.ledger.ui.insights.InsightsSelection(
        yearView = true, year = 2026, month = java.time.YearMonth.of(2026, 11), trendYears = true), 0, 5)

    // a repayment with no loan in Ledger, to show the "Check this balance" card
    private val kanika = e("17", 4, EntryType.PAID_BACK, 700.0, person = "Kanika")

    private fun sheet(dark: Boolean, s: AddEntryState, typing: Boolean = false) = paparazzi.snapshot {
        LedgerTheme(dark = dark) {
            androidx.compose.foundation.layout.Box(
                androidx.compose.ui.Modifier.background(if (dark) androidx.compose.ui.graphics.Color(0xFF151F1C) else androidx.compose.ui.graphics.Color.White),
            ) { AddEntryContent(s, NoActions, typing = typing, onFocus = {}, onClose = {}, onShowKeypad = {}, onSave = {}) }
        }
    }

    private val fresh = AddEntryState(date = today, allUsuals = entries.usuals(), recentPeople = entries.recentPeople())

    @Test fun home_light() = home(false)
    @Test fun home_dark() = home(true)
    @Test fun add_light() = sheet(false, fresh)
    @Test fun add_dark() = sheet(true, fresh)
    @Test fun add_typed() = sheet(false, fresh.copy(amountText = "1200", description = "Dinner", withPeople = listOf("Rahul", "Amit"), category = "Food"))
    @Test fun add_heard() = sheet(false, fresh.copy(type = EntryType.LENT, typeTouched = true, amountText = "200", person = "Ashish",
        heard = "lent 200 to ashish", heardNote = "Check it and tap Save.", keypadOn = false))
    @Test fun add_refund() = sheet(false, fresh.copy(
        editing = entries.first { it.id == "18" }, type = EntryType.SPENT, typeTouched = true, amountText = "1500",
        description = "Train to Jaipur", category = "Travel", keypadOn = false,
        refundOn = true, refundFull = false, refundText = "1320", cards = listOf("HDFC", "SBI"),
    ))
    @Test fun add_cardbill() = sheet(false, fresh.copy(
        type = EntryType.CARD_BILL, typeTouched = true, amountText = "12000", card = "HDFC", cards = listOf("HDFC", "SBI"), keypadOn = false,
    ))
    @Test fun card_page() = framed(false, Tab.HOME) {
        com.himalayasoni.ledger.ui.cards.CardPage(com.himalayasoni.ledger.ui.cards.buildCardState(entries, "HDFC", 5, today), {}, {}, {}, {})
    }
    @Test fun add_typing() = sheet(false, fresh.copy(amountText = "240", description = "Uber", keypadOn = false), typing = true)
    @Test fun add_received_linked() = sheet(false, fresh.copy(
        type = EntryType.RECEIVED, typeTouched = true, amountText = "1500", person = "Dad", keypadOn = false,
        linkable = listOf(com.himalayasoni.ledger.ui.add.LinkOption("6", "Dinner at Toit", "₹3,200 · 4 Nov")), linkTo = "6",
    ))
    @Test fun add_moneyback_person() = sheet(false, fresh.copy(
        editing = entries.first { it.id == "2" }, type = EntryType.SPENT, typeTouched = true, amountText = "1500", description = "Birthday treat",
        category = "Food", keypadOn = false, refundOn = true, refundFrom = "Dad", refundArrived = true, refundTo = "UPI",
    ))
    @Test fun add_lent() = sheet(false, fresh.copy(type = EntryType.LENT, typeTouched = true, amountText = "500", person = "", keypadOn = false))
}
