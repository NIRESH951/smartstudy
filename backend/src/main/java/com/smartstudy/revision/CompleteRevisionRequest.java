package com.smartstudy.revision;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * <pre>
 * POST /api/v1/revisions/12/complete
 * { "rating": "GOOD", "durationMinutes": 25, "studySessionId": null, "notes": "Rotations are clear now" }
 * </pre>
 */
public record CompleteRevisionRequest(
        @NotNull RevisionRating rating,
        @Min(1) @Max(600) Integer durationMinutes,
        Long studySessionId,
        @Size(max = 500) String notes) {
}
