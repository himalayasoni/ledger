# Phase 5 walkthrough: logging without opening the app, and reminders

Ledger β (0.9.0) can now take an entry from the home screen, the app icon, Quick Settings or a
notification, without the app itself opening. It can also remind you about four things.

## What each piece is

| You see | What it is | Code |
| --- | --- | --- |
| A sheet over the home screen | **Quick add pop-up**: the normal add sheet in its own see-through activity | `ui/quickadd/QuickAddActivity.kt` |
| Long-press the icon: Add · Say it · Card bill | **Static app shortcuts** | `res/xml/shortcuts.xml`, `sc_*.xml` icons |
| "Add to Ledger" in Quick Settings | **TileService** | `ui/quickadd/QuickAddTile.kt` |
| Small widget (2×1): + and mic | **Glance widget** | `widget/SmallWidget.kt` |
| Medium widget (4×2): totals + one-tap usuals | **Glance widget** + **ActionCallback** | `widget/MediumWidget.kt`, `widget/WidgetActions.kt` |
| "Anything to log today?" → type "chai 20 cash" | **Notification with RemoteInput** | `reminders/Notifications.kt`, `reminders/QuickLogReceiver.kt` |
| Refund, card bill and weekly people reminders | **AlarmManager** alarms + a receiver | `reminders/ReminderScheduler.kt`, `reminders/ReminderReceiver.kt` |
| Settings → Reminders | Toggles, a time picker, "Hide amounts on widgets" | `ui/account/RemindersSection.kt`, `data/prefs/ReminderPrefs.kt` |

## Files to read, in order

1. `core/domain/QuickLog.kt`: a usual or a typed line becomes an entry (same rules as Save).
2. `core/domain/Widget.kt`: exactly what the widget says. Private categories are always left out.
3. `core/domain/Reminders.kt`: when alarms go off and what each reminder says. Tests:
   `QuickLogTest.kt`, `WidgetAndRemindersTest.kt`.
4. `data/QuickLogger.kt`: saves those entries through the normal repository (so they sync).
5. `ui/quickadd/QuickAddActivity.kt`, then the `<activity>` entry in `AndroidManifest.xml`.
6. `widget/LedgerWidgets.kt`, `SmallWidget.kt`, `MediumWidget.kt`, `WidgetActions.kt`.
7. `reminders/ReminderScheduler.kt`, `ReminderReceiver.kt`, `Notifications.kt`, `QuickLogReceiver.kt`.
8. `LedgerApp.kt`: what starts with the process (channels, alarms, keeping widgets fresh).

## How each entry point works

- **Pop-up**: widgets, shortcuts, the tile and notifications all build their intent with
  `QuickAdd.intent(...)` (extras: `type`, `listen`, `said`). The activity has its own
  `taskAffinity`, `launchMode="singleTask"` and `excludeFromRecents`, so it opens over whatever is on
  screen and leaves nothing behind. Its window is transparent; the sheet draws its own dim. It uses
  a second `AddEntryViewModel` (Koin name `quickAdd`) built on `PrivateAlwaysHidden`, so private
  categories never show there even if you unlocked them in the app.
- **Widget chips**: a tap runs `LogUsualAction`. It saves the usual again (new id, today), puts
  "Logged Chai ₹20" into that widget's own Glance state, redraws, waits 5 s, clears it. Tapping the
  bar runs `UndoUsualAction`, which deletes the entry.
- **Widget freshness**: `LedgerWidgets.keepFresh` watches the widget text (entries + private list +
  hide setting) and redraws when it changes; an alarm just after midnight resets "today".
- **Reply**: the Reply button's PendingIntent goes to `QuickLogReceiver`. It reads the typed text,
  parses it with the shared voice parser, saves it if complete, and re-posts the notification as
  "Logged ₹20 · Chai" with Undo. If something is missing (amount, person, card), the notification
  says so and tapping it opens the pop-up already filled in.
- **Reminders**: one alarm each for the daily nudge (your time), a 10:00 am check (refunds and card
  bills), Sunday 7 pm (people) and midnight (widgets). Each alarm, when it fires, sets the next one.
  Reminded refund and bill keys are remembered so each nags once.

## Concepts to look up

- **Jetpack Glance**: write a widget like Compose (`Row`, `Text`), Glance turns it into
  `RemoteViews`, the restricted layouts a launcher can draw for another app. No Material, no custom
  fonts; `cornerRadius` only works on Android 12+.
- **ActionCallback**: Glance's way to run code on a widget tap. Created by class name (hence the
  keep rule in `proguard-rules.pro`), parameters passed with `actionParametersOf`.
- **Glance state**: each widget has a small Preferences store (`updateAppWidgetState`,
  `currentState(key)`); used here for the Undo bar.
- **RemoteInput**: a text box inside a notification action. Android puts the text into the
  broadcast intent, which is why that one PendingIntent must be `FLAG_MUTABLE`.
- **PendingIntent flags**: `FLAG_IMMUTABLE` (nobody can change what it does: the default here),
  `FLAG_MUTABLE` (only for RemoteInput), `FLAG_UPDATE_CURRENT` (replace the extras of an existing
  one with the same request code).
- **AlarmManager vs WorkManager**: alarms are "around this time", built in, tiny, and need
  rescheduling after reboot (`BOOT_COMPLETED`). WorkManager survives reboots and handles retries and
  network constraints, which these reminders don't need. `setWindow` gives a 10-minute window and
  needs no exact-alarm permission.
- **Notification trampolines**: since Android 12 a receiver started from a notification can't open
  a screen, which is why an incomplete reply asks you to tap.
- **TileService**: `startActivityAndCollapse(PendingIntent)` on Android 14+, an Intent before.
  `unlockAndRun` asks for the phone lock first on the lock screen.
- **Static shortcuts**: `res/xml/shortcuts.xml`, linked from the launcher activity with
  `android.app.shortcuts` meta-data. `targetPackage` must be the applicationId.
- **POST_NOTIFICATIONS** (Android 13+): asked once, when you first switch a reminder on.

## 0.10.0: an entry's page, row actions, money back, the mini quick add

Tapping an entry no longer opens the edit form. It opens the entry's page; editing is one of its
buttons. Money back moved out of the form (you only know about it after you've paid) into its own
small card. And there's a much smaller quick add, with its own app icon for the S23's side key.

| You see | What it is | Code |
| --- | --- | --- |
| Tap any entry (Home, Search, a person, Insights, a card) | **Entry page**: amount after money back ("₹1,500 paid" under it), what it was, date, category, payment (card spends link to the card), shares and who paid back, the loan's person, the comment, the money back | `ui/entry/EntryDetailState.kt` (pure builder), `EntryPage.kt` (stateless), wired in `ui/AppShell.kt` |
| Its buttons: Edit · Got money back / Edit money back · Mark arrived · Duplicate · Delete (· Got it back / I paid back for a loan with a balance) | `EntryAction`, handled in `AppShell` | `ui/entry/EntryViewModel.kt` |
| "Mark arrived" chip on a row whose refund is awaited | One tap, toast "Refund marked arrived · Undo" | `ui/components/EntryList.kt` (`MiniChip`) |
| Long-press a row: Got money back · Mark arrived · Duplicate · Delete | `combinedClickable` + a haptic tick, a `DropdownMenu` in the app's colours | `EntryRowItem`, `RowActions`, `LocalRowActions` |
| "Money back" card | From (shop refund, a person, someone else) · How much (full or part) · Awaited / Arrived (date, came back to, card) · "Counts as ₹180 spent" | `ui/entry/MoneyBackForm.kt` (pure), `MoneyBack.kt` |
| Mini quick add | Amount, keypad with Save, "What was it?", up to 6 usual chips, mic, "More" for the full form | `MiniAddContent` in `ui/add/AddEntrySheet.kt` |
| 1×1 widget "Ledger: +" | A marigold "+" that opens the mini quick add | `widget/TinyWidget.kt`, `res/xml/widget_tiny.xml` |

### The rules (shared with the web app)

`core/domain/EntryActions.kt`, tested in `EntryActionsTest.kt`:

- `withMoneyBack(amount, from, arrived, confirmed, received, to, card, now)`: spends only; the amount is
  capped at what was paid; awaited money back has no arrival date or destination.
- `markRefundArrived(today, now)`: arrived today, back to where it was set to come back, else to the
  card for a card spend, else to the way it was paid (or UPI).
- `duplicateAsNew(id, today, now)`: same amount, description, category, payment, card, person and
  split, as a new entry today, without money back, paid-back shares, old-loan part or comment.
- `withoutMoneyBack(now)`.

Editing an entry keeps its money back exactly as it was (cut down if you lower the amount below it, and
dropped if you change it into something other than a spend). The form no longer touches it.

### How the pieces fit

- **Navigation** is still plain state in `AppShell`: `entryId` sits on top of the tab, person or card
  page you opened it from; Back closes it. If the entry disappears (deleted in its edit sheet, or on
  another device), the page closes itself.
- **Row actions without threading callbacks through every screen**: `AppShell` provides a `RowActions`
  through a `CompositionLocal` (`LocalRowActions`). Every `EntryRowItem` reads it, so Home, Search,
  People, Insights and the card page all get the menu. Screenshot tests provide none, so rows there
  have no menu (the chip still draws).
- **Undo**: `EntryViewModel` keeps the entry as it was before each change and saves it back (with a
  fresh `updatedAt`, so the restored copy also wins on your other devices). Delete is a tombstone, and
  Undo saves the entry again with `deleted = false`.
- **Mini quick add**: `QuickAdd.requestFrom` opens the mini layout when there are no extras (the
  1×1 widget, the small widget's +, the "Quick add" shortcut, the tile). A type, "say it" or a line
  to finish open the full form. Mini mode shows your categories as chips, most used in the last 90 days
  first (`categoriesByUse` in :core). If you type a description without picking one, it guesses: the
  category you used last time for the same description, else what the voice parser makes of it, else Other.
  Payment starts as your last spend's. "More" switches the same state to the full form, so nothing
  typed is lost. Saying something that isn't a plain spend (a loan, a split) also opens the full form.
- **No second icon**: an early version added a "Ledger +" launcher icon (an `activity-alias`). Samsung's
  side-key list shows one entry per app, so it couldn't be picked there; it was removed in favour of the
  "Quick add" app shortcut, which RegiStar can open directly.

### Opening the mini quick add on a Galaxy S23

Samsung's Settings → Advanced features → Side button → Double press → Open app only opens an app's
*launcher entry*: no shortcuts, no second icons. So the launcher entry itself decides.

- `MainActivity` is now an invisible router (`Theme.NoDisplay`). The real app moved to `LedgerActivity`.
  The name `MainActivity` stayed so the home-screen icon and the side-button choice keep working.
- A tap on the icon comes from the home screen app: `getReferrer()` is the launcher's package
  (`com.sec.android.app.launcher`), and `intent.sourceBounds` is set for the opening animation.
  The side button is started by the system, with neither. Icon → `LedgerActivity`; anything else →
  the quick add pop-up.
- The router has `taskAffinity=""` and `excludeFromRecents`, so each launch gets a fresh, throwaway task.
  If it lived in the app's task, a second press would just bring the app back without running the router.
  `LedgerActivity` is `singleTask`, so the icon, widgets and notifications always reach the one copy.
- Settings → Side button: "Quick add" (default), "Full app", or "Quick add, icon too". It also shows who
  opened Ledger last and how that was counted, to check the detection on a real phone. The pop-up's
  home button opens the full app.

Other ways in: RegiStar (back tap, side-key press and hold) can open the **Quick add** app shortcut,
the Quick settings tile "Add to Ledger", the "Quick add" shortcut dragged to the home screen, or the 1×1 widget.

### Concepts to look up

- **Trampoline activity**: an activity that only starts another and finishes. `Theme.NoDisplay` requires
  calling `finish()` before `onResume`.
- **`getReferrer()`** and **task affinity**: who started you, and which task an activity lands in.

- **`combinedClickable`**: `clickable` plus long-press and double-tap. `LocalHapticFeedback` gives the
  little buzz a long press should have.
- **`CompositionLocal`**: a value any composable below can read without it being passed as a parameter
  (how `MaterialTheme` colours reach every `Text`). Good for app-wide services like the row actions;
  not for screen data, which still goes in as parameters.
- **`activity-alias`** and **adaptive icons** (`<adaptive-icon>`: background, foreground and a
  `monochrome` layer for Android 13+ themed icons; keep the drawing inside the middle 66dp of 108dp).

## Add is a page, not a sheet (v17 / web v21)

A sheet as tall as the add form kept fighting itself: dragging the form could drag the sheet, the sheet could
close mid-scroll, and the keyboard stayed up while you scrolled. The fix was to stop using a sheet for it.

- **Android**: `AddEntrySheet` shows the full form as a page drawn over the app (inside `AppShell`'s Box, with a
  `BackHandler` that closes it). The mini quick add stays a small sheet, since it never scrolls. In the pop-up,
  "More" switches from the sheet to the page in place.
- **Web**: `#sheet` is pinned to the visible screen's height (`--vvh`), with no rounded top and no grab handle.
- **Scrolling by hand puts the keyboard away**: Android watches drags through a `NestedScrollConnection`
  (`NestedScrollSource.Drag`, so the form scrolling itself to show a box doesn't count). The web uses `touchmove`.
- **The type-or-say bar** moved to the top, dark, with the mic inside. Once you type, the mic turns into a
  "fill the form" arrow. It no longer looks like the form's own fields.
- **"Your usuals" is gone** from the form. Typing in "What was it?" still suggests past entries that match.

## Share a payment screenshot to Ledger (v18)

Screenshot the payment app's "done" screen → Share → **Log in Ledger**. The quick add pop-up opens over the
screenshot, reads it, and fills in amount, who, date and the UPI reference. You pick a category and Save.

- **Two layers.** `ui/scan/ScreenshotReader` runs ML Kit text recognition (on the phone, through Play services,
  so the APK only grows by a few hundred KB and the image never leaves the phone). core's `scan/PaymentScreen`
  turns the lines into a `ScannedPayment` with plain rules: the tallest ₹ number is the amount; the text after
  "Paid to / Sent to / To / Received from" is who; keywords give the date, UPI ref, app and status.
  `PaymentScreenTest` pins one case per app layout (Google Pay, PhonePe, Paytm, received, failed…).
- **The UPI reference** (12 digits, the same in every app and on your bank statement) goes into the comment as
  "UPI ref …". Sharing the same screenshot twice shows "already logged".
- **Warnings** show in the pop-up's note: failed or pending payments, a duplicate, or a payee who's in your People
  (it might have been a loan).
- **Manifest:** an `ACTION_SEND` `image/*` filter on `QuickAddActivity`, and the ML Kit `DEPENDENCIES=ocr`
  meta-data so Play services downloads the model at install, not at the first share.

### Concepts to look up
- **Share targets**: `Intent.ACTION_SEND`, `EXTRA_STREAM`, and the temporary read permission that comes with a shared URI.
- **ML Kit bundled vs unbundled** models: APK size against first-run availability.

## Screen scan (v20)

Set Settings › Side button › **Scan screen** in Ledger (or use the "Scan screen" app shortcut, which RegiStar's
back tap can open). On a payment's done screen, double-press the side button → Android asks "Start recording or
casting with Ledger?" → **Start** → the quick add pop-up opens, filled in from what was on screen.

- **Why Android asks every time:** an app can only see another app's screen as the phone's assistant, with
  accessibility access, or through screen capture (`MediaProjection`). Ledger uses screen capture, so Gemini stays
  your assistant and nothing is ever captured without that tap.
- `ScreenScanActivity` is an invisible window (`Theme.Ledger.Invisible`) that asks for the capture.
  `createConfigForDefaultDisplay()` (Android 14+) skips the "one app or entire screen?" step.
- `ScreenCaptureService` is a foreground service of type `mediaProjection` (required before Android hands over the
  capture). It mirrors the screen into an `ImageReader`, waits 0.7 s for the consent box to fade, keeps the newest
  frame, stops, and saves a PNG in Ledger's cache.
- The activity is still in front, which is what allows it to open the pop-up (Android blocks a background service
  from opening screens). The pop-up reads the file with the same `ScreenshotReader` as a shared screenshot, then deletes it.
- Screens that block screenshots (`FLAG_SECURE`) come out black; the pop-up then says so and suggests sharing a screenshot.

## Split one payment across categories (v21 / web v23)

One Blinkit order: ₹350 Cigarettes + ₹150 Munchies. It stays **one entry** (one row, one UPI reference, one card
charge), with `parts: [{amount, cat}, …]` that add up to the amount.

- **Counting**: anything that adds money up by category asks `costByCategory()` (web `costByCat`), which gives
  each part its proportional slice of what the bill counts as. Money back or friends' paybacks shrink every part
  the same way. Totals never double count. Pinned by `PartsTest` and a spec case in `remote-entries.json`.
- **Compatibility**: `category` / `cat` keeps the biggest part's category, so an older app still shows the entry,
  just under one category. Room v3 adds a `parts` column; `CURRENT_FORMAT = 3` re-downloads once to pick up parts
  the web saved first.
- **Privacy**: any private part hides the whole payment.
- **Rename / delete a category** updates the parts too (web).
- **Form**: "Split ›" next to Category. Part one starts with the category you had. The chips set whichever part
  is picked. One amount can stay blank and takes the rest. Save checks that the parts add up.

Next ideas: say "blinkit 500 cigarettes 350 munchies 150"; pick a second chip in the quick add pop-up to split;
share only some parts with friends.
