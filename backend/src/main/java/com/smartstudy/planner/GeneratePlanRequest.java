package com.smartstudy.planner;

import java.time.LocalDate;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * <pre>
 * POST /api/v1/planner/generate
 * { "startDate": "2026-10-31", "days": 7 }      (both optional: defaults are today and 7 days)
 * </pre>
 */
public record GeneratePlanRequest(LocalDate startDate, @Min(1) @Max(28) Integer days) {
}
