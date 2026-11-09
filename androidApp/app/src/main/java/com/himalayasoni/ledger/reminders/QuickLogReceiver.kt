package com.himalayasoni.ledger.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import com.himalayasoni.ledger.core.domain.ReplyResult
import com.himalayasoni.ledger.data.QuickLogger
import com.himalayasoni.ledger.widget.LedgerWidgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Handles the daily reminder's buttons without opening the app:
 * - Reply: reads what you typed ("chai 20 cash"), saves it if it's complete, and turns the
 *   notification into "Logged ₹20 · Chai" with Undo. If something required is missing, the
 *   notification asks you to tap it, which opens the quick add pop-up already filled in.
 *   (Android doesn't let a receiver open a screen by itself, so that needs your tap.)
 * - Undo: deletes that entry again.
 *
 * goAsync() tells Android "I'm not done yet" so the work can run off the main thread; we must
 * call finish() on it within a few seconds.
 */
class QuickLogReceiver : BroadcastReceiver(), KoinComponent {
    private val logger: QuickLogger by inject()

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_REPLY -> reply(app, intent)
                    ACTION_UNDO -> intent.getStringExtra(EXTRA_ID)?.let { id ->
                        logger.undo(id)
                        Notifications.post(app, Notifications.ID_DAILY, Notifications.undone(app))
                        LedgerWidgets.refresh(app)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun reply(context: Context, intent: Intent) {
        val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(Notifications.KEY_REPLY)?.toString().orEmpty().trim()
        // posting again under the same id is what stops the reply box's spinner
        val next = when (val r = logger.logReply(text)) {
            is ReplyResult.Ready -> Notifications.logged(context, r.entry, logger.privateCategories())
            is ReplyResult.Incomplete -> Notifications.finish(context, text, r.missing)
            ReplyResult.Empty -> Notifications.daily(context)
        }
        Notifications.post(context, Notifications.ID_DAILY, next)
        LedgerWidgets.refresh(context)
    }

    companion object {
        const val ACTION_REPLY = "com.himalayasoni.ledger.REPLY"
        const val ACTION_UNDO = "com.himalayasoni.ledger.UNDO"
        const val EXTRA_ID = "id"
    }
}
