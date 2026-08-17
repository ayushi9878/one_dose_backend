package com.careflow.followup.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "A patient's answers to a scheduled follow-up.")
public record PatientResponseRequest(

        @Schema(example = "false", description = "Whether the patient took their medication as prescribed.")
        @NotNull(message = "Indicate whether medication was taken.")
        Boolean medicationTaken,

        @Schema(example = "3")
        @NotNull(message = "Missed dose count is required.")
        @Min(value = 0, message = "Missed doses cannot be negative.")
        @Max(value = 500, message = "Missed doses exceeds a plausible range.")
        Integer missedDoses,

        @Schema(example = "true")
        @NotNull(message = "Indicate whether symptoms were reported.")
        Boolean symptomsReported,

        @Schema(example = "[\"dizziness\"]",
                description = "Free-text symptoms as reported by the patient, recorded verbatim.")
        @Size(max = 20, message = "At most 20 symptoms can be recorded per response.")
        List<@Size(max = 120) String> symptoms,

        @Schema(example = "true")
        @NotNull(message = "Indicate whether a refill is needed.")
        Boolean refillNeeded,

        @Schema(example = "Patient reported feeling dizzy.")
        @Size(max = 1000)
        String notes) {
}
