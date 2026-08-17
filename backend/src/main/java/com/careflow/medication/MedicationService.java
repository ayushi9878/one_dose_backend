package com.careflow.medication;

import com.careflow.audit.AuditService;
import com.careflow.common.enums.AuditAction;
import com.careflow.exception.ResourceNotFoundException;
import com.careflow.medication.dto.MedicationRequest;
import com.careflow.medication.dto.MedicationResponse;
import com.careflow.patient.Patient;
import com.careflow.patient.PatientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class MedicationService {

    private final MedicationRepository medicationRepository;
    private final MedicationMapper medicationMapper;
    private final PatientService patientService;
    private final AuditService auditService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<MedicationResponse> listForPatient(Long patientId) {
        patientService.requireAccessiblePatient(patientId);
        return medicationRepository.findByPatientIdOrderByActiveDescMedicineNameAsc(patientId)
                .stream()
                .map(medicationMapper::toResponse)
                .toList();
    }

    @Transactional
    public MedicationResponse create(Long patientId, MedicationRequest request) {
        Patient patient = patientService.requireAccessiblePatient(patientId);

        Medication medication = Medication.builder()
                .patient(patient)
                .medicineName(request.medicineName())
                .dosage(request.dosage())
                .frequency(request.frequency())
                .startDate(request.startDate())
                .endDate(request.endDate())
                .instructions(request.instructions())
                .active(request.active() == null || request.active())
                .build();

        Medication saved = medicationRepository.save(medication);
        auditService.record(AuditAction.MEDICATION_ADDED, patientId,
                saved.getMedicineName() + " " + saved.getDosage() + " added to the medication list.",
                Map.of("medicationId", saved.getId(), "frequency", saved.getFrequency().name()));

        return medicationMapper.toResponse(saved);
    }

    @Transactional
    public MedicationResponse update(Long medicationId, MedicationRequest request) {
        Medication medication = requireAccessibleMedication(medicationId);
        boolean wasActive = medication.isActive();

        medication.setMedicineName(request.medicineName());
        medication.setDosage(request.dosage());
        medication.setFrequency(request.frequency());
        medication.setStartDate(request.startDate());
        medication.setEndDate(request.endDate());
        medication.setInstructions(request.instructions());
        if (request.active() != null) {
            medication.setActive(request.active());
        }

        Long patientId = medication.getPatient().getId();
        AuditAction action = (wasActive && !medication.isActive())
                ? AuditAction.MEDICATION_DISCONTINUED
                : AuditAction.MEDICATION_UPDATED;
        auditService.record(action, patientId,
                medication.getMedicineName() + " prescription updated.",
                Map.of("medicationId", medication.getId()));

        return medicationMapper.toResponse(medication);
    }

    /**
     * Discontinues rather than deletes, so historic adherence stays explainable.
     */
    @Transactional
    public void discontinue(Long medicationId) {
        Medication medication = requireAccessibleMedication(medicationId);
        medication.setActive(false);
        medication.setEndDate(LocalDate.now(clock));

        auditService.record(AuditAction.MEDICATION_DISCONTINUED, medication.getPatient().getId(),
                medication.getMedicineName() + " discontinued.",
                Map.of("medicationId", medication.getId()));
        log.info("Discontinued medication id={}", medicationId);
    }

    private Medication requireAccessibleMedication(Long medicationId) {
        Medication medication = medicationRepository.findById(medicationId)
                .orElseThrow(() -> ResourceNotFoundException.of("Medication", medicationId));
        patientService.requireAccessiblePatient(medication.getPatient().getId());
        return medication;
    }
}
