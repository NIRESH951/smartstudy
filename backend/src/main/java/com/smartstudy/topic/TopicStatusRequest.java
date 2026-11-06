package com.smartstudy.topic;

import jakarta.validation.constraints.NotNull;

/** PATCH /api/v1/topics/{id}/status  { "status": "COMPLETED" } */
public record TopicStatusRequest(@NotNull TopicStatus status) {
}
