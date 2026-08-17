package com.careflow.careplan.dto;

import com.careflow.common.enums.CarePlanStatus;
import com.careflow.common.enums.CarePlanType;
import com.careflow.common.validation.ValidDateRange;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

@ValidDateRange
@Schema(description = "Payload for creating or updating a care plan.")
public record CarePlanRequest(

        @NotNull(message = "Start date is required.")
        @JsonFormat(pattern = "yyyy-MM-dd")
        LocalDate startDate,

        @JsonFormat(pattern = "yyyy-MM-dd")
        LocalDate endDate,

        @NotNull(message = "Plan type is required.")
        CarePlanType planType,

        @Schema(description = "Defaults to ACTIVE when omitted on creation.")
        CarePlanStatus status,

        @Size(max = 1000)
        String notes) {
}
