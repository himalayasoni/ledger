package com.himalayasoni.ledger;

import android.net.Uri;

import com.google.androidbrowserhelper.trusted.LauncherActivity;

/** Opens Ledger, and hands over anything the pop-up saved while it wasn't connected. */
public class Launcher extends LauncherActivity {
    @Override
    protected Uri getLaunchingUrl() {
        Uri u = super.getLaunchingUrl();
        Cloud.flushAsync(this);
        String inbox = Cloud.takeInbox(this);
        return inbox == null ? u : u.buildUpon().appendQueryParameter("inbox", inbox).build();
    }
}
