package com.kids.launcher;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

import androidx.annotation.Nullable;

/**
 * Intelligent vector battery gauge designed for Melody Kids OS.
 * Features:
 * - Proportional fill level tracking exact battery percentage (0 - 100%)
 * - Percentage text rendered directly INSIDE the battery icon
 * - Distinct animations for Slow vs Fast charging (speed & color waves)
 * - Plug & Unplug bounce animations
 * - Low battery alert heartbeat animation (< 20%)
 */
public class BatteryGaugeView extends View {

    private int percent = 100;
    private boolean isCharging = false;
    private boolean isFastCharger = false;
    private boolean isLowBattery = false;
    private boolean showPercentInside = false;

    private Paint borderPaint;
    private Paint fillPaint;
    private Paint tipPaint;
    private Paint boltPaint;
    private Paint textPaint;
    private Paint textShadowPaint;

    private final RectF bodyRect = new RectF();
    private final RectF tipRect = new RectF();
    private final RectF fillRect = new RectF();
    private final Path boltPath = new Path();

    private ValueAnimator chargeAnimator;
    private ValueAnimator lowAnim;
    private float chargeAnimPhase = 0f;
    private float lowAnimAlpha = 1f;

    public BatteryGaugeView(Context context) {
        super(context);
        init();
    }

    public BatteryGaugeView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public BatteryGaugeView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(dpToPx(2f));
        borderPaint.setColor(Color.parseColor("#FF4D8D")); // Melody Hot Pink

        fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        fillPaint.setStyle(Paint.Style.FILL);

        tipPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        tipPaint.setStyle(Paint.Style.FILL);
        tipPaint.setColor(Color.parseColor("#FF4D8D"));

        boltPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        boltPaint.setStyle(Paint.Style.FILL);
        boltPaint.setColor(Color.parseColor("#FFE600")); // Radiant Charging Gold

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        textPaint.setColor(Color.WHITE);

        textShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textShadowPaint.setStyle(Paint.Style.STROKE);
        textShadowPaint.setTextAlign(Paint.Align.CENTER);
        textShadowPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        textShadowPaint.setStrokeWidth(dpToPx(1.5f));
        textShadowPaint.setColor(Color.parseColor("#80000000"));
    }

    public void setShowPercentInside(boolean show) {
        this.showPercentInside = show;
        invalidate();
    }

    public void setBatteryStatus(int percent, boolean isCharging) {
        setBatteryStatus(percent, isCharging, false);
    }

    public void setBatteryStatus(int percent, boolean isCharging, boolean isFastCharger) {
        boolean wasCharging = this.isCharging;
        this.percent = Math.max(0, Math.min(100, percent));
        this.isCharging = isCharging;
        this.isFastCharger = isFastCharger;
        this.isLowBattery = !isCharging && this.percent <= 20;

        // Trigger plug/unplug bounce animation
        if (wasCharging != isCharging) {
            triggerPlugUnplugAnimation(isCharging);
        }

        // Manage Charging Animation (Fast vs Slow speed)
        if (isCharging) {
            long duration = isFastCharger ? 450 : 1200; // Fast vs Slow charger pulse
            if (chargeAnimator == null || chargeAnimator.getDuration() != duration) {
                if (chargeAnimator != null) {
                    chargeAnimator.cancel();
                }
                chargeAnimator = ValueAnimator.ofFloat(0f, 1f);
                chargeAnimator.setDuration(duration);
                chargeAnimator.setRepeatCount(ValueAnimator.INFINITE);
                chargeAnimator.setRepeatMode(ValueAnimator.REVERSE);
                chargeAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
                chargeAnimator.addUpdateListener(anim -> {
                    chargeAnimPhase = (float) anim.getAnimatedValue();
                    invalidate();
                });
            }
            if (!chargeAnimator.isRunning()) {
                chargeAnimator.start();
            }
        } else {
            if (chargeAnimator != null && chargeAnimator.isRunning()) {
                chargeAnimator.cancel();
                chargeAnimPhase = 0f;
            }
        }

        // Manage Low Battery Pulse Animation
        if (isLowBattery) {
            if (lowAnim == null) {
                lowAnim = ValueAnimator.ofFloat(0.35f, 1.0f);
                lowAnim.setDuration(900);
                lowAnim.setRepeatCount(ValueAnimator.INFINITE);
                lowAnim.setRepeatMode(ValueAnimator.REVERSE);
                lowAnim.setInterpolator(new AccelerateDecelerateInterpolator());
                lowAnim.addUpdateListener(anim -> {
                    lowAnimAlpha = (float) anim.getAnimatedValue();
                    invalidate();
                });
            }
            if (!lowAnim.isRunning()) {
                lowAnim.start();
            }
        } else {
            if (lowAnim != null && lowAnim.isRunning()) {
                lowAnim.cancel();
                lowAnimAlpha = 1f;
            }
        }

        invalidate();
    }

    private void triggerPlugUnplugAnimation(boolean plugged) {
        try {
            float targetScale = plugged ? 1.22f : 0.85f;
            animate().scaleX(targetScale).scaleY(targetScale)
                    .setDuration(160)
                    .setInterpolator(new OvershootInterpolator(2.5f))
                    .withEndAction(() -> animate().scaleX(1.0f).scaleY(1.0f).setDuration(140).start())
                    .start();
        } catch (Exception ignored) {}
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec);
        int h = MeasureSpec.getSize(heightMeasureSpec);
        if (w <= 0) w = (int) dpToPx(showPercentInside ? 56f : 38f);
        if (h <= 0) h = (int) dpToPx(showPercentInside ? 26f : 20f);
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) return;

        float tipWidth = dpToPx(3.5f);
        float strokeWidth = dpToPx(2.2f);
        float cornerRadius = dpToPx(5f);
        float innerPadding = strokeWidth + dpToPx(1f);

        // Body bounds
        bodyRect.set(strokeWidth / 2f, strokeWidth / 2f, w - tipWidth - strokeWidth / 2f, h - strokeWidth / 2f);

        // Positive Tip bounds on the right edge
        float tipHeight = h * 0.45f;
        tipRect.set(w - tipWidth - 1f, (h - tipHeight) / 2f, w, (h + tipHeight) / 2f);

        // Colors
        if (isLowBattery) {
            int alpha = (int) (lowAnimAlpha * 255);
            borderPaint.setColor(Color.argb(255, 225, 29, 72)); // Alert Red-Pink
            borderPaint.setAlpha(alpha);
            tipPaint.setColor(Color.argb(255, 225, 29, 72));
            tipPaint.setAlpha(alpha);
        } else if (isCharging) {
            if (isFastCharger) {
                // Neon turquoise-cyan border for fast charging
                borderPaint.setColor(Color.parseColor("#06B6D4"));
                tipPaint.setColor(Color.parseColor("#06B6D4"));
            } else {
                // Warm pink-gold border for slow/normal charging
                borderPaint.setColor(Color.parseColor("#FF4D8D"));
                tipPaint.setColor(Color.parseColor("#FF4D8D"));
            }
            borderPaint.setAlpha(255);
            tipPaint.setAlpha(255);
        } else {
            borderPaint.setColor(Color.parseColor("#FF4D8D"));
            borderPaint.setAlpha(255);
            tipPaint.setColor(Color.parseColor("#FF4D8D"));
            tipPaint.setAlpha(255);
        }

        // Draw battery outline shell & tip
        canvas.drawRoundRect(bodyRect, cornerRadius, cornerRadius, borderPaint);
        canvas.drawRoundRect(tipRect, dpToPx(2f), dpToPx(2f), tipPaint);

        // Calculate proportional inner fill
        float maxFillWidth = bodyRect.width() - (innerPadding * 2f);
        float currentFillWidth = maxFillWidth * (percent / 100f);

        if (currentFillWidth > 0) {
            fillRect.set(
                    bodyRect.left + innerPadding,
                    bodyRect.top + innerPadding,
                    bodyRect.left + innerPadding + currentFillWidth,
                    bodyRect.bottom - innerPadding
            );

            // Shading & Gradients
            if (isLowBattery) {
                fillPaint.setShader(new LinearGradient(
                        fillRect.left, fillRect.top, fillRect.right, fillRect.bottom,
                        Color.parseColor("#E11D48"), Color.parseColor("#FF4D6D"),
                        Shader.TileMode.CLAMP
                ));
                fillPaint.setAlpha((int) (lowAnimAlpha * 255));
            } else if (isCharging) {
                if (isFastCharger) {
                    // Fast charging: energetic cyan & emerald wave
                    int startColor = chargeAnimPhase > 0.5f ? Color.parseColor("#06B6D4") : Color.parseColor("#10B981");
                    int endColor = chargeAnimPhase > 0.5f ? Color.parseColor("#34D399") : Color.parseColor("#38BDF8");
                    fillPaint.setShader(new LinearGradient(
                            fillRect.left, fillRect.top, fillRect.right, fillRect.bottom,
                            startColor, endColor,
                            Shader.TileMode.CLAMP
                    ));
                } else {
                    // Slow charging: gentle pink & amber wave
                    int startColor = Color.parseColor("#FF4D8D");
                    int endColor = chargeAnimPhase > 0.5f ? Color.parseColor("#FBBF24") : Color.parseColor("#FF85A2");
                    fillPaint.setShader(new LinearGradient(
                            fillRect.left, fillRect.top, fillRect.right, fillRect.bottom,
                            startColor, endColor,
                            Shader.TileMode.CLAMP
                    ));
                }
                fillPaint.setAlpha(255);
            } else if (percent > 35) {
                // Melody strawberry pink gradient
                fillPaint.setShader(new LinearGradient(
                        fillRect.left, fillRect.top, fillRect.right, fillRect.bottom,
                        Color.parseColor("#FF4D8D"), Color.parseColor("#FF85A2"),
                        Shader.TileMode.CLAMP
                ));
                fillPaint.setAlpha(255);
            } else {
                // Amber warm medium level
                fillPaint.setShader(new LinearGradient(
                        fillRect.left, fillRect.top, fillRect.right, fillRect.bottom,
                        Color.parseColor("#F59E0B"), Color.parseColor("#FBBF24"),
                        Shader.TileMode.CLAMP
                ));
                fillPaint.setAlpha(255);
            }

            canvas.drawRoundRect(fillRect, dpToPx(3f), dpToPx(3f), fillPaint);
        }

        // Draw Percentage INSIDE battery icon if enabled
        if (showPercentInside) {
            String pctText = percent + "%";
            float textSize = h * 0.48f;
            textPaint.setTextSize(textSize);
            textShadowPaint.setTextSize(textSize);

            // Center text inside bodyRect
            Paint.FontMetrics fm = textPaint.getFontMetrics();
            float textY = bodyRect.centerY() - (fm.ascent + fm.descent) / 2f;
            float textX = bodyRect.centerX();

            if (isCharging) {
                // Offset slightly to accommodate lightning bolt
                textX += dpToPx(4f);
                float boltX = bodyRect.left + dpToPx(8f);
                drawChargingBolt(canvas, boltX, bodyRect.centerY(), h * 0.55f);
            }

            // Draw outline shadow then main text
            canvas.drawText(pctText, textX, textY, textShadowPaint);
            canvas.drawText(pctText, textX, textY, textPaint);
        } else if (isCharging) {
            drawChargingBolt(canvas, bodyRect.centerX(), bodyRect.centerY(), h * 0.65f);
        }
    }

    private void drawChargingBolt(Canvas canvas, float cx, float cy, float size) {
        float half = size / 2f;
        float scale = isFastCharger ? (0.95f + (chargeAnimPhase * 0.35f)) : (0.85f + (chargeAnimPhase * 0.20f));
        canvas.save();
        canvas.translate(cx, cy);
        canvas.scale(scale, scale);

        boltPath.reset();
        boltPath.moveTo(0f, -half);
        boltPath.lineTo(-half * 0.55f, 0.05f * half);
        boltPath.lineTo(-half * 0.1f, 0.05f * half);
        boltPath.lineTo(-half * 0.3f, half);
        boltPath.lineTo(half * 0.6f, -0.1f * half);
        boltPath.lineTo(half * 0.15f, -0.1f * half);
        boltPath.close();

        if (isFastCharger) {
            boltPaint.setColor(Color.parseColor("#38BDF8")); // Electric Cyan for Fast
        } else {
            boltPaint.setColor(Color.parseColor("#FFE600")); // Gold for Slow
        }
        canvas.drawPath(boltPath, boltPaint);
        canvas.restore();
    }

    private float dpToPx(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }
}
