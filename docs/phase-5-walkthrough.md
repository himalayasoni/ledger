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
