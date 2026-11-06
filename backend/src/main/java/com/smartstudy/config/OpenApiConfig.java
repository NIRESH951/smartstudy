package com.smartstudy.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI document served at /v3/api-docs and rendered by Swagger UI at /swagger-ui.html.
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    @Bean
    public OpenAPI smartStudyOpenApi() {
        return new OpenAPI().info(new Info()
                .title("SmartStudy API")
                .version("v1")
                .description("""
                        REST API of SmartStudy - Student Study Planner and Progress Tracking System.

                        Authentication is session based. Call GET /api/v1/auth/csrf first, then send the
                        returned token in the X-CSRF-TOKEN header with every POST, PUT, PATCH and DELETE
                        request. After POST /api/v1/auth/login the browser keeps the HttpOnly
                        SMARTSTUDY_SESSION cookie.""")
                .license(new License().name("Academic project")));
    }
}
