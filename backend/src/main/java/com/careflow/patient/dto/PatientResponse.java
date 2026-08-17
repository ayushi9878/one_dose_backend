package com.careflow.patient.dto;

import com.careflow.common.enums.RiskLevel;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;

@Schema(description = "Patient summary returned by list and detail endpoints.")
public record PatientResponse(
        Long id,
        String medicalRecordNumber,
        String firstName,
        String lastName,
        String fullName,
        LocalDate dateOfBirth,
        int age,
        String phone,
        String email,
        String primaryCondition,
        LocalDate dischargeDate,
        RiskLevel currentRiskLevel,
        boolean active,
        Long careManagerId,
        String careManagerName,
        Instant createdAt,
        Instant updatedAt) {
}
