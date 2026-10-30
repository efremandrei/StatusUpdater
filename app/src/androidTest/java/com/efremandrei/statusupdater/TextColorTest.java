package com.efremandrei.statusupdater;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class TextColorTest {
    private Context context;
    private UiDevice device;
    @Before public void reset() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        context.getSharedPreferences("matrix_settings", Context.MODE_PRIVATE).edit().clear().commit();
    }

    @Test public void presetColorUpdatesPixelsAndDraftSurvivesThemeAndCancel() {
        try (ActivityScenario<MainActivity> screen = ActivityScenario.launch(MainActivity.class)) {
            openPicker(screen); device.findObject(By.desc("Cyan #46DFFF")).click(); positive();
            screen.onActivity(activity -> {
                assertColorLabel(activity, "#46DFFF");
                assertEquals("Draft color must not overwrite saved settings", MatrixConfig.DEFAULT_COLOR, new SettingsStore(activity).color());
                MatrixPreview preview = activity.findViewById(R.id.matrix_preview); preview.setMessage("HI");
                Bitmap image = Bitmap.createBitmap(preview.getWidth(), preview.getHeight(), Bitmap.Config.ARGB_8888);
                preview.draw(new Canvas(image)); boolean found = false;
                for (int y = 0; y < image.getHeight() && !found; y++) for (int x = 0; x < image.getWidth(); x++)
                    if (image.getPixel(x, y) == Color.parseColor("#46DFFF")) { found = true; break; }
                image.recycle(); assertTrue("LED preview must draw the chosen color", found);
                findDescription(activity.getWindow().getDecorView(), "Use light theme").performClick();
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            screen.onActivity(activity -> assertColorLabel(activity, "#46DFFF"));
            openPicker(screen); device.findObject(By.desc("Pink #FF80C8")).click();
            device.findObject(By.res("android", "button2")).click();
            screen.onActivity(activity -> assertColorLabel(activity, "#46DFFF"));
        }
    }

    @Test public void customColorValidatesPersistsAndReachesMatrixRequest() throws Exception {
        try (MockWebServer server = new MockWebServer(); ActivityScenario<MainActivity> screen = ActivityScenario.launch(MainActivity.class)) {
            server.start(); openPicker(screen);
            UiObject2 hex = device.findObject(By.res(context.getPackageName(), "color_hex")); hex.setText("#12");
            device.findObject(By.res("android", "button1")).click();
            assertTrue("Invalid color must leave the picker open", device.hasObject(By.res(context.getPackageName(), "color_hex")));
            device.findObject(By.res(context.getPackageName(), "color_hex")).setText("#12abef"); positive();
            server.enqueue(new MockResponse().setBody("OK"));
            screen.onActivity(activity -> {
                ((EditText) activity.findViewById(R.id.address)).setText("127.0.0.1:" + server.getPort());
                ((EditText) activity.findViewById(R.id.message)).setText("Custom color message");
                findText(activity.getWindow().getDecorView(), "Show text on matrix  ↗").performClick();
            });
            RecordedRequest request = server.takeRequest(8, TimeUnit.SECONDS); assertNotNull(request);
            assertEquals("#12ABEF", new JSONObject(request.getBody().readUtf8()).getString("color"));
            long deadline = SystemClock.uptimeMillis() + 8000;
            while (!"success".equals(new SettingsStore(context).kind()) && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(50);
            assertEquals("success", new SettingsStore(context).kind());
            assertEquals("#12ABEF", new SettingsStore(context).activeColor());
        }
        try (ActivityScenario<MainActivity> reopened = ActivityScenario.launch(MainActivity.class)) {
            reopened.onActivity(activity -> { assertColorLabel(activity, "#12ABEF"); assertEquals("#12ABEF", new SettingsStore(activity).config().color); });
        }
    }

    @Test public void legacyPreferencesKeepAmberAndNewSettingsDoNotRecolorRunningSession() {
        SettingsStore store = new SettingsStore(context);
        MatrixConfig legacy = MatrixConfig.create("192.0.2.1", "Existing session", "60");
        store.save(legacy); store.startSession(legacy);
        context.getSharedPreferences("matrix_settings", Context.MODE_PRIVATE).edit().remove("color").remove("activeColor").commit();
        assertEquals("#FFBD66", store.config().color); assertEquals("#FFBD66", store.activeColor());
        store.save(MatrixConfig.create("192.0.2.2", "Next message", "30", "#46DFFF"));
        SettingsStore reloaded = new SettingsStore(context);
        assertTrue(reloaded.active()); assertEquals(legacy.address, reloaded.activeAddress());
        assertEquals("#FFBD66", reloaded.activeColor()); assertEquals("#46DFFF", reloaded.config().color);
    }

    private void openPicker(ActivityScenario<MainActivity> screen) {
        screen.onActivity(activity -> activity.findViewById(R.id.text_color).performClick());
        assertTrue(device.wait(Until.hasObject(By.res(context.getPackageName(), "color_hex")), 3000));
    }
    private void positive() {
        device.findObject(By.res("android", "button1")).click();
        assertTrue(device.wait(Until.gone(By.res(context.getPackageName(), "color_hex")), 3000));
    }
    private void assertColorLabel(MainActivity activity, String color) {
        assertTrue(((TextView) activity.findViewById(R.id.text_color)).getText().toString().contains(color));
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
