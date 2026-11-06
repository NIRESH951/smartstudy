package com.smartstudy.focus;

import java.util.EnumSet;
import java.util.Set;

public enum FocusStatus {
    RUNNING,
    PAUSED,
    COMPLETED,
    CANCELLED;

    public static final Set<FocusStatus> ACTIVE = EnumSet.of(RUNNING, PAUSED);
}
