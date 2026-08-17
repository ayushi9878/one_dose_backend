package com.careflow.adherence;

import com.careflow.adherence.dto.AdherenceSummary;
import com.careflow.common.enums.MedicationFrequency;
import com.careflow.config.CareFlowProperties;
import com.careflow.followup.FollowUpTask;
import com.careflow.medication.Medication;
import com.careflow.medication.MedicationRepository;
import com.careflow.patient.Patient;
import com.careflow.patient.PatientService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Adherence calculation")
class AdherenceServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 16);

    @Mock
    private AdherenceEventRepository adherenceEventRepository;
    @Mock
    private MedicationRepository medicationRepository;
    @Mock
    private PatientService patientService;

    private AdherenceService adherenceService;
    private Patient patient;

    @BeforeEach
    void setUp() {
        CareFlowProperties properties = new CareFlowProperties();
        properties.getAdherence().setLowThresholdPercentage(80);
        Clock clock = Clock.fixed(TODAY.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);

        adherenceService = new AdherenceService(
                adherenceEventRepository, medicationRepository, patientService, properties, clock);

        patient = Patient.builder()
                .medicalRecordNumber("MRN-1")
                .firstName("Rina")
                .lastName("Mehta")
                .dateOfBirth(LocalDate.of(1978, 4, 12))
                .dischargeDate(TODAY.minusDays(7))
                .build();
        patient.setId(1L);
    }

    private AdherenceEvent event(int expected, int taken, int missed) {
        return AdherenceEvent.builder()
                .patient(patient)
                .recordedDate(TODAY)
                .expectedDoses(expected)
                .takenDoses(taken)
                .missedDoses(missed)
                .build();
    }

    @Test
    @DisplayName("percentage is taken / expected * 100, rounded to two decimals")
    void calculatesPercentageFromStoredDoses() {
        when(patientService.requireAccessiblePatient(1L)).thenReturn(patient);
        when(adherenceEventRepository.findByPatientIdOrderByRecordedDateAsc(1L))
                .thenReturn(List.of(event(30, 26, 4)));

        AdherenceSummary summary = adherenceService.getForPatient(1L);

        assertThat(summary.expectedDoses()).isEqualTo(30);
        assertThat(summary.takenDoses()).isEqualTo(26);
        assertThat(summary.missedDoses()).isEqualTo(4);
        assertThat(summary.adherencePercentage()).isEqualTo(86.67);
    }

    @Test
    @DisplayName("totals aggregate across every recorded event")
    void aggregatesAcrossEvents() {
        when(patientService.requireAccessiblePatient(1L)).thenReturn(patient);
        when(adherenceEventRepository.findByPatientIdOrderByRecordedDateAsc(1L))
                .thenReturn(List.of(event(10, 10, 0), event(10, 5, 5)));

        AdherenceSummary summary = adherenceService.getForPatient(1L);

        assertThat(summary.expectedDoses()).isEqualTo(20);
        assertThat(summary.takenDoses()).isEqualTo(15);
        assertThat(summary.adherencePercentage()).isEqualTo(75.0);
        assertThat(summary.belowThreshold()).isTrue();
        assertThat(summary.history()).hasSize(2);
    }

    @Test
    @DisplayName("a patient with no history reports zero without dividing by zero")
    void handlesEmptyHistory() {
        when(patientService.requireAccessiblePatient(1L)).thenReturn(patient);
        when(adherenceEventRepository.findByPatientIdOrderByRecordedDateAsc(1L)).thenReturn(List.of());

        AdherenceSummary summary = adherenceService.getForPatient(1L);

        assertThat(summary.adherencePercentage()).isZero();
        assertThat(summary.belowThreshold()).isFalse();
    }

    @Test
    @DisplayName("currentPercentage returns null when no doses are expected yet")
    void currentPercentageIsNullWithoutHistory() {
        when(adherenceEventRepository.sumTotalsForPatient(1L)).thenReturn(totals(0L, 0L, 0L));

        assertThat(adherenceService.currentPercentage(1L)).isNull();
    }

    @Test
    @DisplayName("expected doses are projected from active prescriptions over the interval")
    void projectsExpectedDosesFromPrescriptions() {
        FollowUpTask task = FollowUpTask.builder()
                .patient(patient)
                .scheduledDate(TODAY)
                .dayOffset(7)
                .build();

        Medication twiceDaily = Medication.builder()
                .patient(patient)
                .medicineName("Metoprolol")
                .dosage("25 mg")
                .frequency(MedicationFrequency.TWICE_DAILY)
                .startDate(TODAY.minusDays(30))
                .active(true)
                .build();

        when(medicationRepository.findByPatientIdAndActiveTrue(1L)).thenReturn(List.of(twiceDaily));
        when(adherenceEventRepository.save(any(AdherenceEvent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        adherenceService.recordFromResponse(patient, task, true, 3);

        ArgumentCaptor<AdherenceEvent> captor = ArgumentCaptor.forClass(AdherenceEvent.class);
        verify(adherenceEventRepository).save(captor.capture());
        AdherenceEvent saved = captor.getValue();

        // Seven days of a twice-daily medicine plus the inclusive end day.
        assertThat(saved.getExpectedDoses()).isEqualTo(16);
        assertThat(saved.getMissedDoses()).isEqualTo(3);
        assertThat(saved.getTakenDoses()).isEqualTo(13);
    }

    @Test
    @DisplayName("missed doses cannot exceed the doses that were expected")
    void missedDosesAreCappedAtExpected() {
        FollowUpTask task = FollowUpTask.builder()
                .patient(patient).scheduledDate(TODAY).dayOffset(1).build();

        Medication onceDaily = Medication.builder()
                .patient(patient)
                .medicineName("Ramipril")
                .dosage("5 mg")
                .frequency(MedicationFrequency.ONCE_DAILY)
                .startDate(TODAY.minusDays(10))
                .active(true)
                .build();

        when(medicationRepository.findByPatientIdAndActiveTrue(1L)).thenReturn(List.of(onceDaily));
        when(adherenceEventRepository.save(any(AdherenceEvent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        adherenceService.recordFromResponse(patient, task, true, 500);

        ArgumentCaptor<AdherenceEvent> captor = ArgumentCaptor.forClass(AdherenceEvent.class);
        verify(adherenceEventRepository).save(captor.capture());

        assertThat(captor.getValue().getMissedDoses())
                .isLessThanOrEqualTo(captor.getValue().getExpectedDoses());
        assertThat(captor.getValue().getTakenDoses()).isNotNegative();
    }

    @Test
    @DisplayName("reporting medication as not taken records every expected dose as missed")
    void medicationNotTakenMeansNoDosesTaken() {
        FollowUpTask task = FollowUpTask.builder()
                .patient(patient).scheduledDate(TODAY).dayOffset(1).build();

        Medication onceDaily = Medication.builder()
                .patient(patient)
                .medicineName("Ramipril")
                .dosage("5 mg")
                .frequency(MedicationFrequency.ONCE_DAILY)
                .startDate(TODAY.minusDays(10))
                .active(true)
                .build();

        when(medicationRepository.findByPatientIdAndActiveTrue(1L)).thenReturn(List.of(onceDaily));
        when(adherenceEventRepository.save(any(AdherenceEvent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        adherenceService.recordFromResponse(patient, task, false, 0);

        ArgumentCaptor<AdherenceEvent> captor = ArgumentCaptor.forClass(AdherenceEvent.class);
        verify(adherenceEventRepository).save(captor.capture());

        assertThat(captor.getValue().getTakenDoses()).isZero();
        assertThat(captor.getValue().getMissedDoses())
                .isEqualTo(captor.getValue().getExpectedDoses());
    }

    private AdherenceTotals totals(Long expected, Long taken, Long missed) {
        return new AdherenceTotals() {
            @Override
            public Long getExpectedDoses() {
                return expected;
            }

            @Override
            public Long getTakenDoses() {
                return taken;
            }

            @Override
            public Long getMissedDoses() {
                return missed;
            }
        };
    }
}
