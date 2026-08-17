package com.careflow.followup;

import com.careflow.audit.AuditService;
import com.careflow.careplan.CarePlan;
import com.careflow.common.enums.AuditAction;
import com.careflow.common.enums.CarePlanType;
import com.careflow.common.enums.FollowUpStatus;
import com.careflow.common.enums.FollowUpType;
import com.careflow.patient.Patient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Generates the post-discharge follow-up schedule from a care plan template.
 *
 * <p>The cadence itself lives on {@link CarePlanType} so the plan type remains
 * the single source of truth for both scheduling and display.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FollowUpSchedulingService {

    private final FollowUpTaskRepository followUpTaskRepository;
    private final AuditService auditService;

    /**
     * Creates one follow-up per day offset defined by the plan type. Offsets that
     * fall on or before the discharge date are still scheduled, keeping the
     * generated journey faithful to the template.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<FollowUpTask> generateForDischarge(Patient patient, CarePlan carePlan,
                                                   LocalDate dischargeDate) {
        CarePlanType planType = carePlan.getPlanType();

        List<FollowUpTask> tasks = planType.getFollowUpDayOffsets().stream()
                .map(offset -> buildTask(patient, carePlan, dischargeDate, offset))
                .toList();

        List<FollowUpTask> saved = followUpTaskRepository.saveAll(tasks);

        for (FollowUpTask task : saved) {
            auditService.record(AuditAction.FOLLOW_UP_CREATED, patient.getId(),
                    "Day " + task.getDayOffset() + " follow-up scheduled for " + task.getScheduledDate() + ".",
                    Map.of("followUpId", task.getId(), "dayOffset", task.getDayOffset()));
        }

        log.info("Generated {} follow-ups for patient id={} using plan {}",
                saved.size(), patient.getId(), planType);
        return saved;
    }

    private FollowUpTask buildTask(Patient patient, CarePlan carePlan,
                                   LocalDate dischargeDate, int dayOffset) {
        return FollowUpTask.builder()
                .patient(patient)
                .carePlan(carePlan)
                .scheduledDate(dischargeDate.plusDays(dayOffset))
                .dayOffset(dayOffset)
                .status(FollowUpStatus.SCHEDULED)
                .type(typeForOffset(dayOffset))
                .title("Day " + dayOffset + " check-in")
                .build();
    }

    /**
     * The first contact after discharge focuses on symptoms, the last on refill
     * continuity, and the touchpoints in between on medication review.
     */
    private FollowUpType typeForOffset(int dayOffset) {
        if (dayOffset <= 1) {
            return FollowUpType.POST_DISCHARGE_CHECK;
        }
        if (dayOffset >= 30) {
            return FollowUpType.REFILL_CHECK;
        }
        return FollowUpType.MEDICATION_REVIEW;
    }
}
