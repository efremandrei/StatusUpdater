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
        updateWidgets(context, manager, ids, false);
    }
    static void updateWidgets(Context context, AppWidgetManager manager, int[] ids, boolean compact) {
        new SettingsStore(context).expireIfNeeded();
        SessionTimer.reschedule(context);
        for (int id : ids) update(context, manager, id, compact);
    }
    public static void refresh(Context context) {
        new SettingsStore(context).expireIfNeeded();
        SessionTimer.reschedule(context);
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        for (int id : manager.getAppWidgetIds(new ComponentName(context, StatusWidget.class))) update(context, manager, id, false);
        for (int id : manager.getAppWidgetIds(new ComponentName(context, CompactStatusWidget.class))) update(context, manager, id, true);
    }
    private static void update(Context context, AppWidgetManager manager, int id, boolean compact) {
        SettingsStore store = new SettingsStore(context);
        RemoteViews views = new RemoteViews(context.getPackageName(), compact ? R.layout.status_widget_compact : R.layout.status_widget);
        PendingIntent edit = PendingIntent.getActivity(context, 0, new Intent(context, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        boolean configured = store.configured();
        boolean active = store.active();
        boolean busy = "busy".equals(store.kind());
        String description = busy ? (active ? "Stopping text" : "Sending saved text") : active
                ? "Stop text and return to clock" : configured ? "Show saved text on the matrix" : "Open Status Updater to set up your matrix";
        int background = active ? R.drawable.widget_stop_button : R.drawable.widget_button;
        if (compact) {
            views.setTextViewText(R.id.widget_send, busy ? "WAIT" : active ? "STOP" : configured ? "SHOW" : "SET UP");
            views.setImageViewResource(R.id.widget_symbol, busy ? R.drawable.widget_wait : active ? R.drawable.widget_stop : R.drawable.widget_play);
            views.setInt(R.id.widget_root, "setBackgroundResource", background);
            views.setBoolean(R.id.widget_root, "setEnabled", !busy);
            views.setContentDescription(R.id.widget_root, description);
        } else {
            views.setTextViewText(R.id.widget_message, active ? store.activeText() : configured ? store.text() : "Your text, one tap away");
            String end = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(new java.util.Date(store.activeUntil()));
            views.setTextViewText(R.id.widget_status, active && !"error".equals(store.kind()) && !busy ? "Text on · until " + end : configured
                    ? ("ready".equals(store.kind()) ? MatrixConfig.durationLabel(store.minutes()) + " · " + store.address().replaceFirst("^https?://", "") : store.status())
                    : "Set up your IP, text, and duration");
            views.setTextViewText(R.id.widget_send, busy ? (active ? "STOPPING…" : "SENDING…") : active ? "STOP TEXT  ■" : configured ? "SHOW TEXT  ↗" : "SET UP  ↗");
            views.setInt(R.id.widget_send, "setBackgroundResource", background);
            views.setTextColor(R.id.widget_status, android.graphics.Color.parseColor("error".equals(store.kind()) ? "#FFA6A0" : "#B5C5CA"));
            views.setOnClickPendingIntent(R.id.widget_edit, edit);
            views.setOnClickPendingIntent(R.id.widget_root, edit);
        }
        views.setContentDescription(R.id.widget_send, description);
        views.setBoolean(R.id.widget_send, "setEnabled", !busy);
        // Keep the action aligned with the rendered label. A delayed expiry refresh
        // must never let a visible Stop button unexpectedly start another message.
        PendingIntent send = PendingIntent.getForegroundService(context, active ? 2 : 1,
                new Intent(context, SendService.class).setAction(active ? SendService.STOP : SendService.SEND)
                        .putExtra("address", active ? store.activeAddress() : ""), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_send, active || configured ? send : edit);
        if (compact) views.setOnClickPendingIntent(R.id.widget_root, active || configured ? send : edit);
        manager.updateAppWidget(id, views);
    }
}
