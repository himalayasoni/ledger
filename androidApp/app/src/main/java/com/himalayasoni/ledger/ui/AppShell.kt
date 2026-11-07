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
import com.himalayasoni.ledger.ui.components.BottomBar
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
 * Navigation is plain state (no navigation library): four tabs and one detail page.
 */
@Composable
fun AppShell(
    home: HomeViewModel = koinViewModel(),
    people: PeopleViewModel = koinViewModel(),
    account: AccountViewModel = koinViewModel(),
    search: com.himalayasoni.ledger.ui.search.SearchViewModel = koinViewModel(),
    insights: com.himalayasoni.ledger.ui.insights.InsightsViewModel = koinViewModel(),
) {
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var personName by rememberSaveable { mutableStateOf<String?>(null) }
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

    LaunchedEffect(toast) { if (toast != null) { delay(if (toast?.action != null) 5000 else 2400); toast = null } }
    LaunchedEffect(personName) { personName?.let(people::open) }

    // Back: person page → People; any other tab → Home; Home → leave the app (system default)
    BackHandler(enabled = personName != null || tab != Tab.HOME) {
        if (personName != null) personName = null else tab = Tab.HOME
    }

    val say: (String) -> Unit = { toast = ToastMessage(it) }
    val undoable: (Undoable) -> Unit = { u -> toast = ToastMessage(u.message, if (u.undo != null) "Undo" else null) { u.undo?.invoke() } }
    val openEntry: (String) -> Unit = { id -> home.entry(id)?.let { adding = AddRequest(edit = it) } }
    val goTab: (Tab) -> Unit = { personName = null; tab = it }

    Box(Modifier.fillMaxSize().background(c.bg)) {
        when {
            personName != null -> PersonPage(
                personState, onBack = { personName = null }, onAdd = { adding = it }, onOpen = openEntry,
                onSharesPaid = { people.markSharesPaid(it, undoable) }, onFix = { people.fixOldLoan(it, undoable) },
            )
            tab == Tab.HOME -> HomeTab(
                homeState, accountState, onSettings = { showAccount = true }, onAdd = { adding = it }, onOpen = openEntry,
                onSoon = say, onSeeAll = { goTab(Tab.SEARCH) },
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
            tab = if (personName != null) Tab.PEOPLE else tab, onTab = goTab, onAdd = { adding = AddRequest() },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
        AnimatedVisibility(
            visible = toast != null,
            enter = fadeIn() + slideInVertically { it / 2 }, exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 96.dp, start = 16.dp, end = 16.dp),
        ) { toast?.let { ToastBar(it) { toast = null } } }
    }
    adding?.let { req -> AddEntrySheet(req, onDismiss = { adding = null }, onSaved = say) }
    val privacyState by account.privacy.collectAsStateWithLifecycle()
    if (showAccount) AccountSheet(
        accountState, onSignIn = account::signIn, onSignOut = account::signOut, onDismiss = { showAccount = false },
        privacy = privacyState, onTogglePrivate = account::togglePrivate,
        onShowPrivate = { show -> account.setShowPrivate(show); toast = ToastMessage(if (show) "Private expenses are showing" else "Private expenses hidden") },
    )
}
