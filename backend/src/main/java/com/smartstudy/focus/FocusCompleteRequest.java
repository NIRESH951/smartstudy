package com.smartstudy.focus;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * <pre>
 * POST /api/v1/focus/9/complete
 * { "clientReportedSeconds": 1500, "notes": "Solved 6 problems" }
 * </pre>
 * The client value can only lower the stored duration; the server measurement is the upper bound.
 */
public record FocusCompleteRequest(
        @Min(0) @Max(86400) Integer clientReportedSeconds,
        @Size(max = 500) String notes) {
}
