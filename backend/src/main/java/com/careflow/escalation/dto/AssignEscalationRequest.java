package com.careflow.escalation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Assigns an escalation to a care manager for review.")
public record AssignEscalationRequest(

        @Schema(description = "Target care manager. Must hold the CARE_MANAGER or ADMIN role.")
        @NotNull(message = "Care manager id is required.")
        Long careManagerId) {
}
