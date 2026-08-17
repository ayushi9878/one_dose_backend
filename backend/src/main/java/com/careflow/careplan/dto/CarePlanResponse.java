package com.careflow.careplan.dto;

import com.careflow.common.enums.CarePlanStatus;
import com.careflow.common.enums.CarePlanType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;

@Schema(description = "A patient's care plan.")
public record CarePlanResponse(
        Long id,
        Long patientId,
        String patientName,
        LocalDate startDate,
        LocalDate endDate,
        CarePlanType planType,
        CarePlanStatus status,
        String notes,
        Instant createdAt,
        Instant updatedAt) {
}
