package com.careflow.escalation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Closes an escalation. Resolution notes are mandatory for audit.")
public record ResolveEscalationRequest(

        @Schema(example = "Spoke with the patient, refill arranged and dosing schedule reviewed.")
        @NotBlank(message = "Resolution notes are required to close an escalation.")
        @Size(min = 10, max = 2000,
                message = "Resolution notes must be between 10 and 2000 characters.")
        String resolutionNotes) {
}
