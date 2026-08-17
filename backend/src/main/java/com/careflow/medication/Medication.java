package com.careflow.medication;

import com.careflow.common.BaseEntity;
import com.careflow.common.enums.MedicationFrequency;
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

import java.time.LocalDate;

@Entity
@Table(name = "medications", indexes = {
        @Index(name = "idx_medications_patient", columnList = "patient_id"),
        @Index(name = "idx_medications_active", columnList = "patient_id, active")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Medication extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @Column(name = "medicine_name", nullable = false, length = 160)
    private String medicineName;

    @Column(nullable = false, length = 80)
    private String dosage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private MedicationFrequency frequency;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(length = 500)
    private String instructions;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    /**
     * Days this medication was scheduled within the window, clamped to the
     * medication's own start/end dates.
     */
    public long activeDaysWithin(LocalDate windowStart, LocalDate windowEnd) {
        LocalDate from = startDate.isAfter(windowStart) ? startDate : windowStart;
        LocalDate to = (endDate != null && endDate.isBefore(windowEnd)) ? endDate : windowEnd;
        if (from.isAfter(to)) {
            return 0;
        }
        return java.time.temporal.ChronoUnit.DAYS.between(from, to) + 1;
    }
}
