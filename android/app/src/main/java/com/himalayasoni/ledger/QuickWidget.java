package com.himalayasoni.ledger;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.widget.RemoteViews;

/** Home-screen widget: Quick add and Say it, one tap each. */
public class QuickWidget extends AppWidgetProvider {
    @Override
    public void onUpdate(Context c, AppWidgetManager mgr, int[] ids) {
        for (int id : ids) {
            RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_quick);
            v.setOnClickPendingIntent(R.id.w_open, Links.pending(c, 22, "app=1"));
            v.setOnClickPendingIntent(R.id.w_say, Links.pending(c, 21, "voice=1"));
            v.setOnClickPendingIntent(R.id.w_quick, Links.quickPending(c, 20));
            mgr.updateAppWidget(id, v);
        }
    }
}
