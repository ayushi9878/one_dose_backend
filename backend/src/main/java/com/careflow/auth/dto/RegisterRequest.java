package com.careflow.auth.dto;

import com.careflow.common.enums.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Creates a new account.")
public record RegisterRequest(

        @NotBlank(message = "Email is required.")
        @Email(message = "Email must be a valid address.")
        @Size(max = 190)
        String email,

        @Schema(description = "At least 10 characters, mixing letters and digits.")
        @NotBlank(message = "Password is required.")
        @Size(min = 10, max = 100, message = "Password must be between 10 and 100 characters.")
        @Pattern(regexp = ".*[A-Za-z].*", message = "Password must contain at least one letter.")
        @Pattern(regexp = ".*\\d.*", message = "Password must contain at least one digit.")
        String password,

        @NotBlank(message = "Full name is required.")
        @Size(max = 120)
        String fullName,

        @Schema(description = "Defaults to CARE_MANAGER. Only an admin may create ADMIN accounts.")
        UserRole role) {
}
