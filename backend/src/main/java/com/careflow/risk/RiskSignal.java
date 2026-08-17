package com.careflow.risk;

import com.careflow.common.BaseEntity;
import com.careflow.common.enums.RiskLevel;
import com.careflow.common.enums.RiskSignalCode;
import com.careflow.followup.PatientResponse;
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

@Entity
@Table(name = "risk_signals", indexes = {
        @Index(name = "idx_risk_signals_patient", columnList = "patient_id"),
        @Index(name = "idx_risk_signals_level", columnList = "risk_level"),
        @Index(name = "idx_risk_signals_response", columnList = "patient_response_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskSignal extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_response_id")
    private PatientResponse patientResponse;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private RiskSignalCode code;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false, length = 20)
    private RiskLevel riskLevel;

    @Column(nullable = false, length = 300)
    private String detail;

    @Builder.Default
    @Column(name = "requires_human_review", nullable = false)
    private boolean requiresHumanReview = false;
}
