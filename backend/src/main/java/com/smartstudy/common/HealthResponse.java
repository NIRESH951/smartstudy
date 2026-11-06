package com.smartstudy.common;

import java.time.Instant;

public record HealthResponse(
        String status,
        String application,
        String version,
        Instant timestamp,
        DatabaseHealth database) {

    public record DatabaseHealth(
            String status,
            String product,
            String productVersion,
            String databaseName,
            String schemaVersion,
            String message) {
    }
}
