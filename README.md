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
