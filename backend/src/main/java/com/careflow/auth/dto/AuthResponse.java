package com.careflow.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Issued access token and the identity it represents.")
public record AuthResponse(

        @Schema(description = "JWT to send as 'Authorization: Bearer <token>'.")
        String accessToken,

        @Schema(example = "Bearer")
        String tokenType,

        @Schema(description = "Token lifetime in seconds.", example = "28800")
        long expiresIn,

        UserProfile user) {
}
