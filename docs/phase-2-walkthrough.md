# Phase 2 walkthrough: Google sign-in and sync

Ledger β now signs in with your Google account and syncs both ways with the web app.
The design is in [ADR 0002](decisions/0002-offline-first-sync.md).

## What works

- **Account icon (top right) → Sign in with Google.** Android's own account picker, no browser.
- Your web entries appear on the phone; entries added on the phone appear on the web.
- Deleting syncs too. Private categories set on the web are hidden on the phone.
- No signal? Everything still saves. It uploads when you're back online.
- The line under "Ledger" shows the state: *Syncing…*, *Synced 10:42*, or *Sync problem*.

## Read the code in this order

1. `spec/remote-entries.json`: what a document in Firestore looks like, case by case.
2. `core/.../sync/RemoteCodec.kt`: turns those documents into `Entry` and back.
   Plain Kotlin, no Firebase. `RemoteContractTest` checks it against the spec file.
3. `app/.../data/local/EntryDao.kt`: the new queries: `dirty()`, `markClean()`
   (only if nothing changed since the upload started), `applyTombstone()`.
4. `app/.../data/auth/AuthRepository.kt`: Credential Manager → Google ID token → Firebase.
   An *interface* plus one implementation, so screens never touch Firebase directly.
5. `app/.../data/sync/FirestoreSync.kt`: the engine. Read `start()`, then `runFor()`,
   then `applyRemote()` and `upload()`.
6. `app/.../ui/account/`: the sign-in sheet and its ViewModel.
7. `app/.../di/AppModule.kt`: where all of it is plugged together.

## Concepts to look up

1. **Offline-first** and **"single source of truth"** (Android docs, "Build an offline-first app").
2. **Tombstones** in sync systems: why a delete has to be a record.
3. **Last-write-wins** and its weakness (clock skew).
4. **Credential Manager** (the current way to do Google sign-in on Android).
5. Coroutines: `collectLatest` (cancels the old account's sync when you switch),
   `debounce`, `Mutex` (two uploads never run at once).
6. **Dependency inversion**: `HomeViewModel` asks for a `SettingsRepository`, not for
   `SyncPrefs`. A test can hand it a fake.

## Try this

- Turn on airplane mode, add an entry, turn it off. Watch the header go from *Syncing…*
  to *Synced*, then check the web app.
- In `RemoteCodec.kt`, change `"paidBack"` to `"paidback"` and run `./gradlew :core:test`.
  The contract test catches it. That test is the reason the web and the app can't drift apart.

## What's next (Phase 3)

People and balances, Insights, search and filters, and private categories you can show
with your fingerprint.
