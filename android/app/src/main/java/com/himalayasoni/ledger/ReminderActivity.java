package com.himalayasoni.ledger;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;

/**
 * Invisible screen that Ledger's Settings opens with a link like
 * ledger://reminder?on=1&h=21&m=30 (or ?test=1). Saves the time, asks for
 * notification permission if needed, then closes and returns to Ledger.
 */
public class ReminderActivity extends Activity {
    private boolean on, test;
    private int h, m;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Uri u = getIntent().getData();
        on = u != null && "1".equals(u.getQueryParameter("on"));
        test = u != null && "1".equals(u.getQueryParameter("test"));
        h = clamp(u == null ? null : u.getQueryParameter("h"), 21, 23);
        m = clamp(u == null ? null : u.getQueryParameter("m"), 0, 59);
        if (!test) Reminders.prefs(this).edit().putBoolean("on", on).putInt("h", h).putInt("m", m).apply();

        boolean needAsk = (on || test) && Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED;
        if (needAsk) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 7);
        else done(true);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        done(results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED);
    }

    private void done(boolean allowed) {
        Reminders.schedule(this);
        String msg;
        if ((on || test) && !allowed) msg = "Allow notifications for Ledger in your phone's settings to get reminders";
        else if (test) {
            sendBroadcast(new Intent(this, ReminderReceiver.class).putExtra("test", true));
            msg = "Test reminder sent";
        } else if (on) msg = "Reminder set for " + Reminders.label(h, m) + " every day";
        else msg = "Daily reminder turned off";
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
        finish();
        overridePendingTransition(0, 0);
    }

    private static int clamp(String v, int def, int max) {
        try { int x = Integer.parseInt(v); return Math.max(0, Math.min(max, x)); } catch (Exception e) { return def; }
    }
}
