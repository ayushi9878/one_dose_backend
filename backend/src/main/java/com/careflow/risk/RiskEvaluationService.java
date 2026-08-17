package com.careflow.risk;

import com.careflow.common.enums.RiskLevel;
import com.careflow.common.enums.RiskSignalCode;
import com.careflow.config.CareFlowProperties;
import com.careflow.risk.dto.RiskEvaluationResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic operational rules engine.
 *
 * <p>This engine performs no clinical reasoning. It inspects self-reported
 * workflow facts and decides whether a case warrants review by a human care
 * manager. It never produces a diagnosis, a severity of illness, or a treatment
 * recommendation, and it must remain a pure function of its input so that every
 * escalation is reproducible and explainable during audit.
 *
 * <p>Rules:
 * <ul>
 *   <li>missed doses at or above the configured threshold → MEDIUM</li>
 *   <li>symptoms self-reported → HIGH</li>
 *   <li>refill needed → MEDIUM</li>
 *   <li>medication not taken → MEDIUM</li>
 *   <li>calculated adherence below the configured threshold → MEDIUM</li>
 * </ul>
 * The resulting level is the highest level among the signals raised. Any HIGH
 * signal, or two or more concurrent signals, requires human review.
 */
@Service
@RequiredArgsConstructor
public class RiskEvaluationService {

    private final CareFlowProperties properties;

    public RiskEvaluationResult evaluate(RiskEvaluationInput input) {
        List<RiskSignalCode> signals = new ArrayList<>();

        if (input.missedDoses() >= properties.getRisk().getMissedDoseThreshold()) {
            signals.add(RiskSignalCode.MULTIPLE_MISSED_DOSES);
        }
        if (input.symptomsReported()) {
            signals.add(RiskSignalCode.SYMPTOMS_REPORTED);
        }
        if (input.refillNeeded()) {
            signals.add(RiskSignalCode.REFILL_NEEDED);
        }
        if (!input.medicationTaken()) {
            signals.add(RiskSignalCode.MEDICATION_NOT_TAKEN);
        }
        if (input.adherencePercentage() != null
                && input.adherencePercentage() < properties.getAdherence().getLowThresholdPercentage()) {
            signals.add(RiskSignalCode.LOW_ADHERENCE);
        }

        RiskLevel riskLevel = signals.stream()
                .map(RiskSignalCode::getDefaultLevel)
                .reduce(RiskLevel.NONE, RiskLevel::escalateTo);

        boolean requiresHumanReview = riskLevel.isAtLeast(RiskLevel.HIGH) || signals.size() >= 2;

        return new RiskEvaluationResult(
                riskLevel,
                List.copyOf(signals),
                signals.stream().map(RiskSignalCode::getDescription).toList(),
                requiresHumanReview,
                buildSummary(signals, riskLevel, requiresHumanReview));
    }

    private String buildSummary(List<RiskSignalCode> signals, RiskLevel riskLevel,
                                boolean requiresHumanReview) {
        if (signals.isEmpty()) {
            return "No operational risk signals were detected in this response.";
        }
        String joined = String.join(", ", signals.stream().map(RiskSignalCode::getDescription).toList());
        String outcome = requiresHumanReview
                ? " This case has been routed to a care manager for human review."
                : " No escalation was required.";
        return riskLevel + " operational risk. " + joined + outcome;
    }
}
