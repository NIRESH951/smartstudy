package com.smartstudy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Entry point of the SmartStudy REST API.
 *
 * <p>The backend is a modular monolith: every feature (subject, topic, planner,
 * revision, ...) lives in its own package under {@code com.smartstudy} and owns its
 * entities, repositories, DTOs, services and controllers.</p>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class SmartStudyApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartStudyApplication.class, args);
    }
}
