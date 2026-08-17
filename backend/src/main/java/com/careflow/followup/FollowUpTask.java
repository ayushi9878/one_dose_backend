package com.careflow.followup;

import com.careflow.careplan.CarePlan;
import com.careflow.common.BaseEntity;
import com.careflow.common.enums.FollowUpStatus;
import com.careflow.common.enums.FollowUpType;
import com.careflow.patient.Patient;
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
import java.time.LocalDate;

@Entity
@Table(name = "follow_up_tasks", indexes = {
        @Index(name = "idx_follow_ups_patient", columnList = "patient_id"),
        @Index(name = "idx_follow_ups_care_plan", columnList = "care_plan_id"),
        @Index(name = "idx_follow_ups_scheduled", columnList = "scheduled_date"),
        @Index(name = "idx_follow_ups_status", columnList = "status")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FollowUpTask extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "care_plan_id")
    private CarePlan carePlan;

    @Column(name = "scheduled_date", nullable = false)
    private LocalDate scheduledDate;

    @Column(name = "completed_date")
    private Instant completedDate;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FollowUpStatus status = FollowUpStatus.SCHEDULED;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private FollowUpType type;

    /** Day offset from discharge, used for the care-journey timeline. */
    @Column(name = "day_offset")
    private Integer dayOffset;

    @Column(length = 200)
    private String title;

    public boolean isOpen() {
        return status == FollowUpStatus.SCHEDULED || status == FollowUpStatus.PENDING;
    }
}
