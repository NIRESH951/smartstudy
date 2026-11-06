package com.smartstudy.planner;

import java.util.EnumSet;
import java.util.Set;

/**
 * Life cycle of a study session.
 *
 * <pre>
 * PLANNED --start--> IN_PROGRESS --complete--> COMPLETED | INCOMPLETE
 * PLANNED --complete--------------------------> COMPLETED | INCOMPLETE
 * PLANNED --miss / end time passed-----------> MISSED
 * PLANNED --delete (manual)------------------> CANCELLED
 * </pre>
 */
public enum SessionStatus {
    PLANNED,
    IN_PROGRESS,
    COMPLETED,
    INCOMPLETE,
    MISSED,
    CANCELLED;

    /** Statuses that occupy their time slot (used for overlap / conflict detection). */
    public static final Set<SessionStatus> BLOCKING = EnumSet.of(PLANNED, IN_PROGRESS, COMPLETED, INCOMPLETE);

    /** Statuses that represent study actually done. */
    public static final Set<SessionStatus> DONE = EnumSet.of(COMPLETED, INCOMPLETE);
}
