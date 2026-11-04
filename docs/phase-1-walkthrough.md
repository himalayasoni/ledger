# Phase 1 walkthrough: the foundation

What was built, why it's shaped this way, and how to explore it yourself.

## What works

- **Ledger β** installs next to your current Ledger.
- **Home:** this month's spending, received, owed to you / you owe, and entries grouped by day.
- **Add sheet:** pick a type, type the amount on the keypad, category, date, payment mode, save.
- Data is stored in the phone's database (Room). It survives restarts. No sync yet; that's Phase 2.

## Read the code in this order

1. **`core/model/Entry.kt` and `EntryType.kt`.** Start with the vocabulary. An `Entry` is one
   line in the ledger; its fields match what the web app stores, so both apps can share data later.
2. **`core/domain/LedgerMath.kt`** holds the rules, written as small functions (`cost()`, `share()`,
   `balances()`). Then open **`core/src/test/.../LedgerMathTest.kt`**: each test is one rule in plain
   English ("money paid back lowers what the bill cost me"). Tests double as documentation.
3. **`core/data/EntryRepository.kt`** is an *interface*: "something that stores entries". The core
   doesn't know or care that it's Room. That's the **dependency inversion** idea: high-level rules
   don't depend on storage details.
4. **`app/data/local/`** is the Room implementation:
   - `EntryEntity`: how a row looks in SQLite
   - `EntryDao`: the SQL
   - `RoomEntryRepository`: adapts Room to the interface
   - the `toEntry()`/`toEntity()` mappers keep storage details out of the rest of the app
5. **`app/di/AppModule.kt`** is the wiring. Koin builds the database, the repository and the ViewModels.
   No class creates its own dependencies, which is what makes them replaceable in tests.
6. **`app/ui/home/`.** `HomeViewModel` turns the repository's live `Flow` into a `StateFlow` of
   `HomeUiState`. Most of the work happens in the pure `buildHomeState()` (tested in `HomeStateTest`).
   `HomeScreen` only draws.
7. **`app/ui/add/`.** `AddEntryViewModel` owns the keypad rules and validation. The sheet sends
   events (`onKey("5")`, `save()`) and draws `AddEntryState`.

## Concepts to look up (in this order)

| Concept | Where you see it |
|---|---|
| Kotlin data classes & `copy()` | `Entry`, `AddEntryState` |
| Extension functions | `fun Entry.cost()` in LedgerMath |
| Coroutines & `suspend` | `EntryRepository.save` |
| `Flow` / `StateFlow` | live queries, ViewModel state |
| Jetpack Compose basics: `@Composable`, state, recomposition | `HomeScreen`, `AddEntrySheet` |
| ViewModel & lifecycle | `HomeViewModel`, `collectAsStateWithLifecycle` |
| Room: Entity, DAO, Database | `data/local/` |
| Dependency injection | `AppModule.kt` |
| Unidirectional data flow (UDF) | the diagram in `docs/architecture.md` |

## Run it on your Mac

1. Android Studio → **Open** → choose the `androidApp` folder (not the repo root).
2. Let Gradle sync finish (first time downloads a lot; that's normal).
3. Run the tests: right-click `core/src/test` → **Run 'Tests in core'**.
4. Run the app: create an emulator (Device Manager → **+**) or plug in your phone with
   USB debugging on, then press ▶.
5. Try a change: in `LedgerMath.kt` break `share()` (e.g. remove `+ if (includesMe) 1 else 0`)
   and run the tests. Watch which test fails and why. Then undo it.

## Install the preview on your phone

GitHub builds it on every change: **Releases → "Ledger β (native preview)" → `ledger-beta.apk`**.

## What's next (Phase 2)

Google sign-in and Firestore sync: your existing entries appear in Ledger β, and changes
flow both ways between the app, the web app and the laptop.
