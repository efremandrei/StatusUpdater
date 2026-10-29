package com.efremandrei.statusupdater;

import android.app.Activity;
import android.app.AlertDialog;
import android.appwidget.AppWidgetManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.splashscreen.SplashScreen;
import androidx.core.content.ContextCompat;

public final class MainActivity extends Activity {
    private SettingsStore store;
    private EditText address, message, minutes;
    private TextView status, counter, durationHint;
    private MatrixPreview preview;
    private boolean light;
    private int bg, card, ink, muted, border, accent, field;
    private final BroadcastReceiver updates = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { updateStatus(); }
    };

    @Override public void onCreate(Bundle state) {
        store = new SettingsStore(this);
        light = store.light();
        setTheme(light ? R.style.Theme_StatusUpdater_Starting_Light : R.style.Theme_StatusUpdater_Starting);
        SplashScreen.installSplashScreen(this);
        super.onCreate(state);
        bg = c(light ? "#F2F5F3" : "#101418"); card = c(light ? "#FFFFFF" : "#192126");
        ink = c(light ? "#142722" : "#EEF5F3"); muted = c(light ? "#53665F" : "#A6B9BD");
        border = c(light ? "#D6E0DA" : "#34434A"); accent = c(light ? "#137E60" : "#63E5BA");
        field = c(light ? "#F6F9F7" : "#11191E");
        getWindow().setStatusBarColor(bg); getWindow().setNavigationBarColor(bg);
        getWindow().getDecorView().setSystemUiVisibility(light ? View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR : 0);

        LinearLayout root = column(); root.setBackgroundColor(bg);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            } else v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        setContentView(root);

        LinearLayout header = row(); header.setPadding(dp(20), dp(8), dp(14), dp(4));
        ImageView logo = new ImageView(this); logo.setImageResource(R.drawable.ic_brand); logo.setContentDescription("Status Updater logo");
        header.addView(logo, new LinearLayout.LayoutParams(dp(44), dp(44)));
        TextView brand = label("STATUS\nUPDATER", 12, ink, true); brand.setLetterSpacing(0.13f);
        header.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
        header.addView(iconButton("☾", "Use dark theme", () -> theme(false)));
        header.addView(iconButton("☀", "Use light theme", () -> theme(true)));
        header.addView(iconButton("ⓘ", "About Status Updater", this::about));
        root.addView(header);

        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setClipToPadding(false);
        LinearLayout content = column(); content.setPadding(dp(22), dp(10), dp(22), dp(24));
        scroll.addView(content); root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        TextView eyebrow = label("A LITTLE DISPLAY. A CLEAR MESSAGE.", 10, accent, true); eyebrow.setLetterSpacing(0.11f); content.addView(eyebrow);
        TextView title = label("Say it on the matrix.", 29, ink, true); add(content, title, 6);
        TextView subtitle = label("Your words, one tap away.", 14, muted, false); add(content, subtitle, 5);

        LinearLayout display = column(); display.setPadding(dp(12), dp(10), dp(12), dp(10)); display.setBackground(shape(c("#0A1012"), c("#34434A"), 18));
        LinearLayout displayTop = row();
        TextView previewLabel = label("●  TEXT PREVIEW", 10, c("#FFBD66"), true); previewLabel.setLetterSpacing(0.08f);
        displayTop.addView(previewLabel, new LinearLayout.LayoutParams(0, -2, 1)); displayTop.addView(label("32 × 8", 10, c("#91A4AA"), false)); display.addView(displayTop);
        preview = new MatrixPreview(this); display.addView(preview, new LinearLayout.LayoutParams(-1, dp(94)));
        TextView previewNote = label("Approximation · actual font depends on firmware", 10, c("#91A4AA"), false); previewNote.setGravity(Gravity.CENTER); display.addView(previewNote);
        add(content, display, 20);

        LinearLayout connection = section("01", "Your matrix");
        address = input("192.168.1.50", "Matrix address", R.id.address);
        address.setSingleLine(true); address.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI); address.setText(store.address());
        add(connection, address, 12);
        LinearLayout connectionBottom = row();
        TextView networkHint = label("AWTRIX 3 / Light · same Wi-Fi", 11, muted, false);
        connectionBottom.addView(networkHint, new LinearLayout.LayoutParams(0, -2, 1));
        Button test = button("Test connection", false, this::testConnection); test.setTextSize(11); connectionBottom.addView(test, new LinearLayout.LayoutParams(-2, dp(44)));
        add(connection, connectionBottom, 4); add(content, connection, 16);

        LinearLayout composition = section("02", "Your message");
        message = input("e.g. In a meeting — back at 3", "Message text", R.id.message);
        message.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        message.setMinLines(2); message.setMaxLines(5); message.setGravity(Gravity.TOP | Gravity.START); message.setFilters(new InputFilter[]{new InputFilter.LengthFilter(500)}); message.setText(store.text());
        add(composition, message, 12);
        LinearLayout messageBottom = row(); messageBottom.addView(label("Long text scrolls automatically", 11, muted, false), new LinearLayout.LayoutParams(0, -2, 1));
        counter = label("", 11, muted, false); messageBottom.addView(counter); add(composition, messageBottom, 8); add(content, composition, 12);

        LinearLayout time = section("03", "On-screen time");
        LinearLayout durationRow = row();
        minutes = input("60", "Display duration in minutes", R.id.minutes); minutes.setInputType(InputType.TYPE_CLASS_NUMBER); minutes.setSingleLine(true); minutes.setFilters(new InputFilter[]{new InputFilter.LengthFilter(4)}); minutes.setText(Integer.toString(store.minutes()));
        durationRow.addView(minutes, new LinearLayout.LayoutParams(dp(96), dp(52)));
        TextView unit = label("minutes", 15, ink, false); LinearLayout.LayoutParams unitParams = new LinearLayout.LayoutParams(-2, -2); unitParams.setMarginStart(dp(12)); durationRow.addView(unit, unitParams);
        durationHint = label("", 11, muted, false); durationHint.setGravity(Gravity.END); durationRow.addView(durationHint, new LinearLayout.LayoutParams(0, -2, 1)); add(time, durationRow, 12);
        LinearLayout presets = row();
        int[] values = {15, 30, 60, 120}; String[] names = {"15 min", "30 min", "1 hour", "2 hours"};
        for (int i = 0; i < values.length; i++) {
            final int value = values[i]; Button preset = button(names[i], false, () -> minutes.setText(Integer.toString(value))); preset.setTextSize(11);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(44), 1); if (i > 0) p.setMarginStart(dp(6)); presets.addView(preset, p);
        }
        add(time, presets, 8); add(time, label("Default: 1 hour. Returns to your normal display when time is up.", 11, muted, false), 8); add(content, time, 12);

        status = label("", 12, muted, false); status.setPadding(dp(14), dp(12), dp(14), dp(12)); status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE); add(content, status, 14);
        Button show = button("Show text on matrix  ↗", true, this::send); add(content, show, 12, 54);
        LinearLayout secondary = row(); secondary.addView(button("Save settings", false, this::save), new LinearLayout.LayoutParams(0, dp(48), 1));
        LinearLayout.LayoutParams clearParams = new LinearLayout.LayoutParams(0, dp(48), 1); clearParams.setMarginStart(dp(10)); secondary.addView(button("Clear display", false, this::clear), clearParams); add(content, secondary, 8);

        LinearLayout widget = section("↗", "Make it one tap");
        add(widget, label("Save your settings, then add the home-screen widget. Its Show text button sends without opening the app.", 12, muted, false), 10);
        add(widget, button("Add home-screen widget", false, this::pinWidget), 10, 46); add(content, widget, 20);
        TextView footer = label("LOCAL WI-FI  ·  NO ACCOUNT NEEDED", 10, muted, false); footer.setLetterSpacing(0.06f); footer.setGravity(Gravity.CENTER); add(content, footer, 20);

        if (state != null) {
            address.setText(state.getString("draftAddress", store.address()));
            message.setText(state.getString("draftMessage", store.text()));
            minutes.setText(state.getString("draftMinutes", Integer.toString(store.minutes())));
        }
        message.addTextChangedListener(watcher(this::previewChanged)); minutes.addTextChangedListener(watcher(this::durationChanged));
        previewChanged(); durationChanged(); updateStatus();
        root.requestApplyInsets();
    }

    private void previewChanged() { String text = message.getText().toString(); counter.setText(text.length() + " / 500"); preview.setMessage(text); }
    private void durationChanged() {
        try { int value = Integer.parseInt(minutes.getText().toString()); durationHint.setText(value >= 1 && value <= 1440 ? MatrixConfig.durationLabel(value) : "1–1440 min"); }
        catch (NumberFormatException e) { durationHint.setText("1–1440 min"); }
    }
    private TextWatcher watcher(Runnable action) { return new TextWatcher() {
        public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
        public void onTextChanged(CharSequence s, int start, int before, int count) { action.run(); }
        public void afterTextChanged(Editable e) { }
    }; }
    private MatrixConfig validate() { return MatrixConfig.create(address.getText().toString(), message.getText().toString(), minutes.getText().toString()); }
    private void validation(Exception e) {
        if (e instanceof MatrixConfig.ValidationException) {
            String fieldName = ((MatrixConfig.ValidationException) e).field;
            EditText target = "address".equals(fieldName) ? address : "message".equals(fieldName) ? message : minutes;
            target.setError(e.getMessage()); target.requestFocus();
        }
        status.setText(e.getMessage()); status.setTextColor(c(light ? "#AE302A" : "#FFA6A0"));
    }
    private boolean saveSettings(boolean toast) {
        try {
            MatrixConfig config = validate(); store.save(config); StatusWidget.refresh(this); updateStatus();
            hideKeyboard(); if (toast) Toast.makeText(this, "Settings saved. Your widget is ready.", Toast.LENGTH_SHORT).show(); return true;
        } catch (IllegalArgumentException | IllegalStateException e) { validation(e); return false; }
    }
    private void save() { saveSettings(true); }
    private void send() { if (saveSettings(false)) startRequest(SendService.SEND, null); }
    private void testConnection() {
        try { startRequest(SendService.TEST, MatrixConfig.normalizeAddress(address.getText().toString())); }
        catch (IllegalArgumentException e) { validation(e); }
    }
    private void clear() {
        try {
            String host = MatrixConfig.normalizeAddress(address.getText().toString());
            new AlertDialog.Builder(this).setTitle("Clear the current notification?")
                    .setMessage("This dismisses whichever notification is currently on " + host + ", including one sent by another app.")
                    .setNegativeButton("Cancel", null).setPositiveButton("Clear display", (d, w) -> startRequest(SendService.CLEAR, host)).show();
        } catch (IllegalArgumentException e) { validation(e); }
    }
    private void startRequest(String action, String host) {
        try { hideKeyboard(); startForegroundService(new Intent(this, SendService.class).setAction(action).putExtra("address", host)); }
        catch (RuntimeException e) { store.status("Could not start the request. Open the app and try again.", "error"); updateStatus(); }
    }
    private void pinWidget() {
        if (!saveSettings(false)) return;
        AppWidgetManager manager = getSystemService(AppWidgetManager.class);
        if (manager.isRequestPinAppWidgetSupported()) manager.requestPinAppWidget(new ComponentName(this, StatusWidget.class), null, null);
        else new AlertDialog.Builder(this).setTitle("Add Status Updater")
                .setMessage("Long-press an empty space on your home screen, choose Widgets, then drag Status Updater onto the home screen.")
                .setPositiveButton("Got it", null).show();
    }
    private void updateStatus() {
        if (status == null) return;
        status.setText(store.status()); status.setTextColor("error".equals(store.kind()) ? c(light ? "#AE302A" : "#FFA6A0") : muted);
        status.setBackground(shape(field, border, 12));
    }
    private void theme(boolean light) { if (this.light != light) { store.setLight(light); recreate(); } }
    private void about() {
        TextView body = label("Status Updater\nVersion " + BuildConfig.VERSION_NAME + " · build " + BuildConfig.VERSION_CODE
                + "\n\nBy Andrei Efremuahkin\nandrei.efr@gmail.com\n\nhttps://github.com/efremandrei/StatusUpdater"
                + "\n\nFor Ulanzi matrices running AWTRIX 3 / AWTRIX Light. Other firmware is not supported."
                + "\n\nUse the same reachable local network as your matrix. Settings stay on this phone. HTTP sends text unencrypted over your local network."
                + "\n\nThe firmware handles message expiry. A new Show text request replaces the current notification and restarts the duration."
                + "\n\nPreview is approximate. Available characters, fonts, and scrolling depend on your firmware. No cloud account or analytics.", 14, ink, false);
        body.setPadding(dp(24), dp(10), dp(24), dp(16)); body.setTextIsSelectable(true); android.text.util.Linkify.addLinks(body, android.text.util.Linkify.WEB_URLS | android.text.util.Linkify.EMAIL_ADDRESSES);
        ScrollView scroll = new ScrollView(this); scroll.addView(body);
        new AlertDialog.Builder(this).setTitle("About").setView(scroll).setPositiveButton("Close", null).show();
    }
    private void hideKeyboard() { getSystemService(InputMethodManager.class).hideSoftInputFromWindow(address.getWindowToken(), 0); address.clearFocus(); message.clearFocus(); minutes.clearFocus(); }
    @Override protected void onResume() { super.onResume(); ContextCompat.registerReceiver(this, updates, new IntentFilter(SendService.UPDATED), ContextCompat.RECEIVER_NOT_EXPORTED); updateStatus(); }
    @Override protected void onPause() { unregisterReceiver(updates); super.onPause(); }
    @Override public void onSaveInstanceState(Bundle out) { out.putString("draftAddress", address.getText().toString()); out.putString("draftMessage", message.getText().toString()); out.putString("draftMinutes", minutes.getText().toString()); super.onSaveInstanceState(out); }

    private int c(String hex) { return Color.parseColor(hex); }
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private LinearLayout column() { LinearLayout view = new LinearLayout(this); view.setOrientation(LinearLayout.VERTICAL); return view; }
    private LinearLayout row() { LinearLayout view = new LinearLayout(this); view.setOrientation(LinearLayout.HORIZONTAL); view.setGravity(Gravity.CENTER_VERTICAL); return view; }
    private TextView label(String text, int size, int color, boolean bold) {
        TextView view = new TextView(this); view.setText(text); view.setTextSize(size); view.setTextColor(color); view.setIncludeFontPadding(false);
        view.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL)); view.setLineSpacing(dp(2), 1); return view;
    }
    private GradientDrawable shape(int fill, int outline, int radius) { GradientDrawable drawable = new GradientDrawable(); drawable.setColor(fill); drawable.setCornerRadius(dp(radius)); drawable.setStroke(dp(1), outline); return drawable; }
    private LinearLayout section(String number, String title) {
        LinearLayout box = column(); box.setPadding(dp(16), dp(15), dp(16), dp(14)); box.setBackground(shape(card, border, 16));
        LinearLayout heading = row(); TextView index = label(number, 11, accent, true); index.setTypeface(Typeface.MONOSPACE, Typeface.BOLD); heading.addView(index, new LinearLayout.LayoutParams(dp(30), -2)); heading.addView(label(title, 16, ink, true)); box.addView(heading); return box;
    }
    private EditText input(String hint, String description, int id) {
        EditText view = new EditText(this); view.setId(id); view.setHint(hint); view.setContentDescription(description);
        view.setTextSize(15); view.setTextColor(ink); view.setHintTextColor(muted); view.setPadding(dp(12), dp(12), dp(12), dp(12));
        view.setBackground(shape(field, border, 10)); view.setSelectAllOnFocus(false); view.setMinHeight(dp(52)); return view;
    }
    private Button button(String text, boolean primary, Runnable action) {
        Button view = new Button(this); view.setText(text); view.setAllCaps(false); view.setTextSize(13); view.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        view.setTextColor(primary ? c(light ? "#FFFFFF" : "#10251F") : accent); view.setMinHeight(0); view.setMinimumHeight(0); view.setMinWidth(0); view.setMinimumWidth(0);
        view.setPadding(dp(10), dp(4), dp(10), dp(4)); view.setBackgroundTintList(null); view.setBackground(shape(primary ? accent : field, primary ? accent : border, 12));
        android.graphics.drawable.RippleDrawable ripple = new android.graphics.drawable.RippleDrawable(ColorStateList.valueOf(c("#3063E5BA")), view.getBackground(), null); view.setBackground(ripple);
        view.setOnClickListener(v -> action.run()); return view;
    }
    private View iconButton(String text, String description, Runnable action) { Button view = button(text, false, action); view.setTextSize(22); view.setContentDescription(description); view.setBackgroundColor(Color.TRANSPARENT); view.setLayoutParams(new LinearLayout.LayoutParams(dp(44), dp(48))); return view; }
    private void add(LinearLayout parent, View child, int top) { add(parent, child, top, -2); }
    private void add(LinearLayout parent, View child, int top, int height) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, height > 0 ? dp(height) : height); p.topMargin = dp(top); parent.addView(child, p); }
}
