package com.kids.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * Custom High-Resolution Frame & Sticker Overlay for Melody Camera.
 * Renders cute My Melody rabbit ears, bows, hearts, strawberries, and polaroid borders.
 */
public class MelodyFrameOverlayView extends View {

    public static final int FRAME_NONE = 0;
    public static final int FRAME_MELODY_EARS = 1;
    public static final int FRAME_SPARKLES_STARS = 2;
    public static final int FRAME_SWEET_STRAWBERRY = 3;
    public static final int FRAME_HEART_CLOUDS = 4;
    public static final int FRAME_POLAROID = 5;

    private int currentFrame = FRAME_MELODY_EARS;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public MelodyFrameOverlayView(Context context) {
        super(context);
    }

    public MelodyFrameOverlayView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public void setFrame(int frameType) {
        this.currentFrame = frameType;
        invalidate();
    }

    public int getFrame() {
        return currentFrame;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        drawFrameOverlay(canvas, getWidth(), getHeight(), currentFrame);
    }

    public static void drawFrameOverlay(Canvas canvas, int w, int h, int frameType) {
        if (w <= 0 || h <= 0) return;

        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float density = w / 800f; // Scale nicely across screen & full photo bitmap

        switch (frameType) {
            case FRAME_MELODY_EARS:
                drawMelodyEarsFrame(canvas, w, h, p, density);
                break;
            case FRAME_SPARKLES_STARS:
                drawSparklesFrame(canvas, w, h, p, density);
                break;
            case FRAME_SWEET_STRAWBERRY:
                drawStrawberryFrame(canvas, w, h, p, density);
                break;
            case FRAME_HEART_CLOUDS:
                drawHeartCloudsFrame(canvas, w, h, p, density);
                break;
            case FRAME_POLAROID:
                drawPolaroidFrame(canvas, w, h, p, density);
                break;
            default:
                break;
        }
    }

    private static void drawMelodyEarsFrame(Canvas canvas, int w, int h, Paint p, float density) {
        // Cute pink scalloped border
        p.setStyle(Paint.Style.STROKE);
        p.setColor(Color.parseColor("#FF65A5"));
        p.setStrokeWidth(12 * density);
        canvas.drawRoundRect(new RectF(16 * density, 16 * density, w - 16 * density, h - 16 * density), 28 * density, 28 * density, p);

        // White inner highlight
        p.setColor(Color.WHITE);
        p.setStrokeWidth(3 * density);
        canvas.drawRoundRect(new RectF(22 * density, 22 * density, w - 22 * density, h - 22 * density), 22 * density, 22 * density, p);

        // My Melody Bunny Ears at top center
        float cx = w / 2f;
        float earTop = 40 * density;
        float earWidth = 36 * density;
        float earHeight = 90 * density;

        p.setStyle(Paint.Style.FILL);
        // Left Ear (Outer Pink)
        p.setColor(Color.parseColor("#FF4D8D"));
        RectF leftEarOuter = new RectF(cx - 70 * density - earWidth / 2, earTop, cx - 70 * density + earWidth / 2, earTop + earHeight);
        canvas.drawRoundRect(leftEarOuter, earWidth / 2, earWidth / 2, p);
        // Left Ear (Inner White/Soft Pink)
        p.setColor(Color.parseColor("#FFF0F5"));
        RectF leftEarInner = new RectF(cx - 70 * density - earWidth / 3.5f, earTop + 15 * density, cx - 70 * density + earWidth / 3.5f, earTop + earHeight - 12 * density);
        canvas.drawRoundRect(leftEarInner, earWidth / 3.5f, earWidth / 3.5f, p);

        // Right Ear (Outer Pink - slightly bent cute My Melody ear)
        p.setColor(Color.parseColor("#FF4D8D"));
        RectF rightEarOuter = new RectF(cx + 70 * density - earWidth / 2, earTop + 10 * density, cx + 70 * density + earWidth / 2, earTop + earHeight);
        canvas.drawRoundRect(rightEarOuter, earWidth / 2, earWidth / 2, p);
        // Right Ear (Inner Soft Pink)
        p.setColor(Color.parseColor("#FFF0F5"));
        RectF rightEarInner = new RectF(cx + 70 * density - earWidth / 3.5f, earTop + 22 * density, cx + 70 * density + earWidth / 3.5f, earTop + earHeight - 10 * density);
        canvas.drawRoundRect(rightEarInner, earWidth / 3.5f, earWidth / 3.5f, p);

        // Cute Ribbon Bow right below ear
        p.setTextSize(38 * density);
        p.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("🎀", cx - 70 * density, earTop + earHeight + 20 * density, p);

        // Bottom Sweet Ribbon Banner
        p.setColor(Color.parseColor("#99FF4D8D"));
        p.setStyle(Paint.Style.FILL);
        RectF bottomPill = new RectF(cx - 150 * density, h - 50 * density, cx + 150 * density, h - 16 * density);
        canvas.drawRoundRect(bottomPill, 20 * density, 20 * density, p);

        p.setColor(Color.WHITE);
        p.setTextSize(18 * density);
        p.setFakeBoldText(true);
        canvas.drawText("🌸 My Melody Sweet Sparkle 🌸", cx, h - 27 * density, p);
    }

    private static void drawSparklesFrame(Canvas canvas, int w, int h, Paint p, float density) {
        // Pastel lavender-pink border with stars
        p.setStyle(Paint.Style.STROKE);
        p.setColor(Color.parseColor("#C084FC"));
        p.setStrokeWidth(10 * density);
        canvas.drawRoundRect(new RectF(16 * density, 16 * density, w - 16 * density, h - 16 * density), 24 * density, 24 * density, p);

        p.setStyle(Paint.Style.FILL);
        p.setTextSize(26 * density);
        p.setTextAlign(Paint.Align.CENTER);

        // Corners & Edge Sparkles
        canvas.drawText("⭐", 40 * density, 52 * density, p);
        canvas.drawText("✨", w - 40 * density, 52 * density, p);
        canvas.drawText("🌟", 40 * density, h - 35 * density, p);
        canvas.drawText("✨", w - 40 * density, h - 35 * density, p);
        canvas.drawText("💖", w / 2f, 44 * density, p);
        canvas.drawText("🎀", 40 * density, h / 2f, p);
        canvas.drawText("🎀", w - 40 * density, h / 2f, p);

        // Title at bottom
        p.setColor(Color.parseColor("#AA6B21A8"));
        RectF pill = new RectF(w / 2f - 130 * density, h - 48 * density, w / 2f + 130 * density, h - 16 * density);
        canvas.drawRoundRect(pill, 16 * density, 16 * density, p);
        p.setColor(Color.WHITE);
        p.setTextSize(16 * density);
        p.setFakeBoldText(true);
        canvas.drawText("✨ Magical Starlight ✨", w / 2f, h - 26 * density, p);
    }

    private static void drawStrawberryFrame(Canvas canvas, int w, int h, Paint p, float density) {
        // Sweet Strawberry Red & Pastel Pink
        p.setStyle(Paint.Style.STROKE);
        p.setColor(Color.parseColor("#FF4D6D"));
        p.setStrokeWidth(12 * density);
        canvas.drawRoundRect(new RectF(16 * density, 16 * density, w - 16 * density, h - 16 * density), 26 * density, 26 * density, p);

        p.setStyle(Paint.Style.FILL);
        p.setTextSize(30 * density);
        p.setTextAlign(Paint.Align.CENTER);

        canvas.drawText("🍓", 45 * density, 55 * density, p);
        canvas.drawText("🍰", w - 45 * density, 55 * density, p);
        canvas.drawText("🌸", 45 * density, h - 35 * density, p);
        canvas.drawText("🍓", w - 45 * density, h - 35 * density, p);
        canvas.drawText("🍓", w / 2f, 48 * density, p);

        p.setColor(Color.parseColor("#CCFF4D6D"));
        RectF pill = new RectF(w / 2f - 140 * density, h - 48 * density, w / 2f + 140 * density, h - 16 * density);
        canvas.drawRoundRect(pill, 16 * density, 16 * density, p);
        p.setColor(Color.WHITE);
        p.setTextSize(16 * density);
        p.setFakeBoldText(true);
        canvas.drawText("🍓 Sweet Strawberry Cafe 🍓", w / 2f, h - 26 * density, p);
    }

    private static void drawHeartCloudsFrame(Canvas canvas, int w, int h, Paint p, float density) {
        p.setStyle(Paint.Style.STROKE);
        p.setColor(Color.parseColor("#F472B6"));
        p.setStrokeWidth(10 * density);
        canvas.drawRoundRect(new RectF(16 * density, 16 * density, w - 16 * density, h - 16 * density), 24 * density, 24 * density, p);

        p.setStyle(Paint.Style.FILL);
        p.setTextSize(28 * density);
        p.setTextAlign(Paint.Align.CENTER);

        canvas.drawText("💖", 45 * density, 50 * density, p);
        canvas.drawText("💕", w - 45 * density, 50 * density, p);
        canvas.drawText("☁️", w / 2f - 80 * density, 48 * density, p);
        canvas.drawText("🌈", w / 2f + 80 * density, 48 * density, p);
        canvas.drawText("💝", 45 * density, h - 35 * density, p);
        canvas.drawText("💖", w - 45 * density, h - 35 * density, p);

        p.setColor(Color.parseColor("#BBDB2777"));
        RectF pill = new RectF(w / 2f - 130 * density, h - 48 * density, w / 2f + 130 * density, h - 16 * density);
        canvas.drawRoundRect(pill, 16 * density, 16 * density, p);
        p.setColor(Color.WHITE);
        p.setTextSize(16 * density);
        p.setFakeBoldText(true);
        canvas.drawText("💖 Cute Melody Hearts 💖", w / 2f, h - 26 * density, p);
    }

    private static void drawPolaroidFrame(Canvas canvas, int w, int h, Paint p, float density) {
        // Classic White/Blush Polaroid frame with thick bottom margin
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.parseColor("#FFF5F7"));
        // Top border
        canvas.drawRect(0, 0, w, 32 * density, p);
        // Left border
        canvas.drawRect(0, 0, 32 * density, h, p);
        // Right border
        canvas.drawRect(w - 32 * density, 0, w, h, p);
        // Thick bottom polaroid border
        canvas.drawRect(0, h - 85 * density, w, h, p);

        // Cute thin pink inner border around photo
        p.setStyle(Paint.Style.STROKE);
        p.setColor(Color.parseColor("#F472B6"));
        p.setStrokeWidth(3 * density);
        canvas.drawRect(32 * density, 32 * density, w - 32 * density, h - 85 * density, p);

        // Handwritten-style bottom caption
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.parseColor("#9D174D"));
        p.setTextSize(22 * density);
        p.setFakeBoldText(true);
        p.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("✨ Melody Sweet Memory ✨ 🌸", w / 2f, h - 35 * density, p);

        p.setTextSize(24 * density);
        canvas.drawText("🎀", 65 * density, h - 35 * density, p);
        canvas.drawText("📷", w - 65 * density, h - 35 * density, p);
    }
}
