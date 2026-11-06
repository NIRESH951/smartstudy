package com.smartstudy.common;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;

import javax.sql.DataSource;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public health check used by the frontend start page and during setup:
 * proves that the API is running, MySQL is reachable and Flyway has migrated the schema.
 *
 * <pre>
 * GET /api/v1/health  ->  200
 * {
 *   "status": "UP",
 *   "application": "SmartStudy API",
 *   "version": "1.0.0",
 *   "timestamp": "2026-10-31T05:30:00Z",
 *   "database": { "status": "UP", "product": "MySQL", "productVersion": "8.4.6",
 *                 "databaseName": "smartstudy", "schemaVersion": "1", "message": null }
 * }
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public HealthController(DataSource dataSource, JdbcTemplate jdbcTemplate, Clock clock) {
        this.dataSource = dataSource;
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    @GetMapping
    public ResponseEntity<HealthResponse> health() {
        HealthResponse.DatabaseHealth database;
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            boolean valid = connection.isValid(2);
            database = new HealthResponse.DatabaseHealth(
                    valid ? "UP" : "DOWN",
                    metaData.getDatabaseProductName(),
                    metaData.getDatabaseProductVersion(),
                    connection.getCatalog(),
                    findSchemaVersion(),
                    valid ? null : "Connection validation failed");
        } catch (SQLException | DataAccessException ex) {
            database = new HealthResponse.DatabaseHealth("DOWN", null, null, null, null,
                    "Cannot connect to the database. Check DB_URL, DB_USERNAME and DB_PASSWORD.");
        }
        boolean up = "UP".equals(database.status());
        HealthResponse body = new HealthResponse(up ? "UP" : "DOWN", "SmartStudy API", "1.0.0",
                Instant.now(clock), database);
        return ResponseEntity.status(up ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }

    /** Latest successfully applied Flyway migration, or null when Flyway is not in use (tests). */
    private String findSchemaVersion() {
        try {
            ResultSetExtractor<String> firstVersion = resultSet -> resultSet.next() ? resultSet.getString(1) : null;
            return jdbcTemplate.query("SELECT version FROM flyway_schema_history "
                    + "WHERE success = TRUE AND version IS NOT NULL ORDER BY installed_rank DESC", firstVersion);
        } catch (DataAccessException ex) {
            return null;
        }
    }
}
