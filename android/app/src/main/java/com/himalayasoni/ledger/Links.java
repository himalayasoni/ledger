package com.himalayasoni.ledger;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

/** Opens a screen of Ledger, e.g. "quick=1" for the Quick add popup. */
final class Links {
    private Links() {}

    static Intent open(Context c, String query) {
        Uri uri = Uri.parse(BuildConfig.SITE_URL + "?" + query + "&src=apk&apkv=" + BuildConfig.APK_VERSION);
        Intent i = new Intent(Intent.ACTION_VIEW, uri, c, Launcher.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return i;
    }

    /** The Quick add pop-up, which saves without opening Ledger. */
    static Intent quick(Context c) {
        Intent i = new Intent(c, QuickAddActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        return i;
    }

    static PendingIntent quickPending(Context c, int requestCode) {
        return PendingIntent.getActivity(c, requestCode, quick(c), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** Android's voice typing, then the words go to Ledger's entry form. */
    static PendingIntent voicePending(Context c, int requestCode) {
        Intent i = new Intent(c, VoiceActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        return PendingIntent.getActivity(c, requestCode, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static PendingIntent pending(Context c, int requestCode, String query) {
        return PendingIntent.getActivity(c, requestCode, open(c, query),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
