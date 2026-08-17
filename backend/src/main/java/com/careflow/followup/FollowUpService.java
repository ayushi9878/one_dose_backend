package com.careflow.followup;

import com.careflow.adherence.AdherenceService;
import com.careflow.audit.AuditService;
import com.careflow.common.PageResponse;
import com.careflow.common.enums.AuditAction;
import com.careflow.common.enums.FollowUpStatus;
import com.careflow.escalation.Escalation;
import com.careflow.escalation.EscalationMapper;
import com.careflow.escalation.EscalationService;
import com.careflow.exception.BusinessRuleException;
import com.careflow.exception.ResourceNotFoundException;
import com.careflow.followup.dto.FollowUpResponse;
import com.careflow.followup.dto.PatientResponseRequest;
import com.careflow.followup.dto.PatientResponseResult;
import com.careflow.patient.Patient;
import com.careflow.patient.PatientService;
import com.careflow.risk.RiskEvaluationInput;
import com.careflow.risk.RiskEvaluationService;
import com.careflow.risk.RiskSignal;
import com.careflow.risk.RiskSignalRepository;
import com.careflow.risk.dto.RiskEvaluationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class FollowUpService {

    private final FollowUpTaskRepository followUpTaskRepository;
    private final PatientResponseRepository patientResponseRepository;
    private final RiskSignalRepository riskSignalRepository;
    private final FollowUpMapper followUpMapper;
    private final PatientService patientService;
    private final AdherenceService adherenceService;
    private final RiskEvaluationService riskEvaluationService;
    private final EscalationService escalationService;
    private final EscalationMapper escalationMapper;
    private final AuditService auditService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<FollowUpResponse> listForPatient(Long patientId) {
        patientService.requireAccessiblePatient(patientId);
        return followUpTaskRepository.findByPatientIdOrderByScheduledDateAsc(patientId)
                .stream()
                .map(followUpMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public FollowUpResponse getById(Long id) {
        return followUpMapper.toResponse(requireAccessibleTask(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<FollowUpResponse> search(FollowUpStatus status, LocalDate from, LocalDate to,
                                                 Pageable pageable) {
        return PageResponse.from(
                followUpTaskRepository.searchWithPatient(status, from, to, pageable),
                followUpMapper::toResponse);
    }

    /**
     * Records a patient's follow-up answers and runs the downstream workflow in
     * one transaction: store the response, derive an adherence event, evaluate
     * operational risk, persist the resulting signals, and open a case for human
     * review when the rules require it.
     *
     * <p>Everything commits together, so an escalation can never reference a
     * response that was not saved, and a stored response can never silently skip
     * its risk evaluation.
     */
    @Transactional
    public PatientResponseResult submitResponse(Long followUpId, PatientResponseRequest request) {
        FollowUpTask followUpTask = requireAccessibleTask(followUpId);
        Patient patient = followUpTask.getPatient();

        if (followUpTask.getStatus() == FollowUpStatus.COMPLETED
                || patientResponseRepository.existsByFollowUpTaskId(followUpId)) {
            throw new BusinessRuleException(
                    "A response has already been recorded for follow-up " + followUpId + ".");
        }

        PatientResponse response = patientResponseRepository.save(PatientResponse.builder()
                .patient(patient)
                .followUpTask(followUpTask)
                .medicationTaken(request.medicationTaken())
                .missedDoses(request.missedDoses())
                .symptomsReported(request.symptomsReported())
                .symptoms(request.symptoms() == null ? List.of() : List.copyOf(request.symptoms()))
                .refillNeeded(request.refillNeeded())
                .notes(request.notes())
                .build());

        followUpTask.setStatus(FollowUpStatus.COMPLETED);
        followUpTask.setCompletedDate(Instant.now(clock));

        auditService.record(AuditAction.RESPONSE_RECEIVED, patient.getId(),
                "Follow-up response received for day " + followUpTask.getDayOffset() + " check-in.",
                Map.of("followUpId", followUpId, "responseId", response.getId()));

        adherenceService.recordFromResponse(patient, followUpTask,
                request.medicationTaken(), request.missedDoses());

        RiskEvaluationResult evaluation = riskEvaluationService.evaluate(RiskEvaluationInput.builder()
                .medicationTaken(request.medicationTaken())
                .missedDoses(request.missedDoses())
                .symptomsReported(request.symptomsReported())
                .symptomCount(request.symptoms() == null ? 0 : request.symptoms().size())
                .refillNeeded(request.refillNeeded())
                .adherencePercentage(adherenceService.currentPercentage(patient.getId()))
                .build());

        persistSignals(patient, response, evaluation);

        Escalation escalation = null;
        if (evaluation.requiresHumanReview()) {
            escalation = escalationService.createFromRiskEvaluation(patient, response, evaluation);
            followUpTask.setStatus(FollowUpStatus.ESCALATED);
        }

        patient.setCurrentRiskLevel(evaluation.riskLevel());

        auditService.record(AuditAction.FOLLOW_UP_COMPLETED, patient.getId(),
                "Day " + followUpTask.getDayOffset() + " follow-up completed.",
                Map.of("followUpId", followUpId, "riskLevel", evaluation.riskLevel().name()));

        log.info("Processed follow-up id={} patient id={} risk={} escalated={}",
                followUpId, patient.getId(), evaluation.riskLevel(), escalation != null);

        return new PatientResponseResult(
                response.getId(),
                followUpMapper.toResponse(followUpTask),
                evaluation,
                adherenceService.getForPatient(patient.getId()),
                escalation != null ? escalationMapper.toResponse(escalation) : null);
    }

    /**
     * Flags open follow-ups whose scheduled date has passed as MISSED. Invoked by
     * the scheduled sweep so the dashboard reflects reality without a human
     * having to close them by hand.
     */
    @Transactional
    public int markOverdueAsMissed() {
        List<FollowUpTask> overdue = followUpTaskRepository.findByScheduledDateBeforeAndStatusIn(
                LocalDate.now(clock),
                List.of(FollowUpStatus.SCHEDULED, FollowUpStatus.PENDING));

        for (FollowUpTask task : overdue) {
            task.setStatus(FollowUpStatus.MISSED);
            auditService.recordSystemAction(AuditAction.FOLLOW_UP_MISSED, task.getPatient().getId(),
                    "Day " + task.getDayOffset() + " follow-up was not completed by "
                            + task.getScheduledDate() + ".",
                    Map.of("followUpId", task.getId()));
        }
        if (!overdue.isEmpty()) {
            log.info("Marked {} overdue follow-ups as missed", overdue.size());
        }
        return overdue.size();
    }

    private void persistSignals(Patient patient, PatientResponse response,
                                RiskEvaluationResult evaluation) {
        evaluation.signals().forEach(code -> {
            RiskSignal signal = riskSignalRepository.save(RiskSignal.builder()
                    .patient(patient)
                    .patientResponse(response)
                    .code(code)
                    .riskLevel(code.getDefaultLevel())
                    .detail(code.getDescription())
                    .requiresHumanReview(evaluation.requiresHumanReview())
                    .build());

            auditService.record(AuditAction.RISK_SIGNAL_CREATED, patient.getId(),
                    "Operational signal raised: " + code.name() + ".",
                    Map.of("riskSignalId", signal.getId(), "riskLevel", code.getDefaultLevel().name()));
        });
    }

    private FollowUpTask requireAccessibleTask(Long followUpId) {
        FollowUpTask task = followUpTaskRepository.findById(followUpId)
                .orElseThrow(() -> ResourceNotFoundException.of("Follow-up", followUpId));
        patientService.requireAccessiblePatient(task.getPatient().getId());
        return task;
    }
}
