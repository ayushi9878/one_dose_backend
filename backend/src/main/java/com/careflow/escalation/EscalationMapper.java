package com.careflow.escalation;

import com.careflow.common.enums.EscalationStatus;
import com.careflow.escalation.dto.EscalationResponse;
import com.careflow.user.User;
import org.springframework.stereotype.Component;

@Component
public class EscalationMapper {

    public EscalationResponse toResponse(Escalation escalation) {
        User assignee = escalation.getAssignedCareManager();
        User resolver = escalation.getResolvedBy();
        return new EscalationResponse(
                escalation.getId(),
                escalation.getPatient().getId(),
                escalation.getPatient().getFullName(),
                escalation.getPatient().getMedicalRecordNumber(),
                escalation.getSeverity(),
                escalation.getReason(),
                escalation.getStatus(),
                assignee != null ? assignee.getId() : null,
                assignee != null ? assignee.getFullName() : null,
                escalation.getAssignedAt(),
                escalation.getResolvedAt(),
                resolver != null ? resolver.getFullName() : null,
                escalation.getResolutionNotes(),
                escalation.getStatus() != EscalationStatus.RESOLVED,
                escalation.getCreatedAt());
    }
}
