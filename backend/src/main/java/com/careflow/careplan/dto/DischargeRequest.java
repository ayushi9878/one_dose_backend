package com.careflow.careplan.dto;

import com.careflow.common.enums.CarePlanType;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

@Schema(description = "Discharges a patient and generates the follow-up schedule.")
public record DischargeRequest(

        @Schema(example = "2026-08-16")
        @NotNull(message = "Discharge date is required.")
        @JsonFormat(pattern = "yyyy-MM-dd")
        LocalDate dischargeDate,

        @Schema(example = "STANDARD",
                description = "Determines the follow-up cadence: STANDARD schedules days 1/7/14/30, "
                        + "HIGH_RISK adds day 3.")
        @NotNull(message = "Follow-up plan is required.")
        CarePlanType followUpPlan) {
}
