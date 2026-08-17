package com.careflow.medication;

import com.careflow.common.enums.MedicationFrequency;
import com.careflow.medication.dto.MedicationResponse;
import org.springframework.stereotype.Component;

@Component
public class MedicationMapper {

    public MedicationResponse toResponse(Medication medication) {
        return new MedicationResponse(
                medication.getId(),
                medication.getPatient().getId(),
                medication.getMedicineName(),
                medication.getDosage(),
                medication.getFrequency(),
                humanise(medication.getFrequency()),
                medication.getStartDate(),
                medication.getEndDate(),
                medication.getInstructions(),
                medication.isActive(),
                medication.getCreatedAt(),
                medication.getUpdatedAt());
    }

    private String humanise(MedicationFrequency frequency) {
        return switch (frequency) {
            case ONCE_DAILY -> "Once daily";
            case TWICE_DAILY -> "Twice daily";
            case THREE_TIMES_DAILY -> "Three times daily";
            case FOUR_TIMES_DAILY -> "Four times daily";
            case EVERY_OTHER_DAY -> "Every other day";
            case WEEKLY -> "Weekly";
            case AS_NEEDED -> "As needed";
        };
    }
}
