package com.efremandrei.statusupdater;

import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class AppFlowTest {
    private Context context;
    @Before public void reset() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.getSharedPreferences("matrix_settings", Context.MODE_PRIVATE).edit().clear().commit();
    }
    @Test public void defaultsToOneHourAndPreservesDraftAcrossThemeChange() {
        try (ActivityScenario<MainActivity> screen = ActivityScenario.launch(MainActivity.class)) {
            screen.onActivity(activity -> {
                assertEquals("60", ((EditText) activity.findViewById(R.id.minutes)).getText().toString());
                ((EditText) activity.findViewById(R.id.address)).setText("192.168.1.50");
                ((EditText) activity.findViewById(R.id.message)).setText("Draft meeting");
                ((EditText) activity.findViewById(R.id.minutes)).setText("30");
                findDescription(activity.getWindow().getDecorView(), "Use light theme").performClick();
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            screen.onActivity(activity -> {
                assertTrue(new SettingsStore(activity).light());
                assertEquals("Draft meeting", ((EditText) activity.findViewById(R.id.message)).getText().toString());
                assertEquals("30", ((EditText) activity.findViewById(R.id.minutes)).getText().toString());
            });
        }
    }
    @Test public void sendsAndPersistsSettingsAcrossActivityRestart() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.start(); server.enqueue(new MockResponse().setBody("OK"));
            try (ActivityScenario<MainActivity> screen = ActivityScenario.launch(MainActivity.class)) {
                screen.onActivity(activity -> {
                    ((EditText) activity.findViewById(R.id.address)).setText("127.0.0.1:" + server.getPort());
                    ((EditText) activity.findViewById(R.id.message)).setText("Meeting \"A\" שלום");
                    findText(activity.getWindow().getDecorView(), "Show text on matrix  ↗").performClick();
                });
                RecordedRequest request = server.takeRequest(8, TimeUnit.SECONDS); assertNotNull(request);
                assertEquals("/api/notify", request.getPath());
                JSONObject body = new JSONObject(request.getBody().readUtf8()); assertEquals(3600, body.getInt("duration")); assertEquals("Meeting \"A\" שלום", body.getString("text"));
                waitForStatus("success");
                screen.recreate(); screen.onActivity(activity -> assertEquals("Meeting \"A\" שלום", ((EditText) activity.findViewById(R.id.message)).getText().toString()));
            }
        }
    }
    @Test public void reportsHttpErrorWithoutLosingSavedMessage() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.start(); server.enqueue(new MockResponse().setResponseCode(404));
            try (ActivityScenario<MainActivity> screen = ActivityScenario.launch(MainActivity.class)) {
                screen.onActivity(activity -> {
                    ((EditText) activity.findViewById(R.id.address)).setText("127.0.0.1:" + server.getPort());
                    ((EditText) activity.findViewById(R.id.message)).setText("Keep this message");
                    findText(activity.getWindow().getDecorView(), "Show text on matrix  ↗").performClick();
                });
                assertNotNull(server.takeRequest(8, TimeUnit.SECONDS)); waitForStatus("error");
                assertTrue(new SettingsStore(context).status().contains("endpoint not found")); assertEquals("Keep this message", new SettingsStore(context).text());
            }
        }
    }
    @Test public void widgetButtonSendsSavedSettingsAndFinishesWhileOnHomeScreen() throws Exception {
        try (MockWebServer server = new MockWebServer(); ActivityScenario<MainActivity> screen = ActivityScenario.launch(MainActivity.class)) {
            server.start(); server.enqueue(new MockResponse().setBody("OK").setBodyDelay(500, TimeUnit.MILLISECONDS));
            server.enqueue(new MockResponse().setBody("OK")); server.enqueue(new MockResponse().setBody("OK"));
            new SettingsStore(context).save(MatrixConfig.create("127.0.0.1:" + server.getPort(), "From the widget", "30", "#6699FF"));
            AppWidgetHost host = new AppWidgetHost(context, 765);
            int widgetId = host.allocateAppWidgetId();
            InstrumentationRegistry.getInstrumentation().getUiAutomation().adoptShellPermissionIdentity("android.permission.BIND_APPWIDGET");
            try {
                AppWidgetManager manager = AppWidgetManager.getInstance(context);
                assertTrue(manager.bindAppWidgetIdIfAllowed(widgetId, new ComponentName(context, StatusWidget.class)));
                host.startListening();
                AtomicReference<AppWidgetHostView> view = new AtomicReference<>();
                screen.onActivity(activity -> {
                    AppWidgetHostView widget = host.createView(activity, widgetId, manager.getAppWidgetInfo(widgetId));
                    activity.setContentView(widget); view.set(widget); StatusWidget.refresh(activity);
                });
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                screen.onActivity(activity -> { assertNotNull(view.get().findViewById(R.id.widget_send)); view.get().findViewById(R.id.widget_send).performClick(); });
                UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
                RecordedRequest request = server.takeRequest(8, TimeUnit.SECONDS); assertNotNull(request);
                JSONObject body = new JSONObject(request.getBody().readUtf8()); assertEquals("From the widget", body.getString("text")); assertEquals(1800, body.getInt("duration"));
                assertEquals("#6699FF", body.getString("color"));
                waitForStatus("success");
                assertTrue(new SettingsStore(context).active());
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                screen.onActivity(activity -> {
                    assertEquals("STOP TEXT  ■", ((TextView) view.get().findViewById(R.id.widget_send)).getText().toString());
                    new SettingsStore(context).save(MatrixConfig.create("192.0.2.1", "Next message", "60"));
                    StatusWidget.refresh(activity);
                });
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                screen.onActivity(activity -> {
                    assertEquals("From the widget", ((TextView) view.get().findViewById(R.id.widget_message)).getText().toString());
                    assertEquals(android.graphics.Color.parseColor("#6699FF"), ((TextView) view.get().findViewById(R.id.widget_message)).getCurrentTextColor());
                    // Simulate an expiry whose visual refresh Android deferred.
                    // The still-visible Stop button must stop the original IP,
                    // not start the next saved message at its different IP.
                    context.getSharedPreferences("matrix_settings", Context.MODE_PRIVATE).edit()
                            .putLong("activeElapsedUntil", android.os.SystemClock.elapsedRealtime() - 1).commit();
                    view.get().findViewById(R.id.widget_send).performClick();
                });
                device.pressHome();
                RecordedRequest dismiss = server.takeRequest(8, TimeUnit.SECONDS); assertNotNull(dismiss);
                RecordedRequest clock = server.takeRequest(8, TimeUnit.SECONDS); assertNotNull(clock);
                assertEquals("/api/notify/dismiss", dismiss.getPath()); assertEquals("/api/switch", clock.getPath());
                assertEquals("Time", new JSONObject(clock.getBody().readUtf8()).getString("name"));
                waitForInactive(); assertEquals("success", new SettingsStore(context).kind());
                assertNotEquals(context.getPackageName(), device.getCurrentPackageName());
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                screen.onActivity(activity -> assertEquals("SHOW TEXT  ↗", ((TextView) view.get().findViewById(R.id.widget_send)).getText().toString()));
            } finally {
                host.stopListening(); host.deleteAppWidgetId(widgetId);
                InstrumentationRegistry.getInstrumentation().getUiAutomation().dropShellPermissionIdentity();
            }
        }
    }
    private void waitForStatus(String kind) {
        long end = SystemClock.uptimeMillis() + 8000;
        while (SystemClock.uptimeMillis() < end) { if (kind.equals(new SettingsStore(context).kind())) return; SystemClock.sleep(50); }
        fail("Expected " + kind + ": " + new SettingsStore(context).status());
    }
    private void waitForInactive() {
        long end = SystemClock.uptimeMillis() + 8000;
        while (SystemClock.uptimeMillis() < end) { if (!new SettingsStore(context).active() && !"busy".equals(new SettingsStore(context).kind())) return; SystemClock.sleep(50); }
        fail("Session is still active: " + new SettingsStore(context).status());
    }
    @Test public void failedStartNeverEnablesStopAndFailedStopKeepsRetryState() throws Exception {
        try (MockWebServer server = new MockWebServer(); ActivityScenario<MainActivity> screen = ActivityScenario.launch(MainActivity.class)) {
            server.start(); SettingsStore store = new SettingsStore(context);
            MatrixConfig config = MatrixConfig.create("127.0.0.1:" + server.getPort(), "Retry me", "60"); store.save(config);
            server.enqueue(new MockResponse().setResponseCode(500));
            screen.onActivity(activity -> activity.startForegroundService(new android.content.Intent(activity, SendService.class).setAction(SendService.SEND)));
            assertNotNull(server.takeRequest(8, TimeUnit.SECONDS)); waitForStatus("error"); assertFalse(store.active());
            store.startSession(config); server.enqueue(new MockResponse().setResponseCode(500));
            screen.onActivity(activity -> activity.startForegroundService(new android.content.Intent(activity, SendService.class).setAction(SendService.STOP)));
            RecordedRequest stop = server.takeRequest(8, TimeUnit.SECONDS); assertNotNull(stop); assertEquals("/api/notify/dismiss", stop.getPath());
            waitForStatus("error"); assertTrue(store.active()); assertEquals(config.address, store.activeAddress());
        }
    }
    @Test public void expiredSessionClearsWithoutNetworkAndUsesElapsedTime() {
        SettingsStore store = new SettingsStore(context); MatrixConfig config = MatrixConfig.create("192.0.2.1", "Timer", "60");
        store.save(config); store.startSession(config);
        context.getSharedPreferences("matrix_settings", Context.MODE_PRIVATE).edit().putLong("activeUntil", 1).commit();
        assertTrue("Wall clock changes must not end a running session", store.active());
        context.getSharedPreferences("matrix_settings", Context.MODE_PRIVATE).edit().putLong("activeElapsedUntil", android.os.SystemClock.elapsedRealtime() - 1).commit();
        assertFalse(store.active()); store.expireIfNeeded();
        assertEquals("", store.activeAddress()); assertEquals("ready", store.kind()); assertEquals("Timer", store.text());
    }
    @Test public void sessionSurvivesStoreReloadAndRebootFallback() {
        MatrixConfig config = MatrixConfig.create("192.0.2.1", "Keep running", "60");
        new SettingsStore(context).startSession(config);
        SettingsStore reopened = new SettingsStore(context);
        assertTrue(reopened.active()); assertEquals(config.address, reopened.activeAddress()); assertEquals(config.text, reopened.activeText());
        context.getSharedPreferences("matrix_settings", Context.MODE_PRIVATE).edit().putInt("activeBoot", -999)
                .putLong("activeElapsedUntil", 1).commit();
        assertTrue("After reboot use the saved wall-clock deadline", reopened.active());
        context.getSharedPreferences("matrix_settings", Context.MODE_PRIVATE).edit().putLong("activeUntil", 1).commit();
        reopened.expireIfNeeded(); assertFalse(reopened.active()); assertEquals("", reopened.activeAddress());
    }
    private View findText(View view, String text) {
        if (view instanceof TextView && text.contentEquals(((TextView) view).getText())) return view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) { View match = findText(((ViewGroup) view).getChildAt(i), text); if (match != null) return match; }
        return null;
    }
    private View findDescription(View view, String description) {
        if (description.contentEquals(view.getContentDescription() == null ? "" : view.getContentDescription())) return view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) { View match = findDescription(((ViewGroup) view).getChildAt(i), description); if (match != null) return match; }
        return null;
    }
}
