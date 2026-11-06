package com.smartstudy.planner;

import jakarta.validation.constraints.NotNull;

/** PATCH /api/v1/sessions/{id}/lock  { "locked": true } */
public record LockSessionRequest(@NotNull Boolean locked) {
}
