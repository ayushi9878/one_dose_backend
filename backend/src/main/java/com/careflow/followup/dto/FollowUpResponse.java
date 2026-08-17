package com.careflow.followup.dto;

import com.careflow.common.enums.FollowUpStatus;
import com.careflow.common.enums.FollowUpType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;

@Schema(description = "A scheduled follow-up touchpoint in the care journey.")
public record FollowUpResponse(
        Long id,
        Long patientId,
        String patientName,
        Long carePlanId,
        LocalDate scheduledDate,
        Instant completedDate,
        FollowUpStatus status,
        FollowUpType type,
        Integer dayOffset,
        String title,
        boolean overdue,
        Instant createdAt) {
}
