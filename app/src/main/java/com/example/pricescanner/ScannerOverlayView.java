package com.example.pricescanner;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

public class ScannerOverlayView extends View {

    public static final int MODE_IDLE = 0;
    public static final int MODE_SCANNING = 1;
    public static final int MODE_SUCCESS = 2;

    public static final int FRAME_INSET = 0;
    public static final int FRAME_WIDE = 1;

    private final Paint cornerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint flashPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint focusPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint scrimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF frameRect = new RectF();

    private final int colorWhite;
    private final int colorMint;
    private final int colorBlue;

    private int mode = MODE_IDLE;
    private int frameStyle = FRAME_INSET;
    private float scanProgress;
    private ValueAnimator scanAnimator;

    private float flashAlpha;
    private ValueAnimator flashAnimator;

    private float focusX;
    private float focusY;
    private float focusAlpha;
    private float focusScale = 1f;
    private ValueAnimator focusAnimator;

    public ScannerOverlayView(Context context) {
        this(context, null);
    }

    public ScannerOverlayView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        colorWhite = ContextCompat.getColor(context, R.color.white);
        colorMint = ContextCompat.getColor(context, R.color.mint);
        colorBlue = ContextCompat.getColor(context, R.color.blue);
        init();
    }

    private void init() {
        cornerPaint.setStyle(Paint.Style.STROKE);
        cornerPaint.setStrokeWidth(dp(3.5f));
        cornerPaint.setStrokeCap(Paint.Cap.ROUND);

        linePaint.setStyle(Paint.Style.FILL);

        focusPaint.setStyle(Paint.Style.STROKE);
        focusPaint.setStrokeWidth(dp(2));
        focusPaint.setStrokeCap(Paint.Cap.ROUND);
        focusPaint.setColor(colorMint);

        scrimPaint.setStyle(Paint.Style.FILL);
        scrimPaint.setColor(0x4D000000);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateFrameRect();
        startScanAnimation();
    }

    private void updateFrameRect() {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        if (frameStyle == FRAME_WIDE) {
            float frameWidth = Math.min(w - dp(48), dp(360));
            float frameHeight = Math.min(frameWidth * 0.55f, dp(200));
            float centerX = w / 2f;
            float centerY = h * 0.46f;
            frameRect.set(
                    centerX - frameWidth / 2f,
                    centerY - frameHeight / 2f,
                    centerX + frameWidth / 2f,
                    centerY + frameHeight / 2f);
        } else {
            float inset = dp(16);
            frameRect.set(inset, inset, w - inset, h - inset);
        }
    }

    public void setFrameStyle(int style) {
        if (frameStyle == style) {
            return;
        }
        frameStyle = style;
        updateFrameRect();
        invalidate();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (frameRect.isEmpty()) {
            return;
        }
        if (frameStyle == FRAME_WIDE) {
            drawScrim(canvas);
        }
        drawCorners(canvas);
        if (mode != MODE_SUCCESS) {
            drawScanLine(canvas);
        }
        if (flashAlpha > 0) {
            drawFlash(canvas);
        }
        if (focusAlpha > 0) {
            drawFocusRing(canvas);
        }
    }

    private void drawScrim(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        canvas.drawRect(0, 0, w, frameRect.top, scrimPaint);
        canvas.drawRect(0, frameRect.bottom, w, h, scrimPaint);
        canvas.drawRect(0, frameRect.top, frameRect.left, frameRect.bottom, scrimPaint);
        canvas.drawRect(frameRect.right, frameRect.top, w, frameRect.bottom, scrimPaint);
    }

    private void drawCorners(Canvas canvas) {
        int color = mode == MODE_IDLE ? colorWhite : colorMint;
        cornerPaint.setColor(color);
        cornerPaint.setAlpha(mode == MODE_IDLE ? 209 : 255);

        float grow = mode == MODE_SUCCESS ? dp(2) : 0;
        float len = dp(26) + grow;
        float left = frameRect.left - grow / 2;
        float top = frameRect.top - grow / 2;
        float right = frameRect.right + grow / 2;
        float bottom = frameRect.bottom + grow / 2;

        canvas.drawLine(left, top + len, left, top, cornerPaint);
        canvas.drawLine(left, top, left + len, top, cornerPaint);
        canvas.drawLine(right - len, top, right, top, cornerPaint);
        canvas.drawLine(right, top, right, top + len, cornerPaint);
        canvas.drawLine(left, bottom - len, left, bottom, cornerPaint);
        canvas.drawLine(left, bottom, left + len, bottom, cornerPaint);
        canvas.drawLine(right - len, bottom, right, bottom, cornerPaint);
        canvas.drawLine(right, bottom, right, bottom - len, cornerPaint);
    }

    private void drawScanLine(Canvas canvas) {
        boolean active = mode == MODE_SCANNING;
        int base = active ? colorMint : colorBlue;
        float wave = (float) Math.sin(scanProgress * Math.PI);
        int alpha = (int) (255 * wave);

        float startX = frameRect.left + dp(4);
        float endX = frameRect.right - dp(4);
        float y = frameRect.top + dp(10)
                + (frameRect.height() - dp(20)) * scanProgress;

        int clear = base & 0x00FFFFFF;
        int solid = (alpha << 24) | clear;
        linePaint.setShader(new LinearGradient(
                startX, 0, endX, 0,
                new int[]{clear, solid, clear},
                null, Shader.TileMode.CLAMP));
        canvas.drawRect(startX, y - dp(1.2f), endX, y + dp(1.2f), linePaint);

        linePaint.setAlpha((int) (alpha * 0.28f));
        canvas.drawRect(startX, y - dp(6), endX, y + dp(6), linePaint);
        linePaint.setAlpha(255);
    }

    private void drawFlash(Canvas canvas) {
        float cx = frameRect.centerX();
        float cy = frameRect.centerY();
        float radius = Math.max(frameRect.width(), frameRect.height()) * 0.62f;
        int solid = ((int) (flashAlpha * 82) << 24) | (colorMint & 0x00FFFFFF);
        int clear = colorMint & 0x00FFFFFF;
        flashPaint.setShader(new RadialGradient(
                cx, cy, radius,
                new int[]{solid, clear},
                new float[]{0f, 1f},
                Shader.TileMode.CLAMP));
        canvas.drawRect(frameRect, flashPaint);
    }

    private void drawFocusRing(Canvas canvas) {
        float size = dp(64) * focusScale;
        RectF rect = new RectF(
                focusX - size / 2,
                focusY - size / 2,
                focusX + size / 2,
                focusY + size / 2);
        focusPaint.setAlpha((int) (255 * focusAlpha));
        canvas.drawRoundRect(rect, dp(4), dp(4), focusPaint);
    }

    public RectF getFrameRect() {
        return new RectF(frameRect);
    }

    public void setMode(int newMode) {
        if (mode == newMode) {
            return;
        }
        mode = newMode;
        if (newMode == MODE_SUCCESS) {
            stopScanAnimation();
        } else {
            startScanAnimation();
        }
        invalidate();
    }

    public void playSuccess() {
        setMode(MODE_SUCCESS);
        if (flashAnimator != null) {
            flashAnimator.cancel();
        }
        flashAnimator = ValueAnimator.ofFloat(0.85f, 0f);
        flashAnimator.setDuration(700);
        flashAnimator.setInterpolator(new DecelerateInterpolator());
        flashAnimator.addUpdateListener(animation -> {
            flashAlpha = (float) animation.getAnimatedValue();
            invalidate();
        });
        flashAnimator.start();
    }

    public void showFocusRing(float x, float y) {
        focusX = x;
        focusY = y;
        if (focusAnimator != null) {
            focusAnimator.cancel();
        }
        focusAnimator = ValueAnimator.ofFloat(0f, 1f);
        focusAnimator.setDuration(620);
        focusAnimator.setInterpolator(new DecelerateInterpolator());
        focusAnimator.addUpdateListener(animation -> {
            float progress = (float) animation.getAnimatedValue();
            focusScale = 1.16f - 0.16f * progress;
            focusAlpha = 1f - progress;
            invalidate();
        });
        focusAnimator.start();
    }

    private void startScanAnimation() {
        stopScanAnimation();
        int duration = mode == MODE_SCANNING ? 1100 : 2800;
        scanAnimator = ValueAnimator.ofFloat(0f, 1f);
        scanAnimator.setDuration(duration);
        scanAnimator.setRepeatCount(ValueAnimator.INFINITE);
        scanAnimator.setInterpolator(new LinearInterpolator());
        scanAnimator.addUpdateListener(animation -> {
            scanProgress = (float) animation.getAnimatedValue();
            invalidate();
        });
        scanAnimator.start();
    }

    private void stopScanAnimation() {
        if (scanAnimator != null) {
            scanAnimator.cancel();
            scanAnimator = null;
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startScanAnimation();
    }

    @Override
    protected void onDetachedFromWindow() {
        stopScanAnimation();
        if (flashAnimator != null) {
            flashAnimator.cancel();
            flashAnimator = null;
        }
        if (focusAnimator != null) {
            focusAnimator.cancel();
            focusAnimator = null;
        }
        super.onDetachedFromWindow();
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
