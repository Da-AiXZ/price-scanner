package com.example.pricescanner;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

public class ScannerOverlayView extends View {

    private final Paint dimPaint = new Paint();
    private final Paint framePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cornerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF frameRect = new RectF();
    private final Path clipPath = new Path();

    private ValueAnimator animator;
    private float scanProgress;
    private boolean scanning = true;

    public ScannerOverlayView(Context context) {
        this(context, null);
    }

    public ScannerOverlayView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        dimPaint.setColor(Color.argb(148, 0, 0, 0));

        framePaint.setStyle(Paint.Style.STROKE);
        framePaint.setStrokeWidth(dp(1));
        framePaint.setColor(Color.argb(46, 255, 255, 255));

        cornerPaint.setStyle(Paint.Style.STROKE);
        cornerPaint.setStrokeWidth(dp(3.5f));
        cornerPaint.setStrokeCap(Paint.Cap.ROUND);
        cornerPaint.setColor(ContextCompat.getColor(getContext(), R.color.accent));

        linePaint.setStyle(Paint.Style.FILL);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float sideMargin = dp(44);
        float frameWidth = Math.max(0, w - sideMargin * 2);
        float frameHeight = Math.min(dp(200), frameWidth * 0.62f);
        float centerY = h * 0.44f;
        frameRect.set(
                sideMargin,
                centerY - frameHeight / 2f,
                w - sideMargin,
                centerY + frameHeight / 2f);

        clipPath.reset();
        clipPath.addRoundRect(frameRect, dp(18), dp(18), Path.Direction.CW);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (frameRect.isEmpty()) {
            return;
        }

        canvas.drawRect(0, 0, getWidth(), frameRect.top, dimPaint);
        canvas.drawRect(0, frameRect.bottom, getWidth(), getHeight(), dimPaint);
        canvas.drawRect(0, frameRect.top, frameRect.left, frameRect.bottom, dimPaint);
        canvas.drawRect(frameRect.right, frameRect.top, getWidth(), frameRect.bottom, dimPaint);

        canvas.drawRoundRect(frameRect, dp(18), dp(18), framePaint);
        drawCorners(canvas);

        if (scanning) {
            drawScanLine(canvas);
        }
    }

    private void drawCorners(Canvas canvas) {
        float len = dp(30);
        float left = frameRect.left;
        float top = frameRect.top;
        float right = frameRect.right;
        float bottom = frameRect.bottom;

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
        float y = frameRect.top + frameRect.height() * scanProgress;
        int accent = ContextCompat.getColor(getContext(), R.color.accent);

        int alpha = Color.alpha(accent);
        int transparentAccent = (alpha << 24) | (accent & 0x00FFFFFF);
        int clearAccent = accent & 0x00FFFFFF;
        linePaint.setShader(new LinearGradient(
                frameRect.left, 0, frameRect.right, 0,
                new int[]{clearAccent, transparentAccent, clearAccent},
                null, Shader.TileMode.CLAMP));

        int saveCount = canvas.save();
        canvas.clipPath(clipPath);
        canvas.drawRect(
                frameRect.left + dp(12),
                y - dp(1.2f),
                frameRect.right - dp(12),
                y + dp(1.2f),
                linePaint);
        canvas.drawRect(
                frameRect.left + dp(12),
                y - dp(7),
                frameRect.right - dp(12),
                y + dp(7),
                linePaint);
        canvas.restoreToCount(saveCount);
    }

    public RectF getFrameRect() {
        return new RectF(frameRect);
    }

    public void setScanning(boolean scanning) {
        if (this.scanning == scanning) {
            return;
        }
        this.scanning = scanning;
        if (scanning) {
            startAnimation();
        } else {
            stopAnimation();
        }
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (scanning) {
            startAnimation();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        stopAnimation();
        super.onDetachedFromWindow();
    }

    private void startAnimation() {
        if (animator != null && animator.isRunning()) {
            return;
        }
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(2200);
        animator.setRepeatMode(ValueAnimator.REVERSE);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(animation -> {
            scanProgress = (float) animation.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    private void stopAnimation() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
