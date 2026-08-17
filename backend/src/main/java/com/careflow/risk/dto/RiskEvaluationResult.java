package com.careflow.risk.dto;

import com.careflow.common.enums.RiskLevel;
import com.careflow.common.enums.RiskSignalCode;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = """
        Outcome of the deterministic operational rules engine.

        These signals describe workflow conditions only — they are not a clinical
        assessment. When requiresHumanReview is true the case is routed to a
        qualified care manager, who makes every care decision.
        """)
public record RiskEvaluationResult(

        @Schema(example = "HIGH")
        RiskLevel riskLevel,

        @Schema(example = "[\"MULTIPLE_MISSED_DOSES\", \"SYMPTOMS_REPORTED\"]")
        List<RiskSignalCode> signals,

        List<String> signalDescriptions,

        @Schema(description = "True when the case must be reviewed by a human care manager.")
        boolean requiresHumanReview,

        @Schema(description = "Plain-language summary of why the case was flagged.")
        String summary) {

    public boolean hasSignals() {
        return signals != null && !signals.isEmpty();
    }
}
