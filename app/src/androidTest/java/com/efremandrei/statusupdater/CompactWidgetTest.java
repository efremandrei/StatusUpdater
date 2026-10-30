package com.efremandrei.statusupdater;

import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
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
import java.io.File;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class CompactWidgetTest {
    private Context context;

    @Before public void reset() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.getSharedPreferences("matrix_settings", Context.MODE_PRIVATE).edit().clear().commit();
    }

    @Test public void compactWidgetFitsOneCellAndSharesStartStopWithFullWidget() throws Exception {
        try (MockWebServer server = new MockWebServer(); ActivityScenario<MainActivity> screen = ActivityScenario.launch(MainActivity.class)) {
            server.start();
            SettingsStore store = new SettingsStore(context);
            AppWidgetManager manager = AppWidgetManager.getInstance(context);
            AppWidgetHost host = new AppWidgetHost(context, 766);
            int compactId = host.allocateAppWidgetId(), fullId = host.allocateAppWidgetId();
            InstrumentationRegistry.getInstrumentation().getUiAutomation().adoptShellPermissionIdentity("android.permission.BIND_APPWIDGET");
            try {
                assertTrue(manager.bindAppWidgetIdIfAllowed(compactId, new ComponentName(context, CompactStatusWidget.class)));
                assertTrue(manager.bindAppWidgetIdIfAllowed(fullId, new ComponentName(context, StatusWidget.class)));
                AppWidgetProviderInfo info = manager.getAppWidgetInfo(compactId);
                assertEquals(1, info.targetCellWidth); assertEquals(1, info.targetCellHeight);
                assertEquals(AppWidgetProviderInfo.RESIZE_NONE, info.resizeMode);
                host.startListening();
                AtomicReference<AppWidgetHostView> compact = new AtomicReference<>(), full = new AtomicReference<>();
                screen.onActivity(activity -> {
                    LinearLayout container = new LinearLayout(activity);
                    container.setOrientation(LinearLayout.VERTICAL); container.setGravity(Gravity.CENTER);
                    container.setBackgroundColor(android.graphics.Color.rgb(11, 18, 22));
                    AppWidgetHostView small = host.createView(activity, compactId, info);
                    // Test the content at its 40dp fallback minimum, without host-added margins.
                    small.setPadding(0, 0, 0, 0);
                    container.addView(small, new LinearLayout.LayoutParams(dp(40), dp(40)));
                    AppWidgetHostView large = host.createView(activity, fullId, manager.getAppWidgetInfo(fullId));
                    container.addView(large, new LinearLayout.LayoutParams(dp(280), dp(180)));
                    activity.setContentView(container); compact.set(small); full.set(large);
                    StatusWidget.refresh(activity);
                });
                idle();
                screen.onActivity(activity -> {
                    assertLabel(compact.get(), "SET UP");
                    View root = compact.get().findViewById(R.id.widget_root);
                    assertTrue(root.getContentDescription().toString().contains("set up"));
                    TextView label = compact.get().findViewById(R.id.widget_send);
                    assertTrue("Label must fit inside the one-cell tile", label.getBottom() <= root.getHeight() - root.getPaddingBottom());
                    assertTrue("Setup label must not be clipped", label.getPaint().measureText("SET UP") <= label.getWidth());
                    store.save(MatrixConfig.create("127.0.0.1:" + server.getPort(), "Compact widget message", "30"));
                    StatusWidget.refresh(activity);
                });
                idle();
                screenshot("compact-ready.png");
                server.enqueue(new MockResponse().setBody("OK").setBodyDelay(1200, TimeUnit.MILLISECONDS));
                screen.onActivity(activity -> {
                    assertLabel(compact.get(), "SHOW");
                    compact.get().findViewById(R.id.widget_root).performClick();
                });
                RecordedRequest start = server.takeRequest(8, TimeUnit.SECONDS); assertNotNull(start);
                assertEquals("/api/notify", start.getPath());
                JSONObject body = new JSONObject(start.getBody().readUtf8());
                assertEquals("Compact widget message", body.getString("text")); assertEquals(1800, body.getInt("duration"));
                idle();
                screen.onActivity(activity -> {
                    assertLabel(compact.get(), "WAIT");
                    assertFalse(compact.get().findViewById(R.id.widget_root).isEnabled());
                    assertFalse(compact.get().findViewById(R.id.widget_send).isEnabled());
                });
                waitForKind("success"); idle();
                screen.onActivity(activity -> {
                    assertLabel(compact.get(), "STOP"); assertLabel(full.get(), "STOP TEXT  ■");
                    // Edits prepare the next send; both widgets must still stop the original matrix.
                    store.save(MatrixConfig.create("192.0.2.1", "Next message", "60"));
                    StatusWidget.refresh(activity);
                });
                idle(); screenshot("compact-active.png");
                server.enqueue(new MockResponse().setResponseCode(500));
                screen.onActivity(activity -> compact.get().findViewById(R.id.widget_root).performClick());
                assertEquals("/api/notify/dismiss", server.takeRequest(8, TimeUnit.SECONDS).getPath());
                waitForKind("error"); idle();
                screen.onActivity(activity -> {
                    assertLabel(compact.get(), "STOP"); assertLabel(full.get(), "STOP TEXT  ■");
                    assertTrue(compact.get().findViewById(R.id.widget_root).isEnabled());
                    assertTrue(store.active());
                    // The already-rendered Stop action must remain Stop if visual expiry is late.
                    context.getSharedPreferences("matrix_settings", Context.MODE_PRIVATE).edit()
                            .putLong("activeElapsedUntil", SystemClock.elapsedRealtime() - 1).commit();
                });
                server.enqueue(new MockResponse().setBody("OK")); server.enqueue(new MockResponse().setBody("OK"));
                screen.onActivity(activity -> compact.get().findViewById(R.id.widget_root).performClick());
                UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()); device.pressHome();
                RecordedRequest dismiss = server.takeRequest(8, TimeUnit.SECONDS), clock = server.takeRequest(8, TimeUnit.SECONDS);
                assertNotNull(dismiss); assertNotNull(clock);
                assertEquals("/api/notify/dismiss", dismiss.getPath()); assertEquals("/api/switch", clock.getPath());
                assertEquals("Time", new JSONObject(clock.getBody().readUtf8()).getString("name"));
                waitForKind("success"); idle();
                assertFalse(store.active()); assertNotEquals(context.getPackageName(), device.getCurrentPackageName());
                screen.onActivity(activity -> { assertLabel(compact.get(), "SHOW"); assertLabel(full.get(), "SHOW TEXT  ↗"); });
                assertEquals(4, server.getRequestCount());
            } finally {
                host.stopListening(); host.deleteAppWidgetId(compactId); host.deleteAppWidgetId(fullId);
                InstrumentationRegistry.getInstrumentation().getUiAutomation().dropShellPermissionIdentity();
            }
        }
    }

    private int dp(int value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }
    private void idle() { InstrumentationRegistry.getInstrumentation().waitForIdleSync(); }
    private void assertLabel(AppWidgetHostView widget, String expected) {
        TextView label = widget.findViewById(R.id.widget_send); assertNotNull(label); assertEquals(expected, label.getText().toString());
    }
    private void waitForKind(String kind) {
        long deadline = SystemClock.uptimeMillis() + 8000;
        while (SystemClock.uptimeMillis() < deadline) { if (kind.equals(new SettingsStore(context).kind())) return; SystemClock.sleep(50); }
        fail("Expected " + kind + ": " + new SettingsStore(context).status());
    }
    private void screenshot(String name) {
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).takeScreenshot(new File(context.getExternalFilesDir(null), name));
    }
}
