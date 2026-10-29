package com.efremandrei.statusupdater;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

public final class StatusWidget extends AppWidgetProvider {
    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) update(context, manager, id);
    }
    public static void refresh(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        for (int id : manager.getAppWidgetIds(new ComponentName(context, StatusWidget.class))) update(context, manager, id);
    }
    private static void update(Context context, AppWidgetManager manager, int id) {
        SettingsStore store = new SettingsStore(context);
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.status_widget);
        PendingIntent edit = PendingIntent.getActivity(context, 0, new Intent(context, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        boolean configured = store.configured();
        views.setTextViewText(R.id.widget_message, configured ? store.text() : "Your text, one tap away");
        views.setTextViewText(R.id.widget_status, configured
                ? ("ready".equals(store.kind()) ? MatrixConfig.durationLabel(store.minutes()) + " · " + store.address().replaceFirst("^https?://", "") : store.status())
                : "Set up your IP, text, and duration");
        views.setTextViewText(R.id.widget_send, configured ? ("busy".equals(store.kind()) ? "SENDING…" : "SHOW TEXT  ↗") : "SET UP  ↗");
        views.setTextColor(R.id.widget_status, android.graphics.Color.parseColor("error".equals(store.kind()) ? "#FFA6A0" : "#B5C5CA"));
        views.setOnClickPendingIntent(R.id.widget_edit, edit);
        views.setOnClickPendingIntent(R.id.widget_root, edit);
        PendingIntent send = PendingIntent.getForegroundService(context, 1,
                new Intent(context, SendService.class).setAction(SendService.SEND), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_send, configured ? send : edit);
        manager.updateAppWidget(id, views);
    }
}
