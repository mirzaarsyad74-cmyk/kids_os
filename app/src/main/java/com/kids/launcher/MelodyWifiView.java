package com.kids.launcher;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.provider.Settings;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.Toast;

import androidx.annotation.Nullable;

/**
 * Animated Vector WiFi status indicator for Kids OS Launcher.
 * Features:
 * - Dynamic 4-tier signal arcs + base dot
 * - Continuous wave ripple animation scaling in speed with connection link speed (Mbps)
 * - Animated disconnected / WiFi OFF state with soft-pulsing diagonal slash
 * - Click to inspect WiFi SSID, signal, and link speed with direct settings shortcut
 */
public class MelodyWifiView extends View {

    private boolean isWifiEnabled = true;
    private boolean isConnected = false;
    private int signalLevel = 0; // 0 to 4
    private int linkSpeedMbps = 0;
    private String ssid = "";

    private Paint activeArcPaint;
    private Paint inactiveArcPaint;
    private Paint slashPaint;
    private Paint dotPaint;

    private final RectF arcRect1 = new RectF();
    private final RectF arcRect2 = new RectF();
    private final RectF arcRect3 = new RectF();

    private ValueAnimator waveAnimator;
    private float wavePhase = 0f; // 0.0 to 1.0

    private ValueAnimator slashPulseAnimator;
    private float slashAlpha = 1.0f;

    public MelodyWifiView(Context context) {
        super(context);
        init();
    }

    public MelodyWifiView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public MelodyWifiView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        activeArcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        activeArcPaint.setStyle(Paint.Style.STROKE);
        activeArcPaint.setStrokeCap(Paint.Cap.ROUND);
        activeArcPaint.setColor(Color.parseColor("#FF4D8D")); // Melody Hot Pink

        inactiveArcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        inactiveArcPaint.setStyle(Paint.Style.STROKE);
        inactiveArcPaint.setStrokeCap(Paint.Cap.ROUND);
        inactiveArcPaint.setColor(Color.parseColor("#44F472B6")); // Soft muted pink

        slashPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        slashPaint.setStyle(Paint.Style.STROKE);
        slashPaint.setStrokeCap(Paint.Cap.ROUND);
        slashPaint.setColor(Color.parseColor("#FF1744")); // Disconnect alert red/coral

        dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dotPaint.setStyle(Paint.Style.FILL);
        dotPaint.setColor(Color.parseColor("#FF4D8D"));

        startWaveAnimation();
        startSlashPulseAnimation();

        setOnClickListener(v -> showWifiDetails());
    }

    private void startWaveAnimation() {
        if (waveAnimator != null) waveAnimator.cancel();
        // Base duration: 1200ms, faster when link speed is high (up to 600ms)
        long duration = Math.max(600L, 1400L - (long) (Math.min(linkSpeedMbps, 100) * 8));
        waveAnimator = ValueAnimator.ofFloat(0f, 1f);
        waveAnimator.setDuration(duration);
        waveAnimator.setRepeatCount(ValueAnimator.INFINITE);
        waveAnimator.setInterpolator(new LinearInterpolator());
        waveAnimator.addUpdateListener(anim -> {
            wavePhase = (float) anim.getAnimatedValue();
            if (isConnected) {
                invalidate();
            }
        });
        waveAnimator.start();
    }

    private void startSlashPulseAnimation() {
        if (slashPulseAnimator != null) slashPulseAnimator.cancel();
        slashPulseAnimator = ValueAnimator.ofFloat(0.35f, 1.0f);
        slashPulseAnimator.setDuration(900);
        slashPulseAnimator.setRepeatCount(ValueAnimator.INFINITE);
        slashPulseAnimator.setRepeatMode(ValueAnimator.REVERSE);
        slashPulseAnimator.addUpdateListener(anim -> {
            slashAlpha = (float) anim.getAnimatedValue();
            if (!isConnected || !isWifiEnabled) {
                invalidate();
            }
        });
        slashPulseAnimator.start();
    }

    public void updateWifiState(boolean enabled, boolean connected, int level, int speedMbps, String wifiSsid) {
        this.isWifiEnabled = enabled;
        this.isConnected = connected;
        this.signalLevel = Math.max(0, Math.min(4, level));
        this.linkSpeedMbps = speedMbps;
        this.ssid = (wifiSsid == null || wifiSsid.contains("unknown")) ? "" : wifiSsid.replace("\"", "");

        startWaveAnimation();
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float stroke = Math.max(dpToPx(2.2f), w * 0.09f);
        activeArcPaint.setStrokeWidth(stroke);
        inactiveArcPaint.setStrokeWidth(stroke);
        slashPaint.setStrokeWidth(stroke * 1.1f);

        float cx = w / 2f;
        float cy = h * 0.82f;

        float r1 = w * 0.26f;
        float r2 = w * 0.44f;
        float r3 = w * 0.62f;

        arcRect1.set(cx - r1, cy - r1, cx + r1, cy + r1);
        arcRect2.set(cx - r2, cy - r2, cx + r2, cy + r2);
        arcRect3.set(cx - r3, cy - r3, cx + r3, cy + r3);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float cx = w / 2f;
        float cy = h * 0.82f;
        float dotRadius = Math.max(dpToPx(2.2f), w * 0.08f);

        // Sweeping arc sweep angle: 90 degrees centered at top (225 to 315)
        float startAngle = 225f;
        float sweepAngle = 90f;

        if (!isWifiEnabled || !isConnected) {
            // Inactive / Disconnected State
            dotPaint.setColor(Color.parseColor("#88F472B6"));
            canvas.drawCircle(cx, cy, dotRadius, dotPaint);

            canvas.drawArc(arcRect1, startAngle, sweepAngle, false, inactiveArcPaint);
            canvas.drawArc(arcRect2, startAngle, sweepAngle, false, inactiveArcPaint);
            canvas.drawArc(arcRect3, startAngle, sweepAngle, false, inactiveArcPaint);

            // Animated diagonal slash across icon
            slashPaint.setAlpha((int) (slashAlpha * 255));
            float pad = w * 0.14f;
            canvas.drawLine(pad, pad, w - pad, h - pad, slashPaint);
            return;
        }

        // CONNECTED STATE
        // Dot is always illuminated
        dotPaint.setColor(Color.parseColor("#FF4D8D"));
        canvas.drawCircle(cx, cy, dotRadius, dotPaint);

        // Dynamic wave highlight calculation
        // wavePhase moves from 0 to 1, lighting up tier 1 -> tier 2 -> tier 3
        drawTierArc(canvas, arcRect1, 1, 0.15f, startAngle, sweepAngle);
        drawTierArc(canvas, arcRect2, 2, 0.45f, startAngle, sweepAngle);
        drawTierArc(canvas, arcRect3, 3, 0.75f, startAngle, sweepAngle);
    }

    private void drawTierArc(Canvas canvas, RectF rect, int tierLevel, float triggerPhase, float startAngle, float sweepAngle) {
        boolean tierActive = (signalLevel >= tierLevel);

        if (!tierActive) {
            canvas.drawArc(rect, startAngle, sweepAngle, false, inactiveArcPaint);
            return;
        }

        // Active tier: add subtle ripple wave glow based on wavePhase
        float dist = Math.abs(wavePhase - triggerPhase);
        if (dist > 0.5f) dist = 1.0f - dist;
        float waveBoost = Math.max(0f, 1.0f - (dist / 0.35f));

        int baseAlpha = 210;
        int boostedAlpha = Math.min(255, (int) (baseAlpha + waveBoost * 45));
        activeArcPaint.setAlpha(boostedAlpha);

        canvas.drawArc(rect, startAngle, sweepAngle, false, activeArcPaint);
    }

    private void showWifiDetails() {
        if (!isWifiEnabled) {
            Toast.makeText(getContext(), "📴 WiFi is turned OFF. Tap to enable in Settings 🌸", Toast.LENGTH_SHORT).show();
            launchWifiSettings();
        } else if (!isConnected) {
            Toast.makeText(getContext(), "⚠️ WiFi Disconnected. Tap to choose network 🌸", Toast.LENGTH_SHORT).show();
            launchWifiSettings();
        } else {
            String speedStr = linkSpeedMbps > 0 ? " (" + linkSpeedMbps + " Mbps)" : "";
            String quality = signalLevel >= 4 ? "Excellent 🚀" : signalLevel >= 3 ? "Good 📶" : signalLevel >= 2 ? "Moderate 🌸" : "Weak ⚠️";
            String info = "📶 Connected: " + (ssid.isEmpty() ? "Wi-Fi" : ssid) + speedStr + "\nSignal: " + quality;
            Toast.makeText(getContext(), info, Toast.LENGTH_SHORT).show();
        }
    }

    private void launchWifiSettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_WIFI_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(intent);
        } catch (Exception ignored) {}
    }

    private float dpToPx(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (waveAnimator != null) waveAnimator.cancel();
        if (slashPulseAnimator != null) slashPulseAnimator.cancel();
    }
}
