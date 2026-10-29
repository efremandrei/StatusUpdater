package com.efremandrei.statusupdater;

import android.content.Context;
import android.content.SharedPreferences;

public final class SettingsStore {
    private final SharedPreferences prefs;
    public SettingsStore(Context context) { prefs = context.getSharedPreferences("matrix_settings", Context.MODE_PRIVATE); }
    public String address() { return prefs.getString("address", ""); }
    public String text() { return prefs.getString("text", ""); }
    public int minutes() { return prefs.getInt("minutes", MatrixConfig.DEFAULT_MINUTES); }
    public boolean light() { return prefs.getBoolean("light", false); }
    public void setLight(boolean light) { prefs.edit().putBoolean("light", light).apply(); }
    public void save(MatrixConfig config) {
        if (!prefs.edit().putString("address", config.address).putString("text", config.text)
                .putInt("minutes", config.minutes).putString("status", "Saved · ready to show")
                .putString("kind", "ready").commit()) throw new IllegalStateException("Could not save settings. Try again.");
    }
    public MatrixConfig config() { return MatrixConfig.create(address(), text(), Integer.toString(minutes())); }
    public boolean configured() { try { config(); return true; } catch (IllegalArgumentException e) { return false; } }
    private boolean interrupted() { return "busy".equals(prefs.getString("kind", "ready")) && System.currentTimeMillis() - prefs.getLong("statusAt", 0) > 20000; }
    public String status() { return interrupted() ? "Previous request interrupted · tap Show text to retry" : prefs.getString("status", "Save your settings to get started."); }
    public String kind() { return interrupted() ? "error" : prefs.getString("kind", "ready"); }
    @android.annotation.SuppressLint("ApplySharedPref") // Finish durable status before stopping the short-lived service.
    public void status(String message, String kind) { prefs.edit().putString("status", message).putString("kind", kind).putLong("statusAt", System.currentTimeMillis()).commit(); }
}
