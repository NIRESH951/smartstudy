package com.smartstudy.common;

import java.time.Instant;
import java.util.List;

/**
 * Uniform error body returned by every endpoint.
 *
 * <pre>
 * {
 *   "timestamp": "2026-10-31T05:30:00Z",
 *   "status": 400,
 *   "error": "Bad Request",
 *   "code": "VALIDATION_FAILED",
 *   "message": "Please correct the highlighted fields.",
 *   "path": "/api/v1/subjects",
 *   "fieldErrors": [ { "field": "name", "message": "must not be blank" } ]
 * }
 * </pre>
 */
public record ApiErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String code,
        String message,
        String path,
        List<FieldErrorDetail> fieldErrors) {

    public record FieldErrorDetail(String field, String message) {
    }
}
