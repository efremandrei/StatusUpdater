package com.efremandrei.statusupdater;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.widget.Toast;
import java.text.DateFormat;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class SendService extends Service {
    public static final String SEND = "com.efremandrei.statusupdater.SEND";
    public static final String CLEAR = "com.efremandrei.statusupdater.CLEAR";
    public static final String STOP = "com.efremandrei.statusupdater.STOP";
    public static final String TEST = "com.efremandrei.statusupdater.TEST";
    public static final String UPDATED = "com.efremandrei.statusupdater.UPDATED";
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private PowerManager.WakeLock wakeLock;
    private boolean busy;

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (busy) return START_NOT_STICKY;
        busy = true;
        SettingsStore store = new SettingsStore(this);
        store.expireIfNeeded();
        String action = intent == null ? null : intent.getAction();
        String activeAddress = store.activeAddress();
        String stopAddress = !activeAddress.isEmpty() ? activeAddress : intent == null ? null : intent.getStringExtra("address");
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel("matrix_requests", "Matrix requests", NotificationManager.IMPORTANCE_LOW));
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification notification = new Notification.Builder(this, "matrix_requests")
                .setSmallIcon(R.drawable.ic_brand).setContentTitle("Status Updater")
                .setContentText("Contacting your matrix…").setContentIntent(open).setOngoing(true).build();
        if (Build.VERSION.SDK_INT >= 34) startForeground(101, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE);
        else startForeground(101, notification);
        wakeLock = getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "StatusUpdater:Request");
        wakeLock.acquire(45000);
        store.status(STOP.equals(action) ? "Stopping text…" : "Contacting matrix…", "busy");
        changed();
        executor.execute(() -> {
            String message;
            String kind;
            try {
                AwtrixClient client = new AwtrixClient();
                if (TEST.equals(action)) {
                    message = client.test(intent.getStringExtra("address"));
                } else if (CLEAR.equals(action)) {
                    client.clear(intent.getStringExtra("address"));
                    if (store.activeAddress().equals(intent.getStringExtra("address"))) store.endSession();
                    message = "Current notification dismissed · " + time();
                } else if (STOP.equals(action)) {
                    client.stop(stopAddress);
                    store.endSession();
                    message = "Text stopped · clock restored · " + time();
                } else if (SEND.equals(action)) {
                    MatrixConfig config = store.config();
                    client.send(config);
                    store.startSession(config);
                    message = "Sent " + time() + " · " + MatrixConfig.durationLabel(config.minutes);
                } else throw new IllegalArgumentException("Open the app and save your matrix settings first.");
                kind = "success";
            } catch (Exception e) {
                message = AwtrixClient.friendlyError(e);
                kind = "error";
            }
            store.status(message, kind);
            String result = message;
            main.post(() -> {
                changed();
                Toast.makeText(this, result, Toast.LENGTH_LONG).show();
                releaseWakeLock();
                stopForeground(STOP_FOREGROUND_REMOVE);
                stopSelf();
            });
        });
        return START_NOT_STICKY;
    }

    private String time() { return DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date()); }
    private void changed() {
        StatusWidget.refresh(this);
        sendBroadcast(new Intent(UPDATED).setPackage(getPackageName()));
    }
    private void releaseWakeLock() { if (wakeLock != null && wakeLock.isHeld()) wakeLock.release(); }
    @Override public void onDestroy() { releaseWakeLock(); executor.shutdownNow(); super.onDestroy(); }
    @Override public IBinder onBind(Intent intent) { return null; }
}
