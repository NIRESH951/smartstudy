package com.smartstudy.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * <pre>
 * POST /api/v1/auth/register
 * { "fullName": "Asha Verma", "email": "asha@example.com",
 *   "password": "Study@2026", "timeZone": "Asia/Kolkata" }
 * </pre>
 */
public record RegisterRequest(
        @NotBlank @Size(max = 100) String fullName,
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank
        @Size(min = 8, max = 72, message = "must be between 8 and 72 characters")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "must contain at least one letter and one digit")
        String password,
        @Size(max = 64) String timeZone) {
}
