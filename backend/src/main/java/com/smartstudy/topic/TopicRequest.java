package com.smartstudy.topic;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * <pre>
 * POST /api/v1/subjects/3/topics
 * { "name": "AVL Trees", "description": "Rotations and balancing", "difficulty": 4,
 *   "estimatedMinutes": 180, "status": "NOT_STARTED" }
 * </pre>
 */
public record TopicRequest(
        @NotBlank @Size(max = 150) String name,
        @Size(max = 1000) String description,
        @Min(1) @Max(5) int difficulty,
        @Min(15) @Max(6000) int estimatedMinutes,
        TopicStatus status) {
}
