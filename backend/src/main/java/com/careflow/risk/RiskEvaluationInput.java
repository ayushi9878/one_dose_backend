package com.careflow.risk;

import lombok.Builder;

/**
 * Immutable snapshot of the facts the rules engine is allowed to consider.
 * Keeping this separate from the JPA entities lets the engine stay a pure
 * function of its inputs.
 */
@Builder
public record RiskEvaluationInput(
        boolean medicationTaken,
        int missedDoses,
        boolean symptomsReported,
        int symptomCount,
        boolean refillNeeded,
        Double adherencePercentage) {
}
