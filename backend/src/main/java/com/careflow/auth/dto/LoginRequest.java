package com.careflow.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Credentials exchanged for a JWT.")
public record LoginRequest(

        @Schema(example = "care.manager@careflow.health")
        @NotBlank(message = "Email is required.")
        @Email(message = "Email must be a valid address.")
        String email,

        @Schema(example = "CareFlow!2026")
        @NotBlank(message = "Password is required.")
        String password) {
}
