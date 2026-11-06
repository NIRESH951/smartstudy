package com.smartstudy.subject;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * <pre>
 * POST /api/v1/subjects
 * { "name": "Data Structures", "code": "CS201", "color": "#3B82F6", "importance": 5,
 *   "targetScore": 85, "description": "Trees, graphs, hashing", "archived": false }
 * </pre>
 */
public record SubjectRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 20) String code,
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "must be a hex colour such as #10B981") String color,
        @Min(1) @Max(5) int importance,
        @DecimalMin("0") @DecimalMax("100") BigDecimal targetScore,
        @Size(max = 500) String description,
        Boolean archived) {
}
