package com.efremandrei.statusupdater;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.View;

/** A local approximation only; never presented as the matrix's live screen. */
public final class MatrixPreview extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Bitmap pixels;
    private long start;
    private int textColor = Color.parseColor(MatrixConfig.DEFAULT_COLOR);
    public MatrixPreview(Context context) { super(context); setMessage(""); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES); }
    public void setTextColor(int color) { textColor = color; invalidate(); }
    public void setMessage(String message) {
        String display = message.trim().isEmpty() ? "HELLO" : message.replace('\n', ' ');
        Paint text = new Paint();
        text.setTypeface(Typeface.create("monospace", Typeface.BOLD));
        text.setTextSize(8);
        text.setColor(Color.WHITE);
        int width = Math.min(3000, Math.max(32, (int) text.measureText(display) + 8));
        if (pixels != null) pixels.recycle();
        pixels = Bitmap.createBitmap(width, 8, Bitmap.Config.ARGB_8888);
        new Canvas(pixels).drawText(display, width == 32 ? Math.max(0, (32 - text.measureText(display)) / 2) : 0, 7, text);
        start = SystemClock.uptimeMillis();
        setContentDescription("Approximate LED text preview: " + display);
        invalidate();
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cell = Math.min((getWidth() - 32f) / 32f, (getHeight() - 20f) / 8f);
        float left = (getWidth() - cell * 32) / 2, top = (getHeight() - cell * 8) / 2;
        int offset = pixels.getWidth() > 32 ? (int) ((SystemClock.uptimeMillis() - start) / 130) % (pixels.getWidth() + 16) : 0;
        for (int y = 0; y < 8; y++) for (int x = 0; x < 32; x++) {
            int source = (x + offset) % (pixels.getWidth() + 16);
            boolean on = source < pixels.getWidth() && Color.alpha(pixels.getPixel(source, y)) > 80;
            paint.setColor(on ? textColor : 0xFF242D2E);
            canvas.drawCircle(left + (x + 0.5f) * cell, top + (y + 0.5f) * cell, cell * 0.33f, paint);
        }
        if (pixels.getWidth() > 32 && isShown()) postInvalidateDelayed(100);
    }
}
