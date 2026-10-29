package com.efremandrei.statusupdater;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

/** Local widget refresh only. AWTRIX owns the real display expiry. */
public final class SessionTimer extends BroadcastReceiver {
    private static final String EXPIRED = "com.efremandrei.statusupdater.EXPIRED";
    public static void reschedule(Context context) {
        SettingsStore store = new SettingsStore(context);
        AlarmManager alarms = context.getSystemService(AlarmManager.class);
        PendingIntent pending = PendingIntent.getBroadcast(context, 22,
                new Intent(context, SessionTimer.class).setAction(EXPIRED), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        alarms.cancel(pending);
        if (store.active()) alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + store.remainingMillis(), pending);
    }
    @Override public void onReceive(Context context, Intent intent) {
        new SettingsStore(context).expireIfNeeded();
        StatusWidget.refresh(context);
        context.sendBroadcast(new Intent(SendService.UPDATED).setPackage(context.getPackageName()));
    }
}
