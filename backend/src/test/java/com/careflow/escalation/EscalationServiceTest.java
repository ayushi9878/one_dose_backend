package com.careflow.escalation;

import com.careflow.ai.CaseSummaryService;
import com.careflow.audit.AuditService;
import com.careflow.common.enums.EscalationSeverity;
import com.careflow.common.enums.EscalationStatus;
import com.careflow.common.enums.RiskLevel;
import com.careflow.common.enums.RiskSignalCode;
import com.careflow.common.enums.UserRole;
import com.careflow.escalation.dto.AssignEscalationRequest;
import com.careflow.escalation.dto.EscalationResponse;
import com.careflow.escalation.dto.ResolveEscalationRequest;
import com.careflow.exception.BusinessRuleException;
import com.careflow.patient.Patient;
import com.careflow.risk.RiskSignalRepository;
import com.careflow.risk.dto.RiskEvaluationResult;
import com.careflow.security.CareFlowUserDetails;
import com.careflow.security.CurrentUserProvider;
import com.careflow.user.User;
import com.careflow.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Escalation workflow")
class EscalationServiceTest {

    @Mock
    private EscalationRepository escalationRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private RiskSignalRepository riskSignalRepository;
    @Mock
    private CaseSummaryService caseSummaryService;
    @Mock
    private AuditService auditService;
    @Mock
    private CurrentUserProvider currentUserProvider;

    private EscalationService escalationService;
    private Patient patient;
    private User careManager;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(
                LocalDate.of(2026, 8, 16).atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);

        escalationService = new EscalationService(
                escalationRepository, new EscalationMapper(), userRepository, riskSignalRepository,
                caseSummaryService, auditService, currentUserProvider, clock);

        patient = Patient.builder()
                .medicalRecordNumber("MRN-1")
                .firstName("Rina")
                .lastName("Mehta")
                .dateOfBirth(LocalDate.of(1978, 4, 12))
                .build();
        patient.setId(1L);

        careManager = User.builder()
                .email("alex.chen@careflow.health")
                .passwordHash("hash")
                .fullName("Alex Chen")
                .role(UserRole.CARE_MANAGER)
                .enabled(true)
                .build();
        careManager.setId(5L);

        when(currentUserProvider.hasRole(UserRole.PATIENT)).thenReturn(false);
        when(currentUserProvider.require()).thenReturn(new CareFlowUserDetails(careManager));
        when(escalationRepository.save(any(Escalation.class)))
                .thenAnswer(invocation -> {
                    Escalation saved = invocation.getArgument(0);
                    if (saved.getId() == null) {
                        saved.setId(100L);
                    }
                    return saved;
                });
    }

    private Escalation openEscalation() {
        Escalation escalation = Escalation.builder()
                .patient(patient)
                .severity(EscalationSeverity.HIGH)
                .reason("HIGH operational risk.")
                .status(EscalationStatus.PENDING)
                .build();
        escalation.setId(100L);
        return escalation;
    }

    private RiskEvaluationResult highRiskEvaluation() {
        return new RiskEvaluationResult(
                RiskLevel.HIGH,
                List.of(RiskSignalCode.SYMPTOMS_REPORTED, RiskSignalCode.MULTIPLE_MISSED_DOSES),
                List.of("Symptoms reported.", "Multiple missed doses."),
                true,
                "HIGH operational risk. Routed to a care manager for human review.");
    }

    @Test
    @DisplayName("a high-risk evaluation opens a PENDING escalation with matching severity")
    void createsEscalationFromHighRisk() {
        when(escalationRepository.findByPatientIdOrderByCreatedAtDesc(1L)).thenReturn(List.of());

        Escalation escalation =
                escalationService.createFromRiskEvaluation(patient, null, highRiskEvaluation());

        assertThat(escalation.getSeverity()).isEqualTo(EscalationSeverity.HIGH);
        assertThat(escalation.getStatus()).isEqualTo(EscalationStatus.PENDING);
        assertThat(escalation.getPatient()).isSameAs(patient);
    }

    @Test
    @DisplayName("an existing open case is raised in severity rather than duplicated")
    void doesNotDuplicateOpenEscalations() {
        Escalation existing = openEscalation();
        existing.setSeverity(EscalationSeverity.MEDIUM);
        when(escalationRepository.findByPatientIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(existing));

        Escalation result =
                escalationService.createFromRiskEvaluation(patient, null, highRiskEvaluation());

        assertThat(result).isSameAs(existing);
        assertThat(result.getSeverity()).isEqualTo(EscalationSeverity.HIGH);
        verify(escalationRepository, never()).save(any(Escalation.class));
    }

    @Test
    @DisplayName("a repeat signal at the same severity still updates the open case")
    void repeatSignalUpdatesOpenCaseEvenWhenSeverityIsUnchanged() {
        Escalation existing = openEscalation();
        existing.setSeverity(EscalationSeverity.HIGH);
        existing.setReason("Original seeded reason.");
        when(escalationRepository.findByPatientIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(existing));

        Escalation result =
                escalationService.createFromRiskEvaluation(patient, null, highRiskEvaluation());

        // Severity cannot rise past HIGH, but the case must still reflect the
        // new report — otherwise a reviewer sees stale evidence.
        assertThat(result).isSameAs(existing);
        assertThat(result.getSeverity()).isEqualTo(EscalationSeverity.HIGH);
        assertThat(result.getReason()).isEqualTo(highRiskEvaluation().summary());
        verify(auditService).record(
                org.mockito.ArgumentMatchers.eq(com.careflow.common.enums.AuditAction.ESCALATION_UPDATED),
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyMap());
    }

    @Test
    @DisplayName("a recurrence returns an in-review case to the assigned queue")
    void recurrenceReopensInReviewCase() {
        Escalation existing = openEscalation();
        existing.setStatus(EscalationStatus.IN_REVIEW);
        existing.setAssignedCareManager(careManager);
        when(escalationRepository.findByPatientIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(existing));

        Escalation result =
                escalationService.createFromRiskEvaluation(patient, null, highRiskEvaluation());

        assertThat(result.getStatus()).isEqualTo(EscalationStatus.ASSIGNED);
    }

    @Test
    @DisplayName("assigning sets the care manager, timestamp and ASSIGNED status")
    void assignMovesCaseToAssigned() {
        when(escalationRepository.findById(100L)).thenReturn(Optional.of(openEscalation()));
        when(userRepository.findById(5L)).thenReturn(Optional.of(careManager));

        EscalationResponse response =
                escalationService.assign(100L, new AssignEscalationRequest(5L));

        assertThat(response.status()).isEqualTo(EscalationStatus.ASSIGNED);
        assertThat(response.assignedCareManagerName()).isEqualTo("Alex Chen");
        assertThat(response.assignedAt()).isNotNull();
    }

    @Test
    @DisplayName("an escalation cannot be assigned to a non-care-manager")
    void rejectsAssignmentToPatientRole() {
        User portalUser = User.builder()
                .email("patient@example.com").passwordHash("hash")
                .fullName("Rina Mehta").role(UserRole.PATIENT).enabled(true).build();
        portalUser.setId(9L);

        when(escalationRepository.findById(100L)).thenReturn(Optional.of(openEscalation()));
        when(userRepository.findById(9L)).thenReturn(Optional.of(portalUser));

        assertThatThrownBy(() -> escalationService.assign(100L, new AssignEscalationRequest(9L)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("care manager");
    }

    @Test
    @DisplayName("marking in review requires an assignee first")
    void reviewRequiresAssignment() {
        when(escalationRepository.findById(100L)).thenReturn(Optional.of(openEscalation()));

        assertThatThrownBy(() -> escalationService.markInReview(100L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Assign the escalation");
    }

    @Test
    @DisplayName("resolving records notes, resolver and timestamp")
    void resolveClosesTheCase() {
        Escalation escalation = openEscalation();
        escalation.setAssignedCareManager(careManager);
        escalation.setStatus(EscalationStatus.IN_REVIEW);

        when(escalationRepository.findById(100L)).thenReturn(Optional.of(escalation));
        when(userRepository.findById(5L)).thenReturn(Optional.of(careManager));

        EscalationResponse response = escalationService.resolve(100L,
                new ResolveEscalationRequest("Spoke with the patient; refill arranged."));

        assertThat(response.status()).isEqualTo(EscalationStatus.RESOLVED);
        assertThat(response.resolutionNotes()).isEqualTo("Spoke with the patient; refill arranged.");
        assertThat(response.resolvedAt()).isNotNull();
        assertThat(response.resolvedByName()).isEqualTo("Alex Chen");
        assertThat(response.requiresHumanReview()).isFalse();
    }

    @Test
    @DisplayName("a resolved case cannot be resolved twice")
    void cannotResolveTwice() {
        Escalation escalation = openEscalation();
        escalation.setStatus(EscalationStatus.RESOLVED);
        when(escalationRepository.findById(100L)).thenReturn(Optional.of(escalation));

        assertThatThrownBy(() -> escalationService.resolve(100L,
                new ResolveEscalationRequest("Attempting to resolve again.")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already been resolved");
    }

    @Test
    @DisplayName("a resolved case cannot be reassigned")
    void cannotReassignResolvedCase() {
        Escalation escalation = openEscalation();
        escalation.setStatus(EscalationStatus.RESOLVED);
        when(escalationRepository.findById(100L)).thenReturn(Optional.of(escalation));

        assertThatThrownBy(() -> escalationService.assign(100L, new AssignEscalationRequest(5L)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("medium risk maps to medium severity")
    void mediumRiskMapsToMediumSeverity() {
        when(escalationRepository.findByPatientIdOrderByCreatedAtDesc(1L)).thenReturn(List.of());
        RiskEvaluationResult mediumRisk = new RiskEvaluationResult(
                RiskLevel.MEDIUM, List.of(RiskSignalCode.REFILL_NEEDED),
                List.of("Refill needed."), true, "MEDIUM operational risk.");

        Escalation escalation =
                escalationService.createFromRiskEvaluation(patient, null, mediumRisk);

        assertThat(escalation.getSeverity()).isEqualTo(EscalationSeverity.MEDIUM);
    }
}
