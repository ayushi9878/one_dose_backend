package com.careflow.patient;

import com.careflow.common.BaseEntity;
import com.careflow.common.enums.RiskLevel;
import com.careflow.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

import java.time.LocalDate;
import java.time.Period;

@Entity
@Table(name = "patients", indexes = {
        @Index(name = "idx_patients_mrn", columnList = "medical_record_number", unique = true),
        @Index(name = "idx_patients_name", columnList = "last_name, first_name"),
        @Index(name = "idx_patients_risk", columnList = "current_risk_level"),
        @Index(name = "idx_patients_care_manager", columnList = "care_manager_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Patient extends BaseEntity {

    @Column(name = "medical_record_number", nullable = false, unique = true, length = 32)
    private String medicalRecordNumber;

    @Column(name = "first_name", nullable = false, length = 80)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 80)
    private String lastName;

    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    @Column(length = 20)
    private String phone;

    @Column(length = 190)
    private String email;

    @Column(name = "primary_condition", length = 200)
    private String primaryCondition;

    @Column(name = "discharge_date")
    private LocalDate dischargeDate;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "current_risk_level", nullable = false, length = 20)
    private RiskLevel currentRiskLevel = RiskLevel.NONE;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    /** Optional login account, present only when the patient uses the portal. */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", unique = true)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "care_manager_id")
    private User careManager;

    public String getFullName() {
        return firstName + " " + lastName;
    }

    public int getAge() {
        return Period.between(dateOfBirth, LocalDate.now()).getYears();
    }
}
