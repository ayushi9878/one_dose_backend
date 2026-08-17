package com.careflow.careplan;

import com.careflow.audit.AuditService;
import com.careflow.careplan.dto.CarePlanRequest;
import com.careflow.careplan.dto.CarePlanResponse;
import com.careflow.careplan.dto.DischargeRequest;
import com.careflow.careplan.dto.DischargeResponse;
import com.careflow.common.enums.AuditAction;
import com.careflow.common.enums.CarePlanStatus;
import com.careflow.common.enums.CarePlanType;
import com.careflow.common.enums.RiskLevel;
import com.careflow.exception.BusinessRuleException;
import com.careflow.exception.ResourceNotFoundException;
import com.careflow.followup.FollowUpMapper;
import com.careflow.followup.FollowUpSchedulingService;
import com.careflow.followup.FollowUpTask;
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
public class CarePlanService {

    private final CarePlanRepository carePlanRepository;
    private final CarePlanMapper carePlanMapper;
    private final PatientService patientService;
    private final FollowUpSchedulingService followUpSchedulingService;
    private final FollowUpMapper followUpMapper;
    private final AuditService auditService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public CarePlanResponse getActiveForPatient(Long patientId) {
        patientService.requireAccessiblePatient(patientId);
        return carePlanRepository
                .findFirstByPatientIdAndStatusOrderByStartDateDesc(patientId, CarePlanStatus.ACTIVE)
                .map(carePlanMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No active care plan exists for patient " + patientId + "."));
    }

    @Transactional(readOnly = true)
    public List<CarePlanResponse> listForPatient(Long patientId) {
        patientService.requireAccessiblePatient(patientId);
        return carePlanRepository.findByPatientIdOrderByStartDateDesc(patientId)
                .stream()
                .map(carePlanMapper::toResponse)
                .toList();
    }

    @Transactional
    public CarePlanResponse create(Long patientId, CarePlanRequest request) {
        Patient patient = patientService.requireAccessiblePatient(patientId);
        CarePlanStatus status = request.status() != null ? request.status() : CarePlanStatus.ACTIVE;

        if (status == CarePlanStatus.ACTIVE) {
            closeExistingActivePlan(patientId);
        }

        CarePlan carePlan = carePlanRepository.save(CarePlan.builder()
                .patient(patient)
                .startDate(request.startDate())
                .endDate(request.endDate())
                .planType(request.planType())
                .status(status)
                .notes(request.notes())
                .build());

        auditService.record(AuditAction.CARE_PLAN_CREATED, patientId,
                request.planType() + " care plan created.",
                Map.of("carePlanId", carePlan.getId(), "planType", request.planType().name()));

        return carePlanMapper.toResponse(carePlan);
    }

    @Transactional
    public CarePlanResponse update(Long carePlanId, CarePlanRequest request) {
        CarePlan carePlan = carePlanRepository.findById(carePlanId)
                .orElseThrow(() -> ResourceNotFoundException.of("Care plan", carePlanId));
        patientService.requireAccessiblePatient(carePlan.getPatient().getId());

        carePlan.setStartDate(request.startDate());
        carePlan.setEndDate(request.endDate());
        carePlan.setPlanType(request.planType());
        carePlan.setNotes(request.notes());
        if (request.status() != null) {
            carePlan.setStatus(request.status());
        }

        auditService.record(AuditAction.CARE_PLAN_UPDATED, carePlan.getPatient().getId(),
                "Care plan updated.", Map.of("carePlanId", carePlan.getId()));

        return carePlanMapper.toResponse(carePlan);
    }

    /**
     * Discharges a patient: records the discharge date, opens the post-discharge
     * care plan and generates its follow-up schedule in one transaction, so a
     * discharged patient can never exist without a follow-up plan.
     */
    @Transactional
    public DischargeResponse discharge(Long patientId, DischargeRequest request) {
        Patient patient = patientService.requireAccessiblePatient(patientId);

        if (patient.getDischargeDate() != null) {
            throw new BusinessRuleException(
                    "Patient " + patientId + " was already discharged on " + patient.getDischargeDate() + ".");
        }
        if (request.dischargeDate().isAfter(LocalDate.now(clock))) {
            throw new BusinessRuleException("Discharge date cannot be in the future.");
        }

        closeExistingActivePlan(patientId);

        CarePlanType planType = request.followUpPlan();
        CarePlan carePlan = carePlanRepository.save(CarePlan.builder()
                .patient(patient)
                .startDate(request.dischargeDate())
                .endDate(request.dischargeDate().plusDays(planType.getDurationDays()))
                .planType(planType)
                .status(CarePlanStatus.ACTIVE)
                .notes("Generated automatically on discharge.")
                .build());

        auditService.record(AuditAction.CARE_PLAN_CREATED, patientId,
                planType + " care plan created on discharge.",
                Map.of("carePlanId", carePlan.getId(), "planType", planType.name()));

        patient.setDischargeDate(request.dischargeDate());
        if (planType == CarePlanType.HIGH_RISK) {
            patient.setCurrentRiskLevel(RiskLevel.HIGH);
        }

        List<FollowUpTask> followUps =
                followUpSchedulingService.generateForDischarge(patient, carePlan, request.dischargeDate());

        auditService.record(AuditAction.PATIENT_DISCHARGED, patientId,
                "Patient discharged on " + request.dischargeDate() + " under a " + planType + " plan.",
                Map.of("carePlanId", carePlan.getId(),
                        "planType", planType.name(),
                        "followUpsCreated", followUps.size()));

        log.info("Discharged patient id={} plan={} followUps={}", patientId, planType, followUps.size());

        return new DischargeResponse(
                patient.getId(),
                patient.getFullName(),
                request.dischargeDate(),
                carePlanMapper.toResponse(carePlan),
                followUps.size(),
                followUps.stream().map(followUpMapper::toResponse).toList());
    }

    /**
     * A patient carries at most one active plan; superseding one completes it
     * rather than leaving two plans competing for the same journey.
     */
    private void closeExistingActivePlan(Long patientId) {
        carePlanRepository
                .findFirstByPatientIdAndStatusOrderByStartDateDesc(patientId, CarePlanStatus.ACTIVE)
                .ifPresent(existing -> existing.setStatus(CarePlanStatus.COMPLETED));
    }
}
