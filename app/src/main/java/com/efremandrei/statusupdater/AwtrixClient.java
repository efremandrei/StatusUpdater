package com.efremandrei.statusupdater;

import org.json.JSONException;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** AWTRIX 3 / AWTRIX Light HTTP API. The firmware owns the expiry timer. */
public final class AwtrixClient {
    public static JSONObject payload(MatrixConfig config) throws JSONException {
        return new JSONObject().put("text", config.text).put("duration", config.seconds())
                .put("hold", false).put("repeat", -1).put("stack", false)
                .put("wakeup", true).put("textCase", 2).put("color", "#FFBD66");
    }

    public void send(MatrixConfig config) throws IOException {
        try { request(config.address, "/api/notify", payload(config).toString()); }
        catch (JSONException e) { throw new IOException("Could not prepare the message.", e); }
    }

    public void clear(String address) throws IOException {
        request(address, "/api/notify/dismiss", "{}");
    }

    public String test(String address) throws IOException {
        String response = request(address, "/api/stats", null);
        try {
            JSONObject json = new JSONObject(response);
            if (!json.has("version")) throw new JSONException("Missing version");
            return "Connected · AWTRIX " + json.optString("version", "3");
        } catch (JSONException e) {
            throw new IOException("This address did not return AWTRIX 3 statistics. Check the IP and firmware.");
        }
    }

    private String request(String address, String path, String body) throws IOException {
        String base = MatrixConfig.normalizeAddress(address);
        HttpURLConnection connection = (HttpURLConnection) new URL(base + path).openConnection();
        try {
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Accept", "application/json, text/plain");
            connection.setRequestProperty("User-Agent", "StatusUpdater/1.0.0");
            if (body != null) {
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                connection.setFixedLengthStreamingMode(bytes.length);
                try (OutputStream out = connection.getOutputStream()) { out.write(bytes); }
            }
            int code = connection.getResponseCode();
            if (code == 401 || code == 403) throw new IOException("Matrix access denied (HTTP " + code + "). Check firmware HTTP authentication settings.");
            if (code == 404) throw new IOException("AWTRIX endpoint not found. Check the matrix IP and AWTRIX 3 / Light firmware.");
            if (code < 200 || code >= 300) throw new IOException("Matrix returned HTTP " + code + ". Check its IP and connection.");
            String type = connection.getContentType();
            if (type != null && type.toLowerCase(java.util.Locale.ROOT).contains("text/html"))
                throw new IOException("The address returned a web page instead of the AWTRIX API. Check the IP.");
            try (InputStream input = connection.getInputStream(); ByteArrayOutputStream result = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[2048];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (result.size() + count > 65536) throw new IOException("Unexpectedly large response from the matrix.");
                    result.write(buffer, 0, count);
                }
                return result.toString(StandardCharsets.UTF_8.name());
            }
        } finally { connection.disconnect(); }
    }

    public static String friendlyError(Exception error) {
        if (error instanceof java.net.SocketTimeoutException) return "Matrix timed out. Check the IP and use the same Wi-Fi network.";
        if (error instanceof java.net.UnknownHostException) return "Matrix address not found. Check the IP or hostname.";
        if (error instanceof java.net.ConnectException) return "Could not connect. Check the IP, port, and matrix Wi-Fi connection.";
        if (error instanceof javax.net.ssl.SSLException) return "HTTPS connection failed. Most AWTRIX devices use http:// on your local network.";
        return error.getMessage() == null ? "Could not reach the matrix. Check your local network." : error.getMessage();
    }
}
