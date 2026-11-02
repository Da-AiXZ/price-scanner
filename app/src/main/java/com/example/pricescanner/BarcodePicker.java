package com.example.pricescanner;

import android.graphics.Rect;
import android.graphics.RectF;

import androidx.camera.core.ImageProxy;
import androidx.annotation.NonNull;

import com.google.mlkit.vision.barcode.common.Barcode;

import java.util.List;

public final class BarcodePicker {

    private BarcodePicker() {
    }

    /**
     * 只接受完整落在取景框内的条码；多个时优先取靠近中心且面积较大的一个。
     */
    public static String pickInFrame(
            @NonNull List<Barcode> barcodes,
            @NonNull ImageProxy imageProxy,
            float viewWidth,
            float viewHeight,
            @NonNull RectF frame) {
        if (frame.isEmpty() || viewWidth <= 0 || viewHeight <= 0) {
            return null;
        }

        int rotation = imageProxy.getImageInfo().getRotationDegrees();
        float imageWidth = imageProxy.getWidth();
        float imageHeight = imageProxy.getHeight();
        if (rotation == 90 || rotation == 270) {
            float swap = imageWidth;
            imageWidth = imageHeight;
            imageHeight = swap;
        }
        if (imageWidth <= 0 || imageHeight <= 0) {
            return null;
        }

        float scale = Math.max(viewWidth / imageWidth, viewHeight / imageHeight);
        float offsetX = (viewWidth - imageWidth * scale) / 2f;
        float offsetY = (viewHeight - imageHeight * scale) / 2f;

        float centerX = frame.centerX();
        float centerY = frame.centerY();
        float halfDiagonal = (float) Math.hypot(
                frame.width() / 2f, frame.height() / 2f);

        String bestValue = null;
        float bestScore = 0;
        for (Barcode barcode : barcodes) {
            String raw = barcode.getRawValue();
            if (raw == null) {
                continue;
            }
            String value = raw.trim();
            if (!isScannable(barcode, value)) {
                continue;
            }
            Rect box = barcode.getBoundingBox();
            if (box == null || box.width() <= 0 || box.height() <= 0) {
                continue;
            }
            RectF mapped = new RectF(
                    box.left * scale + offsetX,
                    box.top * scale + offsetY,
                    box.right * scale + offsetX,
                    box.bottom * scale + offsetY);
            if (mapped.left < frame.left
                    || mapped.top < frame.top
                    || mapped.right > frame.right
                    || mapped.bottom > frame.bottom) {
                continue;
            }
            float area = mapped.width() * mapped.height();
            float distance = (float) Math.hypot(
                    mapped.centerX() - centerX, mapped.centerY() - centerY);
            float distanceRatio = Math.min(1f, distance / halfDiagonal);
            float score = area * (1.5f - distanceRatio);
            if (score > bestScore) {
                bestScore = score;
                bestValue = value;
            }
        }
        return bestValue;
    }

    public static boolean isScannable(Barcode barcode, String value) {
        switch (barcode.getFormat()) {
            case Barcode.FORMAT_EAN_13:
                return value.length() == 13
                        && isDigits(value)
                        && hasValidCheckDigit(value, false);
            case Barcode.FORMAT_EAN_8:
                return value.length() == 8
                        && isDigits(value)
                        && hasValidCheckDigit(value, true);
            case Barcode.FORMAT_UPC_A:
                return value.length() == 12
                        && isDigits(value)
                        && hasValidCheckDigit(value, true);
            case Barcode.FORMAT_UPC_E:
                return value.length() == 8 && isDigits(value);
            case Barcode.FORMAT_CODE_128:
                return value.length() >= 6;
            case Barcode.FORMAT_ITF:
                return value.length() >= 6
                        && value.length() <= 14
                        && isDigits(value);
            case Barcode.FORMAT_CODE_39:
                return value.length() >= 6;
            default:
                return false;
        }
    }

    private static boolean isDigits(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }

    private static boolean hasValidCheckDigit(String code, boolean weightThreeFirst) {
        int sum = 0;
        for (int i = 0; i < code.length() - 1; i++) {
            int digit = code.charAt(i) - '0';
            boolean useThree = (i % 2 == 0) == weightThreeFirst;
            sum += digit * (useThree ? 3 : 1);
        }
        int expected = (10 - (sum % 10)) % 10;
        return expected == code.charAt(code.length() - 1) - '0';
    }
}
