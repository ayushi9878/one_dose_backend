package com.careflow.patient;

import com.careflow.patient.dto.PatientResponse;
import com.careflow.user.User;
import org.springframework.stereotype.Component;

@Component
public class PatientMapper {

    public PatientResponse toResponse(Patient patient) {
        User careManager = patient.getCareManager();
        return new PatientResponse(
                patient.getId(),
                patient.getMedicalRecordNumber(),
                patient.getFirstName(),
                patient.getLastName(),
                patient.getFullName(),
                patient.getDateOfBirth(),
                patient.getAge(),
                patient.getPhone(),
                patient.getEmail(),
                patient.getPrimaryCondition(),
                patient.getDischargeDate(),
                patient.getCurrentRiskLevel(),
                patient.isActive(),
                careManager != null ? careManager.getId() : null,
                careManager != null ? careManager.getFullName() : null,
                patient.getCreatedAt(),
                patient.getUpdatedAt());
    }
}
