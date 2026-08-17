package com.careflow.medication.dto;

import com.careflow.common.enums.MedicationFrequency;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;

@Schema(description = "A medication prescribed to a patient.")
public record MedicationResponse(
        Long id,
        Long patientId,
        String medicineName,
        String dosage,
        MedicationFrequency frequency,
        String frequencyLabel,
        LocalDate startDate,
        LocalDate endDate,
        String instructions,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {
}
