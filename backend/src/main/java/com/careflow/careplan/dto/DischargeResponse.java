package com.careflow.careplan.dto;

import com.careflow.followup.dto.FollowUpResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

@Schema(description = "Result of a discharge, including the generated follow-up schedule.")
public record DischargeResponse(
        Long patientId,
        String patientName,
        LocalDate dischargeDate,
        CarePlanResponse carePlan,
        int followUpsCreated,
        List<FollowUpResponse> followUps) {
}
