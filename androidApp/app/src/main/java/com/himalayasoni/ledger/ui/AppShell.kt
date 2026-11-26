package com.himalayasoni.ledger.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.himalayasoni.ledger.ui.account.AccountSheet
import com.himalayasoni.ledger.ui.account.AccountViewModel
import com.himalayasoni.ledger.ui.add.AddEntrySheet
import com.himalayasoni.ledger.ui.add.AddRequest
import androidx.compose.runtime.CompositionLocalProvider
import com.himalayasoni.ledger.core.model.EntryType
import com.himalayasoni.ledger.ui.components.BottomBar
import com.himalayasoni.ledger.ui.components.LocalRowActions
import com.himalayasoni.ledger.ui.components.RowActions
import com.himalayasoni.ledger.ui.entry.EntryAction
import com.himalayasoni.ledger.ui.entry.EntryPage
import com.himalayasoni.ledger.ui.entry.EntryViewModel
import com.himalayasoni.ledger.ui.entry.MoneyBackForm
import com.himalayasoni.ledger.ui.entry.MoneyBackSheet
import com.himalayasoni.ledger.ui.components.Tab
import com.himalayasoni.ledger.ui.components.ToastBar
import com.himalayasoni.ledger.ui.components.ToastMessage
import com.himalayasoni.ledger.ui.home.HomeTab
import com.himalayasoni.ledger.ui.home.HomeViewModel
import com.himalayasoni.ledger.ui.people.PeopleTab
import com.himalayasoni.ledger.ui.people.PeopleViewModel
import com.himalayasoni.ledger.ui.people.PersonPage
import com.himalayasoni.ledger.ui.people.Undoable
import com.himalayasoni.ledger.ui.theme.LocalLedgerColors
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel

/**
 * The app's frame: which tab is showing, the person page, the back button, the add and
 * account sheets, and toasts. Screens below it are stateless; this is where they're wired up.
 * Navigation is plain state (no navigation library): four tabs, a person or card page, and an
 * entry's page on top of whichever of those you opened it from.
 */
@Composable
fun AppShell(
    home: HomeViewModel = koinViewModel(),
    people: PeopleViewModel = koinViewModel(),
    account: AccountViewModel = koinViewModel(),
    search: com.himalayasoni.ledger.ui.search.SearchViewModel = koinViewModel(),
    insights: com.himalayasoni.ledger.ui.insights.InsightsViewModel = koinViewModel(),
    cards: com.himalayasoni.ledger.ui.cards.CardsViewModel = koinViewModel(),
    entries: EntryViewModel = koinViewModel(),
    openRequest: com.himalayasoni.ledger.OpenRequest? = null,   // from a reminder: open this entry or card
    onOpened: () -> Unit = {},
) {
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var personName by rememberSaveable { mutableStateOf<String?>(null) }
    var cardName by rememberSaveable { mutableStateOf<String?>(null) }
    var entryId by rememberSaveable { mutableStateOf<String?>(null) }      // an entry's page, over whatever was showing
    var moneyBack by remember { mutableStateOf<MoneyBackForm?>(null) }     // the Money back card
    var adding by remember { mutableStateOf<AddRequest?>(null) }
    var showAccount by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<ToastMessage?>(null) }
    val c = LocalLedgerColors.current

    val homeState by home.state.collectAsStateWithLifecycle()
    val accountState by account.state.collectAsStateWithLifecycle()
    val peopleState by people.people.collectAsStateWithLifecycle()
    val personState by people.person.collectAsStateWithLifecycle()
    val searchState by search.state.collectAsStateWithLifecycle()
    val insightsState by insights.state.collectAsStateWithLifecycle()
    val cardState by cards.card.collectAsStateWithLifecycle()
    val entryPage by entries.page.collectAsStateWithLifecycle()

    LaunchedEffect(toast) { if (toast != null) { delay(if (toast?.action != null) 5000 else 2400); toast = null } }
    LaunchedEffect(personName) { personName?.let(people::open) }
    LaunchedEffect(cardName) { cardName?.let(cards::open) }
    LaunchedEffect(entryId) { entries.open(entryId) }
    // the entry went away (deleted from its edit sheet, or on another device): leave its page
    LaunchedEffect(entryPage) { entryPage?.let { p -> if (p.id == entryId && p.detail == null) entryId = null } }
    // a tapped reminder: the card's page, or the entry (once entries have loaded, so this re-runs as Home's state arrives)
    LaunchedEffect(openRequest, homeState) {
        val r = openRequest ?: return@LaunchedEffect
        r.card?.let { personName = null; entryId = null; cardName = it; onOpened() }
        r.entry?.let { id -> entryId = id; onOpened() }
    }

    // Back: entry page → where you were; person page → People; any other tab → Home; Home → leave the app (system default)
    BackHandler(enabled = entryId != null || personName != null || cardName != null || tab != Tab.HOME) {
        when {
            entryId != null -> entryId = null
            cardName != null -> cardName = null
            personName != null -> personName = null
            else -> tab = Tab.HOME
        }
    }

    val say: (String) -> Unit = { toast = ToastMessage(it) }
    val undoable: (Undoable) -> Unit = { u -> toast = ToastMessage(u.message, if (u.undo != null) "Undo" else null) { u.undo?.invoke() } }
    val openEntry: (String) -> Unit = { id -> entryId = id }
    val goTab: (Tab) -> Unit = { personName = null; cardName = null; entryId = null; tab = it }
    val openMoneyBack: (String) -> Unit = { id -> entries.moneyBackForm(id)?.let { moneyBack = it } }
    val duplicateEntry: (String) -> Unit = { id -> entries.duplicate(id)?.let { adding = AddRequest(prefill = it) } }
    // long-press menu and "Mark arrived" chip on every row, in every list
    val rowActions = remember(entries) {
        object : RowActions {
            override fun markArrived(id: String) { entries.markArrived(id, undoable) }
            override fun moneyBack(id: String) = openMoneyBack(id)
            override fun duplicate(id: String) = duplicateEntry(id)
            override fun delete(id: String) { entries.delete(id, undoable) }
        }
    }
    val onEntryAction: (EntryAction) -> Unit = { a ->
        val id = entryId
        val d = entryPage?.detail
        if (id != null) when (a) {
            EntryAction.EDIT -> entries.entry(id)?.let { adding = AddRequest(edit = it) }
            EntryAction.MONEY_BACK -> openMoneyBack(id)
            EntryAction.MARK_ARRIVED -> entries.markArrived(id, undoable)
            EntryAction.DUPLICATE -> duplicateEntry(id)
            EntryAction.DELETE -> { entryId = null; entries.delete(id, undoable) }
            EntryAction.GOT_IT_BACK -> d?.let { adding = AddRequest(type = EntryType.GOT_BACK, person = it.person, amount = it.balance) }
            EntryAction.PAID_BACK -> d?.let { adding = AddRequest(type = EntryType.PAID_BACK, person = it.person, amount = -it.balance) }
        }
    }

    CompositionLocalProvider(LocalRowActions provides rowActions) { Box(Modifier.fillMaxSize().background(c.bg)) {
        when {
            entryId != null -> EntryPage(
                entryPage?.takeIf { it.id == entryId }?.detail, onBack = { entryId = null }, onAction = onEntryAction,
                onCard = { name -> entryId = null; personName = null; cardName = name },
                backLabel = cardName ?: personName ?: when (tab) { Tab.HOME -> "Home"; Tab.PEOPLE -> "People"; Tab.SEARCH -> "Search"; Tab.INSIGHTS -> "Insights" },
            )
            cardName != null -> com.himalayasoni.ledger.ui.cards.CardPage(
                cardState, onBack = { cardName = null },
                onStatementDay = { d -> cardName?.let { cards.setStatementDay(it, d) } },
                onDueDay = { d -> cardName?.let { cards.setDueDay(it, d) } },
                onOpen = openEntry,
                onPayBill = { adding = AddRequest(type = com.himalayasoni.ledger.core.model.EntryType.CARD_BILL, card = cardName) },
            )
            personName != null -> PersonPage(
                personState, onBack = { personName = null }, onAdd = { adding = it }, onOpen = openEntry,
                onSharesPaid = { people.markSharesPaid(it, undoable) }, onFix = { people.fixOldLoan(it, undoable) },
            )
            tab == Tab.HOME -> HomeTab(
                homeState, accountState, onSettings = { showAccount = true }, onAdd = { adding = it }, onOpen = openEntry,
                onSoon = say, onSeeAll = { goTab(Tab.SEARCH) }, onCard = { cardName = it },
            )
            tab == Tab.PEOPLE -> PeopleTab(
                peopleState, onSettings = { showAccount = true }, onPerson = { personName = it },
                onFix = { people.fixOldLoan(it, undoable) }, onAdd = { adding = it },
            )
            tab == Tab.SEARCH -> com.himalayasoni.ledger.ui.search.SearchTab(
                searchState, onSettings = { showAccount = true }, onFilters = search::update, onClear = search::clear, onOpen = openEntry,
            )
            else -> com.himalayasoni.ledger.ui.insights.InsightsTab(
                insightsState, insights, onSettings = { showAccount = true }, onOpen = openEntry,
                onPerson = { personName = it },
                onSeeCategory = { cat -> search.show(com.himalayasoni.ledger.core.domain.Filters(category = cat)); goTab(Tab.SEARCH) },
            )
        }
        BottomBar(
            tab = when { cardName != null -> Tab.HOME; personName != null -> Tab.PEOPLE; else -> tab }, onTab = goTab, onAdd = { adding = AddRequest() },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
        // adding or editing: a full page over everything (the toast after saving shows on top of it)
        adding?.let { req -> AddEntrySheet(req, onDismiss = { adding = null }, onSaved = say) }
        AnimatedVisibility(
            visible = toast != null,
            enter = fadeIn() + slideInVertically { it / 2 }, exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 96.dp, start = 16.dp, end = 16.dp),
        ) { toast?.let { ToastBar(it) { toast = null } } }
    } }
    moneyBack?.let { f ->
        MoneyBackSheet(
            f, onDismiss = { moneyBack = null },
            onSave = { entries.saveMoneyBack(it, undoable) },
            onRemove = { entries.removeMoneyBack(f.entryId, undoable) },
        )
    }
    val privacyState by account.privacy.collectAsStateWithLifecycle()
    if (showAccount) AccountSheet(
        accountState, onSignIn = account::signIn, onSignOut = account::signOut, onDismiss = { showAccount = false },
        privacy = privacyState, onTogglePrivate = account::togglePrivate,
        onShowPrivate = { show -> account.setShowPrivate(show); toast = ToastMessage(if (show) "Private expenses are showing" else "Private expenses hidden") },
    )
}
