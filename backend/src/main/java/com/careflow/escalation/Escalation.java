package com.careflow.escalation;

import com.careflow.common.BaseEntity;
import com.careflow.common.enums.EscalationSeverity;
import com.careflow.common.enums.EscalationStatus;
import com.careflow.followup.PatientResponse;
import com.careflow.patient.Patient;
import com.careflow.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "escalations", indexes = {
        @Index(name = "idx_escalations_patient", columnList = "patient_id"),
        @Index(name = "idx_escalations_status", columnList = "status"),
        @Index(name = "idx_escalations_severity", columnList = "severity"),
        @Index(name = "idx_escalations_assignee", columnList = "assigned_care_manager_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Escalation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_response_id")
    private PatientResponse patientResponse;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EscalationSeverity severity;

    @Column(nullable = false, length = 500)
    private String reason;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EscalationStatus status = EscalationStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_care_manager_id")
    private User assignedCareManager;

    @Column(name = "assigned_at")
    private Instant assignedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolved_by_id")
    private User resolvedBy;

    @Column(name = "resolution_notes", length = 2000)
    private String resolutionNotes;

    public boolean isResolved() {
        return status == EscalationStatus.RESOLVED;
    }
}
