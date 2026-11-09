package com.himalayasoni.ledger.ui.quickadd

import android.app.PendingIntent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * The "Add to Ledger" button in Quick Settings (swipe down twice from the top of the screen,
 * then edit the tiles to add it). Tapping it closes the shade and opens the quick add pop-up.
 *
 * A TileService is a small service Android binds to while the shade is open; it only has to
 * say what the tile looks like and what a tap does.
 */
class QuickAddTile : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = Tile.STATE_INACTIVE // it's a button, not an on/off switch
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        // on the lock screen, ask to unlock first: entries shouldn't be added by anyone holding the phone
        if (isLocked) unlockAndRun { open() } else open()
    }

    private fun open() {
        val intent = QuickAdd.intent(this)
        if (Build.VERSION.SDK_INT >= 34) {
            // Android 14+ only accepts a PendingIntent here
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
