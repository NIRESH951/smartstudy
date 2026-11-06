package com.smartstudy.readiness;

public enum ReadinessLevel {
    WELL_PREPARED,
    ON_TRACK,
    NEEDS_WORK,
    AT_RISK;

    public static ReadinessLevel of(Double score) {
        if (score == null) {
            return null;
        }
        if (score >= 80.0) {
            return WELL_PREPARED;
        }
        if (score >= 60.0) {
            return ON_TRACK;
        }
        if (score >= 40.0) {
            return NEEDS_WORK;
        }
        return AT_RISK;
    }
}
