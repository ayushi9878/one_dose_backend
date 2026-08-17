package com.careflow.risk;

import com.careflow.common.enums.RiskLevel;
import com.careflow.common.enums.RiskSignalCode;
import com.careflow.config.CareFlowProperties;
import com.careflow.risk.dto.RiskEvaluationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Risk evaluation rules engine")
class RiskEvaluationServiceTest {

    private RiskEvaluationService riskEvaluationService;

    @BeforeEach
    void setUp() {
        CareFlowProperties properties = new CareFlowProperties();
        properties.getRisk().setMissedDoseThreshold(3);
        properties.getAdherence().setLowThresholdPercentage(80);
        riskEvaluationService = new RiskEvaluationService(properties);
    }

    private RiskEvaluationInput.RiskEvaluationInputBuilder healthyResponse() {
        return RiskEvaluationInput.builder()
                .medicationTaken(true)
                .missedDoses(0)
                .symptomsReported(false)
                .symptomCount(0)
                .refillNeeded(false)
                .adherencePercentage(95.0);
    }

    @Test
    @DisplayName("a clean response raises no signals and needs no review")
    void cleanResponseProducesNoSignals() {
        RiskEvaluationResult result = riskEvaluationService.evaluate(healthyResponse().build());

        assertThat(result.riskLevel()).isEqualTo(RiskLevel.NONE);
        assertThat(result.signals()).isEmpty();
        assertThat(result.requiresHumanReview()).isFalse();
    }

    @Nested
    @DisplayName("individual rules")
    class IndividualRules {

        @Test
        @DisplayName("missed doses at the threshold raise MEDIUM risk")
        void missedDosesAtThresholdRaiseMedium() {
            RiskEvaluationResult result = riskEvaluationService.evaluate(
                    healthyResponse().missedDoses(3).build());

            assertThat(result.signals()).containsExactly(RiskSignalCode.MULTIPLE_MISSED_DOSES);
            assertThat(result.riskLevel()).isEqualTo(RiskLevel.MEDIUM);
        }

        @Test
        @DisplayName("missed doses below the threshold raise nothing")
        void missedDosesBelowThresholdRaiseNothing() {
            RiskEvaluationResult result = riskEvaluationService.evaluate(
                    healthyResponse().missedDoses(2).build());

            assertThat(result.signals()).isEmpty();
            assertThat(result.riskLevel()).isEqualTo(RiskLevel.NONE);
        }

        @Test
        @DisplayName("reported symptoms raise HIGH risk on their own")
        void symptomsRaiseHigh() {
            RiskEvaluationResult result = riskEvaluationService.evaluate(
                    healthyResponse().symptomsReported(true).symptomCount(1).build());

            assertThat(result.signals()).containsExactly(RiskSignalCode.SYMPTOMS_REPORTED);
            assertThat(result.riskLevel()).isEqualTo(RiskLevel.HIGH);
            assertThat(result.requiresHumanReview()).isTrue();
        }

        @Test
        @DisplayName("a needed refill raises MEDIUM risk")
        void refillRaisesMedium() {
            RiskEvaluationResult result = riskEvaluationService.evaluate(
                    healthyResponse().refillNeeded(true).build());

            assertThat(result.signals()).containsExactly(RiskSignalCode.REFILL_NEEDED);
            assertThat(result.riskLevel()).isEqualTo(RiskLevel.MEDIUM);
        }

        @Test
        @DisplayName("medication not taken raises MEDIUM risk")
        void medicationNotTakenRaisesMedium() {
            RiskEvaluationResult result = riskEvaluationService.evaluate(
                    healthyResponse().medicationTaken(false).build());

            assertThat(result.signals()).contains(RiskSignalCode.MEDICATION_NOT_TAKEN);
            assertThat(result.riskLevel()).isEqualTo(RiskLevel.MEDIUM);
        }

        @Test
        @DisplayName("adherence below the configured threshold raises MEDIUM risk")
        void lowAdherenceRaisesMedium() {
            RiskEvaluationResult result = riskEvaluationService.evaluate(
                    healthyResponse().adherencePercentage(79.9).build());

            assertThat(result.signals()).containsExactly(RiskSignalCode.LOW_ADHERENCE);
        }

        @Test
        @DisplayName("a patient with no dose history is not treated as low adherence")
        void nullAdherenceIsNotASignal() {
            RiskEvaluationResult result = riskEvaluationService.evaluate(
                    healthyResponse().adherencePercentage(null).build());

            assertThat(result.signals()).isEmpty();
        }
    }

    @Nested
    @DisplayName("combined rules")
    class CombinedRules {

        @Test
        @DisplayName("missed doses plus symptoms yields HIGH risk and human review")
        void missedDosesAndSymptomsYieldHigh() {
            RiskEvaluationResult result = riskEvaluationService.evaluate(
                    healthyResponse()
                            .missedDoses(3)
                            .symptomsReported(true)
                            .symptomCount(1)
                            .build());

            assertThat(result.signals()).containsExactlyInAnyOrder(
                    RiskSignalCode.MULTIPLE_MISSED_DOSES, RiskSignalCode.SYMPTOMS_REPORTED);
            assertThat(result.riskLevel()).isEqualTo(RiskLevel.HIGH);
            assertThat(result.requiresHumanReview()).isTrue();
        }

        @Test
        @DisplayName("two medium signals require review even without a high signal")
        void twoMediumSignalsRequireReview() {
            RiskEvaluationResult result = riskEvaluationService.evaluate(
                    healthyResponse().missedDoses(4).refillNeeded(true).build());

            assertThat(result.riskLevel()).isEqualTo(RiskLevel.MEDIUM);
            assertThat(result.requiresHumanReview()).isTrue();
        }

        @Test
        @DisplayName("a single medium signal does not require review")
        void singleMediumSignalDoesNotRequireReview() {
            RiskEvaluationResult result = riskEvaluationService.evaluate(
                    healthyResponse().refillNeeded(true).build());

            assertThat(result.requiresHumanReview()).isFalse();
        }
    }

    @Test
    @DisplayName("the engine is deterministic for identical input")
    void evaluationIsDeterministic() {
        RiskEvaluationInput input = healthyResponse()
                .missedDoses(5).symptomsReported(true).refillNeeded(true).build();

        RiskEvaluationResult first = riskEvaluationService.evaluate(input);
        RiskEvaluationResult second = riskEvaluationService.evaluate(input);

        assertThat(first).isEqualTo(second);
    }

    @Test
    @DisplayName("the summary never asserts a diagnosis")
    void summaryStaysOperational() {
        RiskEvaluationResult result = riskEvaluationService.evaluate(
                healthyResponse().symptomsReported(true).symptomCount(2).build());

        assertThat(result.summary())
                .contains("routed to a care manager")
                .doesNotContainIgnoringCase("diagnos");
    }

    @Test
    @DisplayName("the configured missed-dose threshold is honoured")
    void thresholdIsConfigurable() {
        CareFlowProperties strict = new CareFlowProperties();
        strict.getRisk().setMissedDoseThreshold(1);
        RiskEvaluationService strictService = new RiskEvaluationService(strict);

        RiskEvaluationResult result = strictService.evaluate(healthyResponse().missedDoses(1).build());

        assertThat(result.signals()).contains(RiskSignalCode.MULTIPLE_MISSED_DOSES);
    }
}
