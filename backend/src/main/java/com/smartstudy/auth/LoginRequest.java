package com.smartstudy.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * <pre>
 * POST /api/v1/auth/login
 * { "email": "demo@smartstudy.dev", "password": "Demo@12345" }
 * </pre>
 */
public record LoginRequest(
        @NotBlank @Size(max = 255) String email,
        @NotBlank @Size(max = 72) String password) {
}
