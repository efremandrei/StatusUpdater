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
        new SettingsStore(context).expireIfNeeded();
        SessionTimer.reschedule(context);
        for (int id : ids) update(context, manager, id);
    }
    public static void refresh(Context context) {
        new SettingsStore(context).expireIfNeeded();
        SessionTimer.reschedule(context);
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        for (int id : manager.getAppWidgetIds(new ComponentName(context, StatusWidget.class))) update(context, manager, id);
    }
    private static void update(Context context, AppWidgetManager manager, int id) {
        SettingsStore store = new SettingsStore(context);
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.status_widget);
        PendingIntent edit = PendingIntent.getActivity(context, 0, new Intent(context, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        boolean configured = store.configured();
        boolean active = store.active();
        boolean busy = "busy".equals(store.kind());
        views.setTextViewText(R.id.widget_message, active ? store.activeText() : configured ? store.text() : "Your text, one tap away");
        String end = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(new java.util.Date(store.activeUntil()));
        views.setTextViewText(R.id.widget_status, active && !"error".equals(store.kind()) && !busy ? "Text on · until " + end : configured
                ? ("ready".equals(store.kind()) ? MatrixConfig.durationLabel(store.minutes()) + " · " + store.address().replaceFirst("^https?://", "") : store.status())
                : "Set up your IP, text, and duration");
        views.setTextViewText(R.id.widget_send, busy ? (active ? "STOPPING…" : "SENDING…") : active ? "STOP TEXT  ■" : configured ? "SHOW TEXT  ↗" : "SET UP  ↗");
        views.setContentDescription(R.id.widget_send, active ? "Stop text and return to clock" : "Show saved text on the matrix");
        views.setInt(R.id.widget_send, "setBackgroundResource", active ? R.drawable.widget_stop_button : R.drawable.widget_button);
        views.setBoolean(R.id.widget_send, "setEnabled", !busy);
        views.setTextColor(R.id.widget_status, android.graphics.Color.parseColor("error".equals(store.kind()) ? "#FFA6A0" : "#B5C5CA"));
        views.setOnClickPendingIntent(R.id.widget_edit, edit);
        views.setOnClickPendingIntent(R.id.widget_root, edit);
        // Keep the action aligned with the rendered label. A delayed expiry refresh
        // must never let a visible Stop button unexpectedly start another message.
        PendingIntent send = PendingIntent.getForegroundService(context, active ? 2 : 1,
                new Intent(context, SendService.class).setAction(active ? SendService.STOP : SendService.SEND)
                        .putExtra("address", active ? store.activeAddress() : ""), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_send, active || configured ? send : edit);
        manager.updateAppWidget(id, views);
    }
}
