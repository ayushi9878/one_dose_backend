package com.careflow.auth.dto;

import com.careflow.common.enums.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "The authenticated user's identity. Never contains credentials.")
public record UserProfile(
        Long id,
        String email,
        String fullName,
        UserRole role,

        @Schema(description = "Set when this account is linked to a patient record.")
        Long patientId) {
}
