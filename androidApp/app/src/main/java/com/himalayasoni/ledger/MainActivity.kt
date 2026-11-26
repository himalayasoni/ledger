package com.himalayasoni.ledger

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.himalayasoni.ledger.ui.quickadd.QuickAdd

/**
 * What the app icon, the side button and every "open an app" setting start. It never shows
 * anything: it decides where to go, starts that, and closes in the same moment.
 *
 * Why it exists: Samsung's side button (double press) can only open an app, and it opens the app's
 * launcher entry, so the only way to get the quick add pop-up from it is for the launcher entry to
 * choose. It keeps the name MainActivity so the home-screen icon and the side-button choice you
 * already made keep pointing at the right thing.
 *
 * How it tells the two apart: a tap on the icon comes from the home screen app (the "referrer"),
 * and the home screen also passes where the icon was (sourceBounds, for the opening animation).
 * The side button is the phone's system, with neither. [LaunchPrefs] lets you change the behaviour,
 * and records who opened Ledger last so Settings can show it.
 *
 * Manifest: a see-through theme with no window (Theme.NoDisplay), an empty taskAffinity and
 * excludeFromRecents, so it runs in its own throwaway task each time. That matters: if it shared the
 * app's task, a second side-button press would just bring the app back and never reach this code.
 */
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val opener = referrer?.host ?: "unknown"
        val fromIcon = intent?.sourceBounds != null || opener == homeApp()
        LaunchPrefs.remember(this, opener, fromIcon)
        val mode = LaunchPrefs.mode(this)
        startActivity(
            when {
                mode == LaunchPrefs.Mode.QUICK || (mode == LaunchPrefs.Mode.SIDE && !fromIcon) -> QuickAdd.intent(this)
                mode == LaunchPrefs.Mode.SCAN && !fromIcon -> Intent(this, com.himalayasoni.ledger.ui.scan.ScreenScanActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                else -> appIntent(this)
            },
        )
        // no animation for this invisible step
        @Suppress("DEPRECATION") overridePendingTransition(0, 0)
        finishAndRemoveTask()
    }

    /** The phone's home screen app, e.g. com.sec.android.app.launcher on a Samsung. */
    private fun homeApp(): String? = packageManager.resolveActivity(
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), android.content.pm.PackageManager.MATCH_DEFAULT_ONLY,
    )?.activityInfo?.packageName

    companion object {
        /** The full app, brought forward as it was left (it's single-task, so never a second copy). */
        fun appIntent(context: Context): Intent = Intent(context, LedgerActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    }
}

/** What opening Ledger does, kept in plain SharedPreferences because [MainActivity] reads it before anything else starts. */
object LaunchPrefs {
    enum class Mode(val key: String) {
        /** Default: the icon opens the app; the side button (or anything else) opens quick add. */
        SIDE("side"),
        /** Always the full app. */
        APP("app"),
        /** Always quick add, even from the icon (the pop-up has a button to the full app). */
        QUICK("quick"),
        /** The side button reads the payment on screen (asks Android each time); the icon opens the app. */
        SCAN("scan"),
    }

    private fun prefs(c: Context) = c.getSharedPreferences("ledger", Context.MODE_PRIVATE)

    fun mode(c: Context): Mode = prefs(c).getString("launchMode", null).let { k -> Mode.entries.firstOrNull { it.key == k } ?: Mode.SIDE }
    fun setMode(c: Context, m: Mode) = prefs(c).edit().putString("launchMode", m.key).apply()

    /** Who opened Ledger last, and whether it counted as the icon: shown in Settings to check the side button. */
    fun remember(c: Context, opener: String, fromIcon: Boolean) =
        prefs(c).edit().putString("lastOpener", opener).putBoolean("lastFromIcon", fromIcon).apply()
    fun lastOpener(c: Context): Pair<String, Boolean>? =
        prefs(c).getString("lastOpener", null)?.let { it to prefs(c).getBoolean("lastFromIcon", true) }
}
