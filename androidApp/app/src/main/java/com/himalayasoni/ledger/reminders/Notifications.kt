package com.himalayasoni.ledger.reminders

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.himalayasoni.ledger.OpenRequest
import com.himalayasoni.ledger.R
import com.himalayasoni.ledger.core.domain.Nudge
import com.himalayasoni.ledger.core.domain.loggedLine
import com.himalayasoni.ledger.core.model.Entry
import com.himalayasoni.ledger.ui.quickadd.QuickAdd

/**
 * Every notification Ledger posts, in one place.
 *
 * Channels (Android 8+) are the groups you see under Ledger in Android's notification settings;
 * you can silence one without losing the others. Ledger has four.
 */
object Notifications {
    const val CH_DAILY = "daily"
    const val CH_REFUNDS = "refunds"
    const val CH_CARDS = "cards"
    const val CH_PEOPLE = "people"

    /** The daily reminder, and what it turns into after you reply ("Logged ₹20 · Chai"). */
    const val ID_DAILY = 1
    const val ID_PEOPLE = 2

    /** The text box's name inside the reply action's result. */
    const val KEY_REPLY = "reply"

    private const val ACCENT = 0xFFF0A20F.toInt()

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannels(listOf(
            NotificationChannel(CH_DAILY, "Daily reminder", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Once a day, only if you haven't logged anything. Reply to log straight from it." },
            NotificationChannel(CH_REFUNDS, "Refunds", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "A refund still hasn't arrived after a week." },
            NotificationChannel(CH_CARDS, "Card bills", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "A few days before a card bill is due." },
            NotificationChannel(CH_PEOPLE, "People", NotificationManager.IMPORTANCE_LOW)
                .apply { description = "A weekly summary of what people owe you." },
        ))
    }

    /** Android 13+ asks you before an app may post notifications; older versions allow it by default. */
    fun allowed(context: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    @android.annotation.SuppressLint("MissingPermission") // checked by allowed()
    fun post(context: Context, id: Int, notification: Notification) {
        if (allowed(context)) NotificationManagerCompat.from(context).notify(id, notification)
    }

    fun cancel(context: Context, id: Int) = NotificationManagerCompat.from(context).cancel(id)

    private fun base(context: Context, channel: String) = NotificationCompat.Builder(context, channel)
        .setSmallIcon(R.drawable.ic_stat)
        .setColor(ACCENT)
        .setAutoCancel(true)

    /** A ticket that sends a broadcast to [QuickLogReceiver] (for Reply and Undo). */
    private fun broadcast(context: Context, requestCode: Int, intent: Intent, mutable: Boolean = false): PendingIntent =
        PendingIntent.getBroadcast(
            context, requestCode, intent.setClass(context, QuickLogReceiver::class.java),
            // Reply must be MUTABLE: Android writes your typed text into the intent before sending it.
            // Everything else is IMMUTABLE, so nothing else can be changed.
            (if (mutable) PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_IMMUTABLE) or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    /** Opens the main app, optionally on an entry or a card. */
    private fun openApp(context: Context, requestCode: Int, open: OpenRequest? = null): PendingIntent =
        PendingIntent.getActivity(
            context, requestCode, OpenRequest.intent(context, open),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    /** "Anything to log today?" with Reply (type it right there) and Add (the quick add pop-up). */
    fun daily(context: Context): Notification {
        val input = RemoteInput.Builder(KEY_REPLY).setLabel("chai 20 cash").build()
        val reply = NotificationCompat.Action.Builder(
            R.drawable.ic_stat, "Reply",
            broadcast(context, 10, Intent(QuickLogReceiver.ACTION_REPLY), mutable = true),
        ).addRemoteInput(input)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .setShowsUserInterface(false)
            .setAllowGeneratedReplies(false)
            .build()
        val add = NotificationCompat.Action.Builder(R.drawable.ic_stat, "Add", QuickAdd.pendingIntent(context, 11)).build()
        return base(context, CH_DAILY)
            .setContentTitle("Anything to log today?")
            .setContentText("Reply with something like “chai 20 cash”.")
            .setContentIntent(QuickAdd.pendingIntent(context, 12))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .addAction(reply)
            .addAction(add)
            .build()
    }

    /** After a reply was saved: "Logged ₹20 · Chai", with Undo. Goes away by itself after a while. */
    fun logged(context: Context, entry: Entry, privateCategories: Set<String>): Notification {
        val undo = broadcast(context, 13, Intent(QuickLogReceiver.ACTION_UNDO).putExtra(QuickLogReceiver.EXTRA_ID, entry.id))
        return base(context, CH_DAILY)
            .setContentTitle(entry.loggedLine(privateCategories))
            .setContentText("Saved in Ledger.")
            .setContentIntent(openApp(context, 14))
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setTimeoutAfter(10 * 60_000L)
            .addAction(R.drawable.ic_stat, "Undo", undo)
            .build()
    }

    /** After Undo: a brief "Removed", then gone. */
    fun undone(context: Context): Notification = base(context, CH_DAILY)
        .setContentTitle("Removed. Nothing was logged.")
        .setSilent(true)
        .setTimeoutAfter(4_000L)
        .build()

    /** The reply was missing something ("who it was with"): tap to finish it in the pop-up, already filled in. */
    fun finish(context: Context, said: String, missing: String): Notification = base(context, CH_DAILY)
        .setContentTitle("Almost: add $missing")
        .setContentText("Tap to finish “$said”.")
        .setContentIntent(QuickAdd.pendingIntent(context, 15, said = said))
        .setSilent(true)
        .build()

    /** A refund, card bill or people reminder from the checks. Tapping opens the entry or the card. */
    fun nudge(context: Context, channel: String, nudge: Nudge, id: Int): Notification = base(context, channel)
        .setContentTitle(nudge.title)
        .setContentText(nudge.text)
        .setStyle(NotificationCompat.BigTextStyle().bigText(nudge.text))
        .setContentIntent(openApp(context, id, OpenRequest(entry = nudge.entryId, card = nudge.card)))
        .build()
}
