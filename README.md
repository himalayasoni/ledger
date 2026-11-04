# Ledger

A personal money diary: expenses, lending, shared bills and insights, with an
offline-first **native Android app** (Kotlin, Jetpack Compose) and a **web app**,
synced through Firebase.

- **Architecture:** [docs/architecture.md](docs/architecture.md)
- **Decisions:** [docs/decisions/](docs/decisions/)
- **Current phase:** [docs/phase-1-walkthrough.md](docs/phase-1-walkthrough.md)

| Part | Tech | Where |
|---|---|---|
| Android app | Kotlin · Compose · Room · Koin · Coroutines/Flow | `androidApp/` |
| Web app | HTML/JS · IndexedDB · service worker | repo root |
| Backend | Firebase Auth · Firestore · security rules | Firebase project |
| CI/CD | GitHub Actions: tests, size budget, signed releases | `.github/workflows/` |

---

## Web app notes

# Ledger

A personal money diary: expenses, lending and borrowing, and spending shared with people.

- Installs on your phone from the browser (Add to Home screen) and works offline.
- Entries are saved on the device first (IndexedDB).
- Optional sync to your own Firebase project with Google sign-in, so the phone and laptop share one diary.

## Setup
1. Put your Firebase web config in `config.js` (`window.LEDGER_FIREBASE = {...}`).
2. In Firebase Authentication, add this site's domain under **Authorized domains**.
3. Firestore rules should allow only your own account to read and write `users/{uid}/**`.

No build step: the site is plain files served by GitHub Pages.
