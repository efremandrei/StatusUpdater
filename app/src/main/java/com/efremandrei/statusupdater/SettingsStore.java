package com.efremandrei.statusupdater;

import android.content.Context;
import android.content.SharedPreferences;

public final class SettingsStore {
    private final SharedPreferences prefs;
    private final Context context;
    public SettingsStore(Context context) { this.context = context.getApplicationContext(); prefs = context.getSharedPreferences("matrix_settings", Context.MODE_PRIVATE); }
    public String address() { return prefs.getString("address", ""); }
    public String text() { return prefs.getString("text", ""); }
    public int minutes() { return prefs.getInt("minutes", MatrixConfig.DEFAULT_MINUTES); }
    public String color() { return readColor("color"); }
    private String readColor(String key) {
        try { return MatrixConfig.normalizeColor(prefs.getString(key, MatrixConfig.DEFAULT_COLOR)); }
        catch (MatrixConfig.ValidationException e) { return MatrixConfig.DEFAULT_COLOR; }
    }
    public boolean light() { return prefs.getBoolean("light", false); }
    public void setLight(boolean light) { prefs.edit().putBoolean("light", light).apply(); }
    public void save(MatrixConfig config) {
        if (!prefs.edit().putString("address", config.address).putString("text", config.text)
                .putInt("minutes", config.minutes).putString("color", config.color).putString("status", "Saved · ready to show")
                .putString("kind", "ready").commit()) throw new IllegalStateException("Could not save settings. Try again.");
    }
    public MatrixConfig config() { return MatrixConfig.create(address(), text(), Integer.toString(minutes()), color()); }
    public boolean configured() { try { config(); return true; } catch (IllegalArgumentException e) { return false; } }
    private int bootCount() { return android.provider.Settings.Global.getInt(context.getContentResolver(), android.provider.Settings.Global.BOOT_COUNT, -1); }
    public long remainingMillis() {
        long end = prefs.getLong("activeUntil", 0);
        if (end == 0) return 0;
        long elapsedEnd = prefs.getLong("activeElapsedUntil", 0);
        long remaining = elapsedEnd > 0 && prefs.getInt("activeBoot", -2) == bootCount()
                ? elapsedEnd - android.os.SystemClock.elapsedRealtime() : end - System.currentTimeMillis();
        return Math.max(0, remaining);
    }
    public boolean active() { return remainingMillis() > 0; }
    public String activeAddress() { return prefs.getString("activeAddress", ""); }
    public String activeText() { return prefs.getString("activeText", ""); }
    public String activeColor() { return readColor("activeColor"); }
    public long activeUntil() { return System.currentTimeMillis() + remainingMillis(); }
    @android.annotation.SuppressLint("ApplySharedPref")
    public void startSession(MatrixConfig config) {
        long duration = config.seconds() * 1000L;
        prefs.edit().putString("activeAddress", config.address).putString("activeText", config.text)
                .putString("activeColor", config.color)
                .putLong("activeUntil", System.currentTimeMillis() + duration)
                .putLong("activeElapsedUntil", android.os.SystemClock.elapsedRealtime() + duration)
                .putInt("activeBoot", bootCount()).commit();
    }
    @android.annotation.SuppressLint("ApplySharedPref")
    public void endSession() {
        prefs.edit().remove("activeAddress").remove("activeText").remove("activeColor").remove("activeUntil")
                .remove("activeElapsedUntil").remove("activeBoot").commit();
    }
    public void expireIfNeeded() {
        if (prefs.getLong("activeUntil", 0) != 0 && !active()) {
            endSession();
            if (!"busy".equals(kind())) status("Text time finished · ready to show", "ready");
        }
    }
    private boolean interrupted() { return "busy".equals(prefs.getString("kind", "ready")) && System.currentTimeMillis() - prefs.getLong("statusAt", 0) > 45000; }
    public String status() { return interrupted() ? "Previous request interrupted · tap Show text to retry" : prefs.getString("status", "Save your settings to get started."); }
    public String kind() { return interrupted() ? "error" : prefs.getString("kind", "ready"); }
    @android.annotation.SuppressLint("ApplySharedPref") // Finish durable status before stopping the short-lived service.
    public void status(String message, String kind) { prefs.edit().putString("status", message).putString("kind", kind).putLong("statusAt", System.currentTimeMillis()).commit(); }
}
