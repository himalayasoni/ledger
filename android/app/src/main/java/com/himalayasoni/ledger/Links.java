package com.himalayasoni.ledger;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import com.google.androidbrowserhelper.trusted.LauncherActivity;

/** Opens a screen of Ledger, e.g. "quick=1" for the Quick add popup. */
final class Links {
    private Links() {}

    static Intent open(Context c, String query) {
        Uri uri = Uri.parse(BuildConfig.SITE_URL + "?" + query + "&src=apk&apkv=" + BuildConfig.APK_VERSION);
        Intent i = new Intent(Intent.ACTION_VIEW, uri, c, LauncherActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return i;
    }

    static PendingIntent pending(Context c, int requestCode, String query) {
        return PendingIntent.getActivity(c, requestCode, open(c, query),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
