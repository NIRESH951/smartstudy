package com.smartstudy.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Small numeric helpers shared by the scoring algorithms (all scores use a 0-100 scale). */
public final class ScoreMath {

    private ScoreMath() {
    }

    /** Limits a score to the inclusive range 0..100. */
    public static double clamp(double value) {
        if (Double.isNaN(value)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(100.0, value));
    }

    /** Rounds half-up to two decimal places. */
    public static double round2(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.0;
        }
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    public static BigDecimal toDecimal(double value) {
        return BigDecimal.valueOf(round2(value)).setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal toDecimalOrNull(Double value) {
        return value == null ? null : toDecimal(value);
    }

    public static Double toDouble(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }

    /** obtained / max * 100, rounded to two decimals. */
    public static BigDecimal percent(BigDecimal obtained, BigDecimal max) {
        if (max == null || max.signum() <= 0 || obtained == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return obtained.multiply(BigDecimal.valueOf(100)).divide(max, 2, RoundingMode.HALF_UP);
    }
}
