package com.efremandrei.statusupdater;

import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import java.io.IOException;
import static org.junit.Assert.*;

public class AwtrixClientTest {
    private MockWebServer server;
    private String base;

    @Before public void startServer() throws Exception {
        server = new MockWebServer();
        server.start();
        base = "http://127.0.0.1:" + server.getPort();
    }
    @After public void stopServer() throws Exception { server.shutdown(); }
    @Test public void sendsOneHourNotificationWithSafeReplacementAndNoHold() throws Exception {
        String text = "Meeting \"A\"\nשלום ☕";
        server.enqueue(new MockResponse().setBody("OK"));
        new AwtrixClient().send(MatrixConfig.create(base, text, "60"));
        RecordedRequest request = server.takeRequest();
        assertEquals("POST", request.getMethod()); assertEquals("/api/notify", request.getPath());
        JSONObject json = new JSONObject(request.getBody().readUtf8()); assertEquals(text, json.getString("text"));
        assertEquals(3600, json.getInt("duration")); assertEquals(-1, json.getInt("repeat"));
        assertFalse(json.getBoolean("hold")); assertFalse(json.getBoolean("stack")); assertTrue(json.getBoolean("wakeup"));
    }
    @Test public void configurableDurationUsesSeconds() throws Exception {
        server.enqueue(new MockResponse().setBody("OK"));
        new AwtrixClient().send(MatrixConfig.create(base, "Short", "15")); assertEquals(900, new JSONObject(server.takeRequest().getBody().readUtf8()).getInt("duration"));
    }
    @Test public void testsWithoutChangingDisplay() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"version\":\"0.98\",\"bat\":87}"));
        assertEquals("Connected · AWTRIX 0.98", new AwtrixClient().test(base));
        RecordedRequest request = server.takeRequest();
        assertEquals("GET", request.getMethod()); assertEquals("/api/stats", request.getPath()); assertEquals(0, request.getBodySize());
    }
    @Test public void clearTargetsDismissEndpoint() throws Exception {
        server.enqueue(new MockResponse().setBody("OK")); new AwtrixClient().clear(base);
        RecordedRequest request = server.takeRequest(); assertEquals("/api/notify/dismiss", request.getPath()); assertEquals("POST", request.getMethod());
    }
    @Test public void reportsHttpFailureAndRejectsRedirect() throws Exception {
        for (int error : new int[]{401, 403, 404, 500, 302}) { server.enqueue(new MockResponse().setResponseCode(error)); assertThrows(IOException.class, () -> new AwtrixClient().send(MatrixConfig.create(base, "Message", "60"))); }
    }
    @Test public void rejectsUnrelatedWebServer() throws Exception {
        server.enqueue(new MockResponse().setBody("<html>Router login</html>").setHeader("Content-Type", "text/html"));
        assertThrows(IOException.class, () -> new AwtrixClient().test(base));
        server.enqueue(new MockResponse().setBody("<html>Router login</html>").setHeader("Content-Type", "text/html"));
        assertThrows(IOException.class, () -> new AwtrixClient().send(MatrixConfig.create(base, "Message", "60")));
    }
    @Test public void rejectsMalformedStats() { server.enqueue(new MockResponse().setBody("{}")); assertThrows(IOException.class, () -> new AwtrixClient().test(base)); }
}
