package com.careflow.escalation.dto;

import com.careflow.common.enums.EscalationSeverity;
import com.careflow.common.enums.EscalationStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "A case routed to a human care manager for review.")
public record EscalationResponse(
        Long id,
        Long patientId,
        String patientName,
        String medicalRecordNumber,
        EscalationSeverity severity,
        String reason,
        EscalationStatus status,
        Long assignedCareManagerId,
        String assignedCareManagerName,
        Instant assignedAt,
        Instant resolvedAt,
        String resolvedByName,
        String resolutionNotes,
        boolean requiresHumanReview,
        Instant createdAt) {
}
