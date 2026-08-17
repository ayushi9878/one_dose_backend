package com.careflow.followup;

import com.careflow.common.BaseEntity;
import com.careflow.patient.Patient;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "patient_responses", indexes = {
        @Index(name = "idx_responses_patient", columnList = "patient_id"),
        @Index(name = "idx_responses_follow_up", columnList = "follow_up_task_id", unique = true)
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatientResponse extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "follow_up_task_id", nullable = false, unique = true)
    private FollowUpTask followUpTask;

    @Column(name = "medication_taken", nullable = false)
    private boolean medicationTaken;

    @Builder.Default
    @Column(name = "missed_doses", nullable = false)
    private int missedDoses = 0;

    @Column(name = "symptoms_reported", nullable = false)
    private boolean symptomsReported;

    @Builder.Default
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "patient_response_symptoms",
            joinColumns = @JoinColumn(name = "patient_response_id"))
    @Column(name = "symptom", length = 120)
    private List<String> symptoms = new ArrayList<>();

    @Column(name = "refill_needed", nullable = false)
    private boolean refillNeeded;

    @Column(length = 1000)
    private String notes;
}
