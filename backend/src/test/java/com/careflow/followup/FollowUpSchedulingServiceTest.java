package com.careflow.followup;

import com.careflow.audit.AuditService;
import com.careflow.careplan.CarePlan;
import com.careflow.common.enums.CarePlanType;
import com.careflow.common.enums.FollowUpStatus;
import com.careflow.common.enums.FollowUpType;
import com.careflow.patient.Patient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Follow-up generation on discharge")
class FollowUpSchedulingServiceTest {

    private static final LocalDate DISCHARGE = LocalDate.of(2026, 8, 16);

    @Mock
    private FollowUpTaskRepository followUpTaskRepository;
    @Mock
    private AuditService auditService;

    private FollowUpSchedulingService schedulingService;
    private Patient patient;

    @BeforeEach
    void setUp() {
        schedulingService = new FollowUpSchedulingService(followUpTaskRepository, auditService);

        patient = Patient.builder()
                .medicalRecordNumber("MRN-1")
                .firstName("Rina")
                .lastName("Mehta")
                .dateOfBirth(LocalDate.of(1978, 4, 12))
                .build();
        patient.setId(1L);

        // saveAll echoes its argument, assigning ids so audit metadata stays valid.
        when(followUpTaskRepository.saveAll(anyList())).thenAnswer(invocation -> {
            List<FollowUpTask> tasks = invocation.getArgument(0);
            long id = 1;
            for (FollowUpTask task : tasks) {
                task.setId(id++);
            }
            return tasks;
        });
    }

    private CarePlan carePlanOf(CarePlanType planType) {
        CarePlan carePlan = CarePlan.builder()
                .patient(patient)
                .startDate(DISCHARGE)
                .planType(planType)
                .build();
        carePlan.setId(10L);
        return carePlan;
    }

    @Test
    @DisplayName("STANDARD generates follow-ups on days 1, 7, 14 and 30")
    void standardPlanGeneratesFourFollowUps() {
        List<FollowUpTask> tasks = schedulingService.generateForDischarge(
                patient, carePlanOf(CarePlanType.STANDARD), DISCHARGE);

        assertThat(tasks).hasSize(4);
        assertThat(tasks).extracting(FollowUpTask::getDayOffset)
                .containsExactly(1, 7, 14, 30);
        assertThat(tasks).extracting(FollowUpTask::getScheduledDate)
                .containsExactly(
                        DISCHARGE.plusDays(1),
                        DISCHARGE.plusDays(7),
                        DISCHARGE.plusDays(14),
                        DISCHARGE.plusDays(30));
    }

    @Test
    @DisplayName("HIGH_RISK additionally generates a day 3 follow-up")
    void highRiskPlanGeneratesFiveFollowUps() {
        List<FollowUpTask> tasks = schedulingService.generateForDischarge(
                patient, carePlanOf(CarePlanType.HIGH_RISK), DISCHARGE);

        assertThat(tasks).hasSize(5);
        assertThat(tasks).extracting(FollowUpTask::getDayOffset)
                .containsExactly(1, 3, 7, 14, 30);
    }

    @Test
    @DisplayName("every generated follow-up starts as SCHEDULED and belongs to the care plan")
    void generatedFollowUpsAreScheduledAndLinked() {
        CarePlan carePlan = carePlanOf(CarePlanType.STANDARD);

        List<FollowUpTask> tasks = schedulingService.generateForDischarge(patient, carePlan, DISCHARGE);

        assertThat(tasks).allSatisfy(task -> {
            assertThat(task.getStatus()).isEqualTo(FollowUpStatus.SCHEDULED);
            assertThat(task.getCarePlan()).isSameAs(carePlan);
            assertThat(task.getPatient()).isSameAs(patient);
            assertThat(task.getCompletedDate()).isNull();
        });
    }

    @Test
    @DisplayName("follow-up type reflects its position in the care journey")
    void followUpTypeVariesByOffset() {
        List<FollowUpTask> tasks = schedulingService.generateForDischarge(
                patient, carePlanOf(CarePlanType.STANDARD), DISCHARGE);

        assertThat(tasks.get(0).getType()).isEqualTo(FollowUpType.POST_DISCHARGE_CHECK);
        assertThat(tasks.get(1).getType()).isEqualTo(FollowUpType.MEDICATION_REVIEW);
        assertThat(tasks.get(3).getType()).isEqualTo(FollowUpType.REFILL_CHECK);
    }

    @Test
    @DisplayName("POST_DISCHARGE follows the standard four-touchpoint cadence")
    void postDischargePlanMatchesStandardCadence() {
        List<FollowUpTask> tasks = schedulingService.generateForDischarge(
                patient, carePlanOf(CarePlanType.POST_DISCHARGE), DISCHARGE);

        assertThat(tasks).extracting(FollowUpTask::getDayOffset).containsExactly(1, 7, 14, 30);
    }
}
