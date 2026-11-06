package com.smartstudy.planner;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import com.smartstudy.revision.RevisionRating;

/**
 * <pre>
 * POST /api/v1/sessions/41/complete
 * { "actualMinutes": 45, "topicCompleted": false, "revisionRating": null, "notes": "Did 10 problems" }
 * </pre>
 * <ul>
 *   <li>{@code actualMinutes} is optional; when omitted the server uses the focus time recorded for
 *       the session or the time elapsed since it started (capped at the planned length).</li>
 *   <li>{@code revisionRating} (AGAIN/HARD/GOOD/EASY) is required for REVISION sessions.</li>
 * </ul>
 * A session with at least 80 % of its planned minutes becomes COMPLETED, otherwise INCOMPLETE
 * (and the missing work is rescheduled).
 */
public record CompleteSessionRequest(
        @Min(1) @Max(600) Integer actualMinutes,
        Boolean topicCompleted,
        RevisionRating revisionRating,
        @Size(max = 1000) String notes) {
}
