# 0001. Native Kotlin app for Android, the web app for everything else

- **Status:** Accepted
- **Date:** 4 November 2026

## Context

Ledger started as a web app (installable from Chrome) and then got an Android "shell":
the same website running inside Chrome, packaged as an APK. That shell made two
problems obvious:

- Native features (voice, widgets, notifications) had to cross the Chrome boundary,
  which caused double permission prompts and limited what a widget could do.
- The app is now also a learning project and a portfolio piece, so how it is built matters,
  not only whether it works.

Requirements, as of today:

1. **Best possible app on the owner's phone** (Android).
2. **Lightweight:** small download, fast start, works offline.
3. **Maybe iPhone and laptop later.** Unknown, so keep the door open without paying for it now.
4. **Learn architecture properly** along the way.

## Options considered

| | Native Kotlin (Android) + web | React Native (Expo) | Flutter |
|---|---|---|---|
| Android quality | Best (the platform's own toolkit) | Very good | Very good |
| iPhone | Web app now; Kotlin Multiplatform later | Same codebase | Same codebase |
| Laptop / web | Existing web app | Expo web | Flutter web (canvas-based) |
| Widgets, tile, voice, fingerprint | Built in, same language | Native code needed anyway | Native code needed anyway |
| Size of a minimal app (Android) | ~1.5 MB | Several times larger | ~5× native |
| Reuses existing code | No (rewrite in Kotlin) | Yes (JavaScript) | No (Dart) |

## Decision

Build a **native Android app in Kotlin with Jetpack Compose**. Keep the **existing web app**
as the client for the laptop and, if needed, an iPhone (it installs from Safari too).
Both talk to the **same Firebase backend** with the same data format.

The Android app keeps Ledger's rules in a separate **`core` module of plain Kotlin**
(no Android code), so they're fast to test and could later be shared with an iPhone app
through Kotlin Multiplatform.

## Consequences

- **Good:** smallest, fastest app on the main device; native widgets and features without
  workarounds; deep Android and architecture learning; a clear path to iPhone later.
- **Cost:** some logic exists twice (TypeScript in the web app, Kotlin in the app), such as
  the sentence parser and the merge rules. We'll keep them honest with **shared test cases**
  (one list of examples both implementations must pass) instead of trusting them to stay in sync.
- **Budget:** the release APK must stay **under 10 MB**. CI checks this on every build.
- **Revisit if:** an iPhone becomes a real requirement (→ evaluate Kotlin Multiplatform for the
  `core` module and screens), or keeping two implementations in sync becomes painful.
