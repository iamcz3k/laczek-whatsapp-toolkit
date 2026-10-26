package com.laczek.whatsapptoolkit;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** Lightweight, local-only microphone level history for the live recording screen. */
public final class AudioLevelView extends View {
    private static final int BARS = 42;
    private final float[] levels = new float[BARS];
    private final Paint background = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint baselinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int cursor;

    public AudioLevelView(Context context) {
        super(context);
        background.setColor(0xFF15171B);
        barPaint.setColor(0xFFFF8A3D);
        barPaint.setStrokeCap(Paint.Cap.ROUND);
        baselinePaint.setColor(0xFF454A52);
        baselinePaint.setStrokeWidth(dp(1));
        setContentDescription("Live microphone waveform");
    }

    public void addLevel(int amplitude) {
        // MediaRecorder amplitudes are device-dependent; logarithmic scaling keeps quiet
        // signals visible without allowing a single peak to flatten the whole display.
        float normalized = amplitude <= 0 ? 0f : (float) (Math.log10(1 + amplitude) / 4.3);
        levels[cursor] = Math.max(0.035f, Math.min(1f, normalized));
        cursor = (cursor + 1) % BARS;
        postInvalidateOnAnimation();
    }

    public void clearLevels() {
        java.util.Arrays.fill(levels, 0f);
        cursor = 0;
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) return;
        float radius = dp(12);
        canvas.drawRoundRect(new RectF(0, 0, w, h), radius, radius, background);
        float center = h / 2f;
        canvas.drawLine(dp(12), center, w - dp(12), center, baselinePaint);
        float usable = Math.max(1, w - dp(24));
        float slot = usable / BARS;
        float stroke = Math.max(dp(2), slot * 0.48f);
        barPaint.setStrokeWidth(stroke);
        for (int i = 0; i < BARS; i++) {
            int index = (cursor + i) % BARS;
            float level = levels[index];
            if (level <= 0f) continue;
            float x = dp(12) + slot * (i + 0.5f);
            float half = Math.max(dp(2), level * (h * 0.42f));
            canvas.drawLine(x, center - half, x, center + half, barPaint);
        }
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
