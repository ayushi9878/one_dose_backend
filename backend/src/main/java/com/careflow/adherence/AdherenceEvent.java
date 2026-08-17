package com.careflow.adherence;

import com.careflow.common.BaseEntity;
import com.careflow.followup.FollowUpTask;
import com.careflow.patient.Patient;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

import java.time.LocalDate;

/**
 * A point-in-time adherence observation derived from a patient response.
 * Aggregating these yields the reported adherence figures.
 */
@Entity
@Table(name = "adherence_events", indexes = {
        @Index(name = "idx_adherence_patient", columnList = "patient_id"),
        @Index(name = "idx_adherence_recorded", columnList = "patient_id, recorded_date")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdherenceEvent extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "follow_up_task_id")
    private FollowUpTask followUpTask;

    @Column(name = "recorded_date", nullable = false)
    private LocalDate recordedDate;

    @Column(name = "expected_doses", nullable = false)
    private int expectedDoses;

    @Column(name = "taken_doses", nullable = false)
    private int takenDoses;

    @Column(name = "missed_doses", nullable = false)
    private int missedDoses;
}
