package com.himalayasoni.ledger;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

public class ReminderReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        Reminders.ensureChannel(c);
        boolean allowed = Build.VERSION.SDK_INT < 33
                || c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        if (allowed) {
            NotificationCompat.Builder b = new NotificationCompat.Builder(c, Reminders.CHANNEL)
                    .setSmallIcon(R.drawable.ic_stat)
                    .setColor(c.getColor(R.color.accent))
                    .setContentTitle("Anything to log today?")
                    .setContentText("Add it in two taps: amount, then category.")
                    .setContentIntent(Links.pending(c, 10, "quick=1"))
                    .addAction(0, "Quick add", Links.pending(c, 12, "quick=1"))
                    .addAction(0, "Say it", Links.pending(c, 11, "voice=1"))
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT);
            try { NotificationManagerCompat.from(c).notify(1, b.build()); } catch (SecurityException ignored) {}
        }
        if (!intent.getBooleanExtra("test", false)) Reminders.schedule(c);
    }
}
