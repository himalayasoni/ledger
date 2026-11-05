# 0002. Offline-first sync: the phone's database is the truth, Firestore is the mailbox

- **Status:** Accepted
- **Date:** 5 November 2026

## Context

The native app must show the same entries as the web app, work with no signal,
and stay small. Firestore already holds the web app's data at `users/{uid}/entries/{id}`,
and its SDK can also act as an offline database on its own.

## Decision

1. **Room is the only source of truth on the phone.** Screens read only from Room.
   Firestore runs with a *memory* cache, not its disk cache, so the data isn't stored twice.
2. **Changes are marked, not sent.** Saving or deleting writes to Room with `dirty = 1`.
   A separate sync engine (`FirestoreSync`) uploads dirty rows in batches, then marks
   them clean. Saving never waits on the network.
3. **Deletions are tombstones** (`{id, deleted: true, updatedAt}`), so a delete reaches
   devices that were offline when it happened.
4. **Newest edit wins** (compare `updatedAt`), the same rule as the web app.
5. **Download only what changed.** Every write is stamped with the server's clock (`srv`).
   The phone remembers the newest `srv` it has seen and listens only for documents after it.
   Server time is used because phone clocks can be wrong.
6. **One shared format, tested from both sides.** `spec/remote-entries.json` lists real
   documents and what they mean. The Kotlin decoder is tested against it, so a web change
   that breaks the phone fails CI rather than corrupting data.

## Consequences

- The app works the same with or without signal. The header shows "Syncing…" or "Synced 10:42".
- Two edits to the *same* entry on two offline devices: the later one wins whole
  (no per-field merge). Fine for a personal ledger. Revisit if Ledger becomes shared.
- Sign-in is optional. Without it, everything still works, on this phone only.
- If the phone's clock is far off, its edits could lose or win unfairly against the web app.
  Accepted: phones set their clocks from the network.

## Alternatives rejected

- **Firestore's own offline cache as the database.** Less code, but the screens would depend
  on Firebase types, the data would be stored twice, and the core rules couldn't be tested
  without Firebase.
- **WorkManager for uploads.** More robust for very long offline periods, but heavier.
  Pending changes are retried each time the app opens, which is enough for now.
