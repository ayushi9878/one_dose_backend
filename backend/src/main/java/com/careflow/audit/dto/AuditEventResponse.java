package com.careflow.audit.dto;

import com.careflow.common.enums.AuditAction;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "An immutable record of a workflow action.")
public record AuditEventResponse(
        Long id,
        Long patientId,
        Long actorId,
        String actorName,
        AuditAction action,
        String description,
        String metadata,
        Instant timestamp) {
}
