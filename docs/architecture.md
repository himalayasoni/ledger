# Ledger architecture

Ledger is a personal money diary: expenses, lending, shared bills, insights.
It has two clients that share one backend.

```
              ┌──────────────────────────┐
              │  Firebase (Google)       │
              │  · Auth: Google sign-in  │
              │  · Firestore: entries    │
              │  · Security rules:       │
              │    only you, your data   │
              └────────────┬─────────────┘
                 sync      │       sync
        ┌──────────────────┴──────────────────┐
┌───────┴────────┐                    ┌────────┴────────┐
│ Android app    │                    │ Web app         │
│ Kotlin/Compose │                    │ HTML/JS (PWA)   │
│ androidApp/    │                    │ repo root       │
└────────────────┘                    └─────────────────┘
```

## Android app (`androidApp/`)

Two Gradle modules:

| Module | What's in it | Depends on Android? |
|---|---|---|
| `core` | The rules: entry model, money formatting, totals, balances, shared-bill maths, the repository *interface* | No: plain Kotlin, tests run in milliseconds |
| `app` | Screens (Compose), ViewModels, the database (Room), dependency wiring (Koin) | Yes |

Inside `app`, code is organised by layer:

```
app/src/main/java/com/himalayasoni/ledger/
  LedgerApp.kt          starts Koin when the app launches
  MainActivity.kt       the only Activity; hosts Compose
  di/AppModule.kt       the wiring diagram: what gets built from what
  data/local/           Room: table (EntryEntity), queries (EntryDao), database, repository
  ui/theme/             colours and Material theme
  ui/home/              Home screen + HomeViewModel + pure state builder
  ui/add/               Add sheet + AddEntryViewModel
```

### How data flows (unidirectional)

```
  you tap "Save"
        │  event
        ▼
  AddEntryViewModel ──► EntryRepository.save() ──► Room (phone database)
                                                        │ change
                                                        ▼
  HomeScreen ◄── HomeUiState ◄── HomeViewModel ◄── Flow<List<Entry>>
     draws          formatted       maps with           live query
                    strings         core rules
```

- **State flows down, events flow up.** Screens never change data directly; they send
  events to a ViewModel. The ViewModel produces an immutable state; the screen draws it.
- **The database is the source of truth.** Home doesn't "add a row" after saving; it just
  re-renders when Room's live query emits the new list. One path, no drift.
- **Pure functions where possible.** `buildHomeState()` turns entries into screen state with
  no Android code, so it's unit-tested like the core rules.

### Storage and sync

See [ADR 0002](decisions/0002-offline-first-sync.md).

- Room is the source of truth. Every local change is marked `dirty`. Deletions are kept as
  tombstones so they reach other devices.
- `FirestoreSync` uploads dirty rows in batches and listens only for documents newer than the
  last server timestamp it has seen. On conflicts, the newer `updatedAt` wins (same as the web).
- The document format is shared with the web app and pinned by `spec/remote-entries.json`.

## Web app (repo root)

`index.html` (+ `sw.js`, `manifest.webmanifest`, `config.js`). Stores entries in IndexedDB,
syncs through the same Firestore collection: `users/{uid}/entries/{id}`.

## Repository layout

| Path | What |
|---|---|
| `/` | Web app (served by GitHub Pages) |
| `androidApp/` | Native Android app (this architecture) |
| `android/` | Old Chrome-shell app, to be retired when the native app reaches parity |
| `docs/` | This file, decision records, phase walkthroughs |
| `.github/workflows/` | CI: tests, builds, size budget, releases |

## Quality gates (CI)

On every change to `androidApp/`:
1. Unit tests: `core` rules and screen-state builders.
2. Signed release build with R8 shrinking.
3. **Size budget:** release APK must be under 10 MB.
4. Preview release (`native-vN`, marked as pre-release so the current app ignores it).
