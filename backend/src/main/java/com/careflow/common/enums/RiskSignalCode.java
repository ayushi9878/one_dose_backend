package com.careflow.common.enums;

import lombok.Getter;

/**
 * Operational signals emitted by the rules engine. Each code states an observed
 * workflow fact — never an inferred clinical condition.
 */
@Getter
public enum RiskSignalCode {

    MULTIPLE_MISSED_DOSES(RiskLevel.MEDIUM, "Patient reported three or more missed doses."),
    SYMPTOMS_REPORTED(RiskLevel.HIGH, "Patient self-reported one or more symptoms."),
    REFILL_NEEDED(RiskLevel.MEDIUM, "Patient indicated a medication refill is required."),
    MEDICATION_NOT_TAKEN(RiskLevel.MEDIUM, "Patient reported not taking prescribed medication."),
    LOW_ADHERENCE(RiskLevel.MEDIUM, "Calculated adherence fell below the acceptable threshold."),
    FOLLOW_UP_MISSED(RiskLevel.LOW, "A scheduled follow-up was not completed on time.");

    private final RiskLevel defaultLevel;
    private final String description;

    RiskSignalCode(RiskLevel defaultLevel, String description) {
        this.defaultLevel = defaultLevel;
        this.description = description;
    }
}
