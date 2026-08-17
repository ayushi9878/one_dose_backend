package com.careflow.medication.dto;

import com.careflow.common.enums.MedicationFrequency;
import com.careflow.common.validation.ValidDateRange;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

@ValidDateRange
@Schema(description = "Payload for prescribing or updating a medication.")
public record MedicationRequest(

        @Schema(example = "Metoprolol")
        @NotBlank(message = "Medicine name is required.")
        @Size(max = 160)
        String medicineName,

        @Schema(example = "25 mg")
        @NotBlank(message = "Dosage is required.")
        @Size(max = 80)
        String dosage,

        @NotNull(message = "Frequency is required.")
        MedicationFrequency frequency,

        @NotNull(message = "Start date is required.")
        @JsonFormat(pattern = "yyyy-MM-dd")
        LocalDate startDate,

        @Schema(description = "Leave null for an ongoing prescription.")
        @JsonFormat(pattern = "yyyy-MM-dd")
        LocalDate endDate,

        @Schema(example = "Take with food in the morning.")
        @Size(max = 500)
        String instructions,

        Boolean active) {
}
