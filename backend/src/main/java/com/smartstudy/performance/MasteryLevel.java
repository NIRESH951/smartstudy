package com.smartstudy.performance;

/**
 * Mastery classification:
 * <ul>
 *   <li>0 - 39.99: WEAK</li>
 *   <li>40 - 64.99: NEEDS_IMPROVEMENT</li>
 *   <li>65 - 79.99: GOOD</li>
 *   <li>80 - 100: STRONG</li>
 * </ul>
 * Topics without any assessment are reported as NOT_ASSESSED - they are never treated as 0% mastery.
 */
public enum MasteryLevel {
    WEAK,
    NEEDS_IMPROVEMENT,
    GOOD,
    STRONG,
    NOT_ASSESSED;

    public static MasteryLevel of(Double masteryScore) {
        if (masteryScore == null) {
            return NOT_ASSESSED;
        }
        if (masteryScore < 40.0) {
            return WEAK;
        }
        if (masteryScore < 65.0) {
            return NEEDS_IMPROVEMENT;
        }
        if (masteryScore < 80.0) {
            return GOOD;
        }
        return STRONG;
    }
}
