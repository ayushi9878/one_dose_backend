package com.careflow.escalation;

import com.careflow.ai.CaseSummaryService;
import com.careflow.audit.AuditService;
import com.careflow.common.PageResponse;
import com.careflow.common.enums.AuditAction;
import com.careflow.common.enums.EscalationSeverity;
import com.careflow.common.enums.EscalationStatus;
import com.careflow.common.enums.UserRole;
import com.careflow.escalation.dto.AssignEscalationRequest;
import com.careflow.escalation.dto.EscalationResponse;
import com.careflow.escalation.dto.ResolveEscalationRequest;
import com.careflow.exception.BusinessRuleException;
import com.careflow.exception.ResourceNotFoundException;
import com.careflow.followup.PatientResponse;
import com.careflow.patient.Patient;
import com.careflow.risk.RiskSignal;
import com.careflow.risk.RiskSignalRepository;
import com.careflow.risk.dto.RiskEvaluationResult;
import com.careflow.security.CareFlowUserDetails;
import com.careflow.security.CurrentUserProvider;
import com.careflow.user.User;
import com.careflow.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EscalationService {

    private final EscalationRepository escalationRepository;
    private final EscalationMapper escalationMapper;
    private final UserRepository userRepository;
    private final RiskSignalRepository riskSignalRepository;
    private final CaseSummaryService caseSummaryService;
    private final AuditService auditService;
    private final CurrentUserProvider currentUserProvider;
    private final Clock clock;

    /**
     * The escalation queue is deliberately shared: every care manager can see
     * unassigned cases so none goes unowned. {@code assignedToMe} narrows the
     * view to the caller's own queue.
     */
    @Transactional(readOnly = true)
    public PageResponse<EscalationResponse> search(EscalationStatus status, EscalationSeverity severity,
                                                   boolean assignedToMe, Pageable pageable) {
        Long assigneeFilter = assignedToMe ? currentUserProvider.requireId() : null;
        Page<Escalation> page = escalationRepository.search(status, severity, assigneeFilter, pageable);
        return PageResponse.from(page, escalationMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public EscalationResponse getById(Long id) {
        return escalationMapper.toResponse(requireEscalation(id));
    }

    /**
     * Builds an orientation briefing for the reviewing care manager from the
     * case's own recorded signals.
     */
    @Transactional(readOnly = true)
    public CaseSummaryService.CaseSummary summarise(Long escalationId) {
        Escalation escalation = requireEscalation(escalationId);
        List<RiskSignal> signals = escalation.getPatientResponse() != null
                ? riskSignalRepository.findByPatientResponseIdOrderByCreatedAtDesc(
                        escalation.getPatientResponse().getId())
                : riskSignalRepository.findByPatientIdOrderByCreatedAtDesc(escalation.getPatient().getId());
        return caseSummaryService.summarise(escalation, signals);
    }

    @Transactional(readOnly = true)
    public List<EscalationResponse> listForPatient(Long patientId) {
        return escalationRepository.findByPatientIdOrderByCreatedAtDesc(patientId)
                .stream()
                .map(escalationMapper::toResponse)
                .toList();
    }

    /**
     * Opens a case for human review off the back of a risk evaluation. Called by
     * the response workflow inside the caller's transaction so a case and the
     * response that triggered it are always committed together.
     *
     * <p>A patient carries at most one open case, so a fresh signal for someone
     * already escalated updates that case rather than stacking duplicates. The
     * update is never silent: the case is re-pointed at the triggering response,
     * its reason is refreshed, severity is raised if warranted, and an audit
     * entry records the recurrence — otherwise a care manager reviewing the case
     * would have no way to know new evidence had arrived.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Escalation createFromRiskEvaluation(Patient patient, PatientResponse response,
                                               RiskEvaluationResult evaluation) {
        EscalationSeverity severity = EscalationSeverity.fromRiskLevel(evaluation.riskLevel());

        Escalation existing = findOpenEscalation(patient.getId());
        if (existing != null) {
            boolean raised = severity.ordinal() > existing.getSeverity().ordinal();
            if (raised) {
                existing.setSeverity(severity);
            }
            existing.setPatientResponse(response);
            existing.setReason(evaluation.summary());

            // A recurrence on an already-assigned case returns it to the queue:
            // the assignee's earlier review no longer reflects the latest report.
            if (existing.getStatus() == EscalationStatus.IN_REVIEW) {
                existing.setStatus(EscalationStatus.ASSIGNED);
            }

            auditService.record(AuditAction.ESCALATION_UPDATED, patient.getId(),
                    raised
                            ? "Open escalation raised to " + severity + " severity after a new response."
                            : "New " + evaluation.riskLevel() + " signals added to the open escalation.",
                    Map.of("escalationId", existing.getId(),
                            "severity", existing.getSeverity().name(),
                            "signals", evaluation.signals().stream().map(Enum::name).toList()));

            log.info("Updated open escalation id={} for patient id={} severity={} raised={}",
                    existing.getId(), patient.getId(), existing.getSeverity(), raised);
            return existing;
        }

        Escalation escalation = escalationRepository.save(Escalation.builder()
                .patient(patient)
                .patientResponse(response)
                .severity(severity)
                .reason(evaluation.summary())
                .status(EscalationStatus.PENDING)
                .build());

        auditService.record(AuditAction.ESCALATION_CREATED, patient.getId(),
                severity + " escalation opened for human review.",
                Map.of("escalationId", escalation.getId(),
                        "signals", evaluation.signals().stream().map(Enum::name).toList()));

        log.info("Opened escalation id={} severity={} for patient id={}",
                escalation.getId(), severity, patient.getId());
        return escalation;
    }

    @Transactional
    public EscalationResponse assign(Long escalationId, AssignEscalationRequest request) {
        Escalation escalation = requireEscalation(escalationId);
        if (escalation.isResolved()) {
            throw new BusinessRuleException("A resolved escalation cannot be reassigned.");
        }

        User careManager = userRepository.findById(request.careManagerId())
                .orElseThrow(() -> ResourceNotFoundException.of("Care manager", request.careManagerId()));
        if (careManager.getRole() != UserRole.CARE_MANAGER && careManager.getRole() != UserRole.ADMIN) {
            throw new BusinessRuleException(
                    "Escalations can only be assigned to a care manager.");
        }

        escalation.setAssignedCareManager(careManager);
        escalation.setAssignedAt(Instant.now(clock));
        escalation.setStatus(EscalationStatus.ASSIGNED);

        auditService.record(AuditAction.ESCALATION_ASSIGNED, escalation.getPatient().getId(),
                "Escalation assigned to " + careManager.getFullName() + ".",
                Map.of("escalationId", escalation.getId(), "careManagerId", careManager.getId()));

        return escalationMapper.toResponse(escalation);
    }

    @Transactional
    public EscalationResponse markInReview(Long escalationId) {
        Escalation escalation = requireEscalation(escalationId);
        if (escalation.isResolved()) {
            throw new BusinessRuleException("A resolved escalation cannot be reopened for review.");
        }
        if (escalation.getAssignedCareManager() == null) {
            throw new BusinessRuleException(
                    "Assign the escalation to a care manager before marking it in review.");
        }

        escalation.setStatus(EscalationStatus.IN_REVIEW);
        auditService.record(AuditAction.ESCALATION_IN_REVIEW, escalation.getPatient().getId(),
                "Escalation moved to in-review.", Map.of("escalationId", escalation.getId()));

        return escalationMapper.toResponse(escalation);
    }

    @Transactional
    public EscalationResponse resolve(Long escalationId, ResolveEscalationRequest request) {
        Escalation escalation = requireEscalation(escalationId);
        if (escalation.isResolved()) {
            throw new BusinessRuleException("This escalation has already been resolved.");
        }

        CareFlowUserDetails currentUser = currentUserProvider.require();
        User resolver = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> ResourceNotFoundException.of("User", currentUser.getId()));

        escalation.setStatus(EscalationStatus.RESOLVED);
        escalation.setResolutionNotes(request.resolutionNotes());
        escalation.setResolvedAt(Instant.now(clock));
        escalation.setResolvedBy(resolver);

        auditService.record(AuditAction.ESCALATION_RESOLVED, escalation.getPatient().getId(),
                "Escalation resolved by " + resolver.getFullName() + ".",
                Map.of("escalationId", escalation.getId()));

        log.info("Resolved escalation id={} by user id={}", escalationId, resolver.getId());
        return escalationMapper.toResponse(escalation);
    }

    private Escalation findOpenEscalation(Long patientId) {
        return escalationRepository.findByPatientIdOrderByCreatedAtDesc(patientId).stream()
                .filter(candidate -> !candidate.isResolved())
                .findFirst()
                .orElse(null);
    }

    private Escalation requireEscalation(Long id) {
        if (currentUserProvider.hasRole(UserRole.PATIENT)) {
            throw new AccessDeniedException("Patients cannot access escalation cases.");
        }
        return escalationRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Escalation", id));
    }
}
