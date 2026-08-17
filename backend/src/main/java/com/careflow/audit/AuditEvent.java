package com.careflow.audit;

import com.careflow.common.BaseEntity;
import com.careflow.common.enums.AuditAction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Append-only record of a meaningful workflow action. Rows are never updated or
 * deleted, so the actor and patient are stored as plain identifiers rather than
 * associations that could cascade.
 */
@Entity
@Table(name = "audit_events", indexes = {
        @Index(name = "idx_audit_patient", columnList = "patient_id, created_at"),
        @Index(name = "idx_audit_action", columnList = "action"),
        @Index(name = "idx_audit_actor", columnList = "actor_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditEvent extends BaseEntity {

    @Column(name = "patient_id")
    private Long patientId;

    @Column(name = "actor_id")
    private Long actorId;

    @Column(name = "actor_name", length = 120)
    private String actorName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private AuditAction action;

    @Column(nullable = false, length = 500)
    private String description;

    /** Small JSON payload of non-sensitive contextual values. */
    @Column(columnDefinition = "TEXT")
    private String metadata;
}
