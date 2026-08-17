package com.careflow.ai;

import com.careflow.config.CareFlowProperties;
import com.careflow.escalation.Escalation;
import com.careflow.risk.RiskSignal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Produces a short, human-readable briefing of an escalation for the care
 * manager who picks it up.
 *
 * <p>This is a convenience layer over facts the platform already holds. It never
 * influences the risk decision, never diagnoses, and never recommends treatment
 * — those remain the deterministic engine's and the care manager's job
 * respectively. When Groq is not configured the deterministic template below is
 * used, so the feature degrades cleanly rather than failing the request.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CaseSummaryService {

    private final CareFlowProperties properties;
    private final GroqClient groqClient;

    public CaseSummary summarise(Escalation escalation, List<RiskSignal> signals) {
        String deterministic = deterministicSummary(escalation, signals);

        if (!properties.getAi().isUsable()) {
            return new CaseSummary(deterministic, "template");
        }

        return groqClient.summarise(buildPrompt(escalation, signals))
                .map(text -> new CaseSummary(text, "groq:" + properties.getAi().getModel()))
                .orElseGet(() -> new CaseSummary(deterministic, "template"));
    }

    private String deterministicSummary(Escalation escalation, List<RiskSignal> signals) {
        String signalText = signals.isEmpty()
                ? "No specific operational signals were recorded."
                : signals.stream().map(signal -> signal.getCode().name()).distinct()
                        .reduce((a, b) -> a + ", " + b).orElse("");

        return """
                %s severity case for %s.
                Reason: %s
                Operational signals: %s
                Next step: a care manager should contact the patient to confirm the situation \
                and decide on appropriate action.""".formatted(
                escalation.getSeverity(),
                escalation.getPatient().getFullName(),
                escalation.getReason(),
                signalText);
    }

    /**
     * The prompt deliberately withholds direct identifiers and instructs the
     * model to stay operational, so the generated text cannot drift into
     * clinical advice.
     */
    private String buildPrompt(Escalation escalation, List<RiskSignal> signals) {
        String signalText = signals.stream()
                .map(signal -> "- " + signal.getCode().name() + ": " + signal.getDetail())
                .reduce((a, b) -> a + "\n" + b)
                .orElse("- none recorded");

        return """
                You are assisting a care operations team. Summarise the following case for the \
                care manager who will review it, in at most three sentences.

                Rules you must follow:
                - Do not diagnose, suggest a diagnosis, or recommend any treatment or medication change.
                - Describe only the operational situation and what the care manager should verify.
                - Do not invent facts that are not listed below.

                Severity: %s
                Reason recorded by the rules engine: %s
                Operational signals:
                %s
                """.formatted(escalation.getSeverity(), escalation.getReason(), signalText);
    }

    public record CaseSummary(String summary, String source) {
    }
}
