package com.example.pricescanner;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class MoneyUtils {

    private MoneyUtils() {
    }

    public static String formatCents(long cents) {
        return BigDecimal.valueOf(cents, 2).toPlainString();
    }

    public static Long parseYuanToCents(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim().replace(',', '.');
        if (value.isEmpty()) {
            return null;
        }
        try {
            BigDecimal yuan = new BigDecimal(value).setScale(2, RoundingMode.HALF_UP);
            if (yuan.compareTo(BigDecimal.ZERO) <= 0) {
                return null;
            }
            return yuan.movePointRight(2).longValueExact();
        } catch (ArithmeticException | NumberFormatException e) {
            return null;
        }
    }
}
