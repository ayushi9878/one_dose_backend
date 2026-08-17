package com.careflow.adherence;

import com.careflow.adherence.dto.AdherencePoint;
import com.careflow.adherence.dto.AdherenceSummary;
import com.careflow.config.CareFlowProperties;
import com.careflow.followup.FollowUpTask;
import com.careflow.medication.Medication;
import com.careflow.medication.MedicationRepository;
import com.careflow.patient.Patient;
import com.careflow.patient.PatientService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * Owns every adherence figure the platform reports. Percentages are always
 * derived here from stored dose counts, never supplied by a client.
 */
@Service
@RequiredArgsConstructor
public class AdherenceService {

    private final AdherenceEventRepository adherenceEventRepository;
    private final MedicationRepository medicationRepository;
    private final PatientService patientService;
    private final CareFlowProperties properties;
    private final Clock clock;

    @Transactional(readOnly = true)
    public AdherenceSummary getForPatient(Long patientId) {
        Patient patient = patientService.requireAccessiblePatient(patientId);
        List<AdherenceEvent> events = adherenceEventRepository.findByPatientIdOrderByRecordedDateAsc(patientId);

        int expected = events.stream().mapToInt(AdherenceEvent::getExpectedDoses).sum();
        int taken = events.stream().mapToInt(AdherenceEvent::getTakenDoses).sum();
        int missed = events.stream().mapToInt(AdherenceEvent::getMissedDoses).sum();
        double percentage = percentage(taken, expected);

        return new AdherenceSummary(
                patient.getId(),
                patient.getFullName(),
                expected,
                taken,
                missed,
                percentage,
                expected > 0 && percentage < properties.getAdherence().getLowThresholdPercentage(),
                events.stream().map(this::toPoint).toList());
    }

    /**
     * Returns the patient's current adherence percentage, or null when no doses
     * have been recorded yet — a patient with no history is not "0% adherent".
     */
    @Transactional(readOnly = true)
    public Double currentPercentage(Long patientId) {
        AdherenceTotals totals = adherenceEventRepository.sumTotalsForPatient(patientId);
        if (totals == null || totals.getExpectedDoses() == null || totals.getExpectedDoses() == 0) {
            return null;
        }
        return percentage(totals.getTakenDoses().intValue(), totals.getExpectedDoses().intValue());
    }

    /**
     * Converts a follow-up response into an adherence reading.
     *
     * <p>Expected doses are projected from the patient's active prescriptions over
     * the interval the follow-up covers, so a patient on more medicines is not
     * unfairly penalised for the same number of missed doses.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public AdherenceEvent recordFromResponse(Patient patient, FollowUpTask followUpTask,
                                             boolean medicationTaken, int missedDoses) {
        int expectedDoses = projectExpectedDoses(patient, followUpTask);
        int cappedMissed = Math.min(missedDoses, expectedDoses);
        int takenDoses = medicationTaken ? Math.max(expectedDoses - cappedMissed, 0) : 0;

        return adherenceEventRepository.save(AdherenceEvent.builder()
                .patient(patient)
                .followUpTask(followUpTask)
                .recordedDate(LocalDate.now(clock))
                .expectedDoses(expectedDoses)
                .takenDoses(takenDoses)
                .missedDoses(medicationTaken ? cappedMissed : expectedDoses)
                .build());
    }

    /**
     * Sums each active medication's daily dosing across the days since the
     * previous touchpoint. Falls back to a single-day window for the first
     * follow-up after discharge.
     */
    private int projectExpectedDoses(Patient patient, FollowUpTask followUpTask) {
        LocalDate windowEnd = followUpTask.getScheduledDate();
        LocalDate windowStart = windowStart(patient, followUpTask, windowEnd);

        List<Medication> medications = medicationRepository.findByPatientIdAndActiveTrue(patient.getId());
        double projected = medications.stream()
                .mapToDouble(medication -> medication.getFrequency().getDosesPerDay()
                        * medication.activeDaysWithin(windowStart, windowEnd))
                .sum();

        return Math.max((int) Math.round(projected), 0);
    }

    private LocalDate windowStart(Patient patient, FollowUpTask followUpTask, LocalDate windowEnd) {
        LocalDate anchor = patient.getDischargeDate() != null
                ? patient.getDischargeDate()
                : windowEnd.minusDays(1);
        Integer dayOffset = followUpTask.getDayOffset();
        if (dayOffset == null) {
            return anchor.isBefore(windowEnd) ? anchor : windowEnd.minusDays(1);
        }
        LocalDate candidate = windowEnd.minusDays(Math.max(dayOffset, 1));
        return candidate.isBefore(anchor) ? anchor : candidate;
    }

    private AdherencePoint toPoint(AdherenceEvent event) {
        return new AdherencePoint(
                event.getRecordedDate(),
                event.getExpectedDoses(),
                event.getTakenDoses(),
                event.getMissedDoses(),
                percentage(event.getTakenDoses(), event.getExpectedDoses()));
    }

    private double percentage(int taken, int expected) {
        if (expected <= 0) {
            return 0d;
        }
        return BigDecimal.valueOf(taken * 100.0 / expected)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
