package com.careflow.patient;

import com.careflow.audit.AuditService;
import com.careflow.common.PageResponse;
import com.careflow.common.enums.AuditAction;
import com.careflow.common.enums.RiskLevel;
import com.careflow.common.enums.UserRole;
import com.careflow.exception.BusinessRuleException;
import com.careflow.exception.DuplicateResourceException;
import com.careflow.exception.ResourceNotFoundException;
import com.careflow.patient.dto.PatientRequest;
import com.careflow.patient.dto.PatientResponse;
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
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PatientService {

    private final PatientRepository patientRepository;
    private final UserRepository userRepository;
    private final PatientMapper patientMapper;
    private final AuditService auditService;
    private final CurrentUserProvider currentUserProvider;

    /**
     * Roster search. Admins see everyone, care managers see their own caseload;
     * the PATIENT role has no roster access at all and is rejected by the
     * controller before reaching this method.
     */
    @Transactional(readOnly = true)
    public PageResponse<PatientResponse> search(String search, RiskLevel riskLevel,
                                                Boolean active, Pageable pageable) {
        String normalised = (search == null || search.isBlank()) ? null : search.trim();
        Page<Patient> page = patientRepository.search(
                normalised, riskLevel, careManagerScopeFilter(), active, pageable);
        return PageResponse.from(page, patientMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public PatientResponse getById(Long id) {
        return patientMapper.toResponse(requireAccessiblePatient(id));
    }

    @Transactional
    public PatientResponse create(PatientRequest request) {
        if (patientRepository.existsByMedicalRecordNumber(request.medicalRecordNumber())) {
            throw new DuplicateResourceException(
                    "A patient already exists with medical record number " + request.medicalRecordNumber() + ".");
        }

        Patient patient = Patient.builder()
                .medicalRecordNumber(request.medicalRecordNumber())
                .firstName(request.firstName())
                .lastName(request.lastName())
                .dateOfBirth(request.dateOfBirth())
                .phone(request.phone())
                .email(request.email())
                .primaryCondition(request.primaryCondition())
                .currentRiskLevel(RiskLevel.NONE)
                .active(true)
                .careManager(resolveCareManager(request.careManagerId()))
                .build();

        Patient saved = patientRepository.save(patient);
        auditService.record(AuditAction.PATIENT_CREATED, saved.getId(),
                "Patient record created for " + saved.getFullName() + ".",
                Map.of("medicalRecordNumber", saved.getMedicalRecordNumber()));

        log.info("Created patient id={} mrn={}", saved.getId(), saved.getMedicalRecordNumber());
        return patientMapper.toResponse(saved);
    }

    @Transactional
    public PatientResponse update(Long id, PatientRequest request) {
        Patient patient = requireAccessiblePatient(id);

        patientRepository.findByMedicalRecordNumber(request.medicalRecordNumber())
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(conflict -> {
                    throw new DuplicateResourceException(
                            "Another patient already uses medical record number "
                                    + request.medicalRecordNumber() + ".");
                });

        patient.setMedicalRecordNumber(request.medicalRecordNumber());
        patient.setFirstName(request.firstName());
        patient.setLastName(request.lastName());
        patient.setDateOfBirth(request.dateOfBirth());
        patient.setPhone(request.phone());
        patient.setEmail(request.email());
        patient.setPrimaryCondition(request.primaryCondition());
        patient.setCareManager(resolveCareManager(request.careManagerId()));

        auditService.record(AuditAction.PATIENT_UPDATED, patient.getId(),
                "Patient details updated for " + patient.getFullName() + ".");

        return patientMapper.toResponse(patient);
    }

    /**
     * Patients are deactivated rather than removed so their care history and
     * audit trail remain intact.
     */
    @Transactional
    public void deactivate(Long id) {
        Patient patient = requireAccessiblePatient(id);
        if (!patient.isActive()) {
            throw new BusinessRuleException("Patient " + id + " is already inactive.");
        }
        patient.setActive(false);
        auditService.record(AuditAction.PATIENT_DELETED, patient.getId(),
                "Patient record deactivated for " + patient.getFullName() + ".");
        log.info("Deactivated patient id={}", id);
    }

    /**
     * Loads a patient, enforcing that care managers only reach their own
     * caseload and portal patients only reach their own record.
     */
    @Transactional(readOnly = true)
    public Patient requireAccessiblePatient(Long id) {
        Patient patient = patientRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Patient", id));
        assertAccessible(patient);
        return patient;
    }

    private void assertAccessible(Patient patient) {
        CareFlowUserDetails currentUser = currentUserProvider.require();
        switch (currentUser.getRole()) {
            case ADMIN -> {
                // Full visibility.
            }
            case CARE_MANAGER -> {
                User careManager = patient.getCareManager();
                if (careManager == null || !careManager.getId().equals(currentUser.getId())) {
                    throw new AccessDeniedException("This patient is not assigned to you.");
                }
            }
            case PATIENT -> {
                User linked = patient.getUser();
                if (linked == null || !linked.getId().equals(currentUser.getId())) {
                    throw new AccessDeniedException("You may only access your own patient record.");
                }
            }
        }
    }

    /**
     * Care managers see only their own caseload in list endpoints; other roles
     * are unfiltered.
     */
    private Long careManagerScopeFilter() {
        CareFlowUserDetails currentUser = currentUserProvider.require();
        return currentUser.getRole() == UserRole.CARE_MANAGER ? currentUser.getId() : null;
    }

    private User resolveCareManager(Long careManagerId) {
        if (careManagerId == null) {
            return null;
        }
        User careManager = userRepository.findById(careManagerId)
                .orElseThrow(() -> ResourceNotFoundException.of("Care manager", careManagerId));
        if (careManager.getRole() != UserRole.CARE_MANAGER && careManager.getRole() != UserRole.ADMIN) {
            throw new BusinessRuleException(
                    "User " + careManagerId + " is not a care manager and cannot be assigned patients.");
        }
        return careManager;
    }
}
