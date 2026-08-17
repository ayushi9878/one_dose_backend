package com.careflow.followup.dto;

import com.careflow.adherence.dto.AdherenceSummary;
import com.careflow.escalation.dto.EscalationResponse;
import com.careflow.risk.dto.RiskEvaluationResult;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = """
        Result of submitting a follow-up response, showing the full chain the
        backend executed: response stored, adherence recalculated, operational
        risk evaluated and — where required — a case opened for human review.
        """)
public record PatientResponseResult(
        Long responseId,
        FollowUpResponse followUp,
        RiskEvaluationResult riskEvaluation,
        AdherenceSummary adherence,

        @Schema(description = "The escalation opened for this case, or null when none was required.")
        EscalationResponse escalation) {
}
