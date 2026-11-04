package com.himalayasoni.ledger;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/** Quick Settings tile: pull down the shade, tap, and Quick add opens. */
public class QuickTile extends TileService {
    @Override
    public void onStartListening() {
        Tile t = getQsTile();
        if (t != null) { t.setState(Tile.STATE_INACTIVE); t.updateTile(); }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onClick() {
        Intent i = Links.quick(this);
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 30, i,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        } else {
            startActivityAndCollapse(i);
        }
    }
}
