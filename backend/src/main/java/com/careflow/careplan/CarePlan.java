package com.careflow.careplan;

import com.careflow.common.BaseEntity;
import com.careflow.common.enums.CarePlanStatus;
import com.careflow.common.enums.CarePlanType;
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
@Table(name = "care_plans", indexes = {
        @Index(name = "idx_care_plans_patient", columnList = "patient_id"),
        @Index(name = "idx_care_plans_status", columnList = "status")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CarePlan extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan_type", nullable = false, length = 32)
    private CarePlanType planType;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CarePlanStatus status = CarePlanStatus.ACTIVE;

    @Column(length = 1000)
    private String notes;

    public boolean isActive() {
        return status == CarePlanStatus.ACTIVE;
    }
}
