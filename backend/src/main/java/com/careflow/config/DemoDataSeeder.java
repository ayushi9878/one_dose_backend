package com.careflow.config;

import com.careflow.adherence.AdherenceEvent;
import com.careflow.adherence.AdherenceEventRepository;
import com.careflow.audit.AuditEvent;
import com.careflow.audit.AuditEventRepository;
import com.careflow.careplan.CarePlan;
import com.careflow.careplan.CarePlanRepository;
import com.careflow.common.enums.AuditAction;
import com.careflow.common.enums.CarePlanStatus;
import com.careflow.common.enums.CarePlanType;
import com.careflow.common.enums.EscalationSeverity;
import com.careflow.common.enums.EscalationStatus;
import com.careflow.common.enums.FollowUpStatus;
import com.careflow.common.enums.FollowUpType;
import com.careflow.common.enums.MedicationFrequency;
import com.careflow.common.enums.RiskLevel;
import com.careflow.common.enums.RiskSignalCode;
import com.careflow.common.enums.UserRole;
import com.careflow.escalation.Escalation;
import com.careflow.escalation.EscalationRepository;
import com.careflow.followup.FollowUpTask;
import com.careflow.followup.FollowUpTaskRepository;
import com.careflow.followup.PatientResponse;
import com.careflow.followup.PatientResponseRepository;
import com.careflow.medication.Medication;
import com.careflow.medication.MedicationRepository;
import com.careflow.patient.Patient;
import com.careflow.patient.PatientRepository;
import com.careflow.risk.RiskSignal;
import com.careflow.risk.RiskSignalRepository;
import com.careflow.user.User;
import com.careflow.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Populates a demonstration dataset so the dashboard is meaningful on first run.
 *
 * <p>Enabled only when {@code careflow.seed.enabled} is true, and skipped
 * entirely once any patient exists, so it can never overwrite real data. Every
 * name, identifier and clinical detail below is fictional.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "careflow.seed", name = "enabled", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

    private static final String DEMO_PASSWORD = "CareFlow!2026";

    private final UserRepository userRepository;
    private final PatientRepository patientRepository;
    private final MedicationRepository medicationRepository;
    private final CarePlanRepository carePlanRepository;
    private final FollowUpTaskRepository followUpTaskRepository;
    private final PatientResponseRepository patientResponseRepository;
    private final AdherenceEventRepository adherenceEventRepository;
    private final RiskSignalRepository riskSignalRepository;
    private final EscalationRepository escalationRepository;
    private final AuditEventRepository auditEventRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    private final Random random = new Random(20260816L);

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (patientRepository.count() > 0) {
            log.info("Demo seed skipped: patient data already present.");
            return;
        }

        log.info("Seeding CareFlow demonstration data.");

        User admin = createUser("admin@careflow.health", "Priya Raghavan", UserRole.ADMIN);
        User alexChen = createUser("alex.chen@careflow.health", "Alex Chen", UserRole.CARE_MANAGER);
        User mayaSingh = createUser("maya.singh@careflow.health", "Maya Singh", UserRole.CARE_MANAGER);
        List<User> careManagers = List.of(alexChen, mayaSingh);

        List<PatientSeed> seeds = patientSeeds();
        List<Patient> patients = new ArrayList<>();

        for (int i = 0; i < seeds.size(); i++) {
            PatientSeed seed = seeds.get(i);
            User careManager = careManagers.get(i % careManagers.size());
            patients.add(createPatientGraph(seed, careManager, admin));
        }

        createPatientPortalAccount(patients.get(0));

        log.info("Demo seed complete: {} patients, {} users.", patients.size(), 3);
    }

    private User createUser(String email, String fullName, UserRole role) {
        return userRepository.save(User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(DEMO_PASSWORD))
                .fullName(fullName)
                .role(role)
                .enabled(true)
                .build());
    }

    /**
     * Links the first patient to a portal login so the PATIENT role can be
     * demonstrated end to end.
     */
    private void createPatientPortalAccount(Patient patient) {
        User portalUser = createUser("rina.mehta@example.com", patient.getFullName(), UserRole.PATIENT);
        patient.setUser(portalUser);
    }

    private Patient createPatientGraph(PatientSeed seed, User careManager, User actor) {
        LocalDate today = LocalDate.now(clock);
        LocalDate dischargeDate = today.minusDays(seed.daysSinceDischarge());

        Patient patient = patientRepository.save(Patient.builder()
                .medicalRecordNumber(seed.mrn())
                .firstName(seed.firstName())
                .lastName(seed.lastName())
                .dateOfBirth(today.minusYears(seed.age()).minusDays(random.nextInt(360)))
                .phone(seed.phone())
                .email(seed.email())
                .primaryCondition(seed.condition())
                .dischargeDate(dischargeDate)
                .currentRiskLevel(seed.riskLevel())
                .active(true)
                .careManager(careManager)
                .build());

        audit(AuditAction.PATIENT_CREATED, patient.getId(), actor,
                "Patient record created for " + patient.getFullName() + ".");

        for (MedicationSeed medicationSeed : seed.medications()) {
            Medication medication = medicationRepository.save(Medication.builder()
                    .patient(patient)
                    .medicineName(medicationSeed.name())
                    .dosage(medicationSeed.dosage())
                    .frequency(medicationSeed.frequency())
                    .startDate(dischargeDate)
                    .instructions(medicationSeed.instructions())
                    .active(true)
                    .build());
            audit(AuditAction.MEDICATION_ADDED, patient.getId(), actor,
                    medication.getMedicineName() + " " + medication.getDosage() + " added.");
        }

        CarePlanType planType = seed.planType();
        CarePlan carePlan = carePlanRepository.save(CarePlan.builder()
                .patient(patient)
                .startDate(dischargeDate)
                .endDate(dischargeDate.plusDays(planType.getDurationDays()))
                .planType(planType)
                .status(CarePlanStatus.ACTIVE)
                .notes("Generated automatically on discharge.")
                .build());

        audit(AuditAction.CARE_PLAN_CREATED, patient.getId(), actor, planType + " care plan created.");
        audit(AuditAction.PATIENT_DISCHARGED, patient.getId(), actor,
                "Patient discharged on " + dischargeDate + " under a " + planType + " plan.");

        seedFollowUps(patient, carePlan, dischargeDate, today, seed, actor);
        return patient;
    }

    /**
     * Walks the plan's real cadence, completing every touchpoint already in the
     * past so adherence history and the care-journey timeline look lived-in.
     */
    private void seedFollowUps(Patient patient, CarePlan carePlan, LocalDate dischargeDate,
                               LocalDate today, PatientSeed seed, User actor) {
        for (int dayOffset : carePlan.getPlanType().getFollowUpDayOffsets()) {
            LocalDate scheduledDate = dischargeDate.plusDays(dayOffset);
            boolean inPast = !scheduledDate.isAfter(today);

            FollowUpTask task = followUpTaskRepository.save(FollowUpTask.builder()
                    .patient(patient)
                    .carePlan(carePlan)
                    .scheduledDate(scheduledDate)
                    .dayOffset(dayOffset)
                    .type(typeForOffset(dayOffset))
                    .status(FollowUpStatus.SCHEDULED)
                    .title("Day " + dayOffset + " check-in")
                    .build());

            audit(AuditAction.FOLLOW_UP_CREATED, patient.getId(), actor,
                    "Day " + dayOffset + " follow-up scheduled for " + scheduledDate + ".");

            if (!inPast) {
                continue;
            }

            if (seed.missesFollowUpAtDay() != null && seed.missesFollowUpAtDay() == dayOffset) {
                task.setStatus(FollowUpStatus.MISSED);
                audit(AuditAction.FOLLOW_UP_MISSED, patient.getId(), actor,
                        "Day " + dayOffset + " follow-up was not completed.");
                continue;
            }

            completeFollowUp(patient, task, scheduledDate, seed, actor);
        }
    }

    private void completeFollowUp(Patient patient, FollowUpTask task, LocalDate scheduledDate,
                                  PatientSeed seed, User actor) {
        boolean troubled = seed.riskLevel() == RiskLevel.HIGH || seed.riskLevel() == RiskLevel.MEDIUM;
        int missedDoses = troubled ? 1 + random.nextInt(3) : random.nextInt(2);
        boolean symptoms = troubled && random.nextInt(3) == 0;
        boolean refill = random.nextInt(4) == 0;

        PatientResponse response = patientResponseRepository.save(PatientResponse.builder()
                .patient(patient)
                .followUpTask(task)
                .medicationTaken(missedDoses < 3)
                .missedDoses(missedDoses)
                .symptomsReported(symptoms)
                .symptoms(symptoms ? List.of(seed.symptom()) : List.of())
                .refillNeeded(refill)
                .notes(symptoms ? "Patient reported " + seed.symptom() + "." : null)
                .build());

        task.setStatus(FollowUpStatus.COMPLETED);
        task.setCompletedDate(scheduledDate.atStartOfDay().toInstant(java.time.ZoneOffset.UTC));

        audit(AuditAction.RESPONSE_RECEIVED, patient.getId(), actor,
                "Follow-up response received for day " + task.getDayOffset() + " check-in.");

        int expectedDoses = 14 + random.nextInt(14);
        int cappedMissed = Math.min(missedDoses * 2, expectedDoses);
        adherenceEventRepository.save(AdherenceEvent.builder()
                .patient(patient)
                .followUpTask(task)
                .recordedDate(scheduledDate)
                .expectedDoses(expectedDoses)
                .takenDoses(expectedDoses - cappedMissed)
                .missedDoses(cappedMissed)
                .build());

        if (symptoms) {
            seedEscalation(patient, response, actor);
        }
        audit(AuditAction.FOLLOW_UP_COMPLETED, patient.getId(), actor,
                "Day " + task.getDayOffset() + " follow-up completed.");
    }

    private void seedEscalation(Patient patient, PatientResponse response, User actor) {
        riskSignalRepository.save(RiskSignal.builder()
                .patient(patient)
                .patientResponse(response)
                .code(RiskSignalCode.SYMPTOMS_REPORTED)
                .riskLevel(RiskLevel.HIGH)
                .detail(RiskSignalCode.SYMPTOMS_REPORTED.getDescription())
                .requiresHumanReview(true)
                .build());
        audit(AuditAction.RISK_SIGNAL_CREATED, patient.getId(), actor,
                "Operational signal raised: SYMPTOMS_REPORTED.");

        boolean alreadyOpen = escalationRepository.findByPatientIdOrderByCreatedAtDesc(patient.getId())
                .stream().anyMatch(existing -> !existing.isResolved());
        if (alreadyOpen) {
            return;
        }

        escalationRepository.save(Escalation.builder()
                .patient(patient)
                .patientResponse(response)
                .severity(EscalationSeverity.HIGH)
                .reason("HIGH operational risk. Patient self-reported symptoms alongside missed doses. "
                        + "This case has been routed to a care manager for human review.")
                .status(EscalationStatus.PENDING)
                .build());
        audit(AuditAction.ESCALATION_CREATED, patient.getId(), actor,
                "HIGH escalation opened for human review.");
    }

    private FollowUpType typeForOffset(int dayOffset) {
        if (dayOffset <= 1) {
            return FollowUpType.POST_DISCHARGE_CHECK;
        }
        return dayOffset >= 30 ? FollowUpType.REFILL_CHECK : FollowUpType.MEDICATION_REVIEW;
    }

    private void audit(AuditAction action, Long patientId, User actor, String description) {
        auditEventRepository.save(AuditEvent.builder()
                .action(action)
                .patientId(patientId)
                .actorId(actor.getId())
                .actorName(actor.getFullName())
                .description(description)
                .build());
    }

    private List<PatientSeed> patientSeeds() {
        return List.of(
                new PatientSeed("MRN-10024", "Rina", "Mehta", 48, "+91-98200-11223",
                        "rina.mehta@example.com", "Congestive heart failure", 12,
                        CarePlanType.HIGH_RISK, RiskLevel.HIGH, "dizziness", null,
                        List.of(new MedicationSeed("Metoprolol", "25 mg", MedicationFrequency.TWICE_DAILY,
                                        "Take with food."),
                                new MedicationSeed("Furosemide", "40 mg", MedicationFrequency.ONCE_DAILY,
                                        "Take in the morning."))),
                new PatientSeed("MRN-10031", "Daniel", "Okafor", 63, "+44-7700-900412",
                        "daniel.okafor@example.com", "Type 2 diabetes", 20,
                        CarePlanType.STANDARD, RiskLevel.MEDIUM, "fatigue", 7,
                        List.of(new MedicationSeed("Metformin", "500 mg", MedicationFrequency.TWICE_DAILY,
                                "Take with meals."))),
                new PatientSeed("MRN-10047", "Sofia", "Alvarez", 55, "+34-600-112233",
                        "sofia.alvarez@example.com", "Hypertension", 31,
                        CarePlanType.STANDARD, RiskLevel.LOW, "headache", null,
                        List.of(new MedicationSeed("Amlodipine", "5 mg", MedicationFrequency.ONCE_DAILY,
                                "Take at the same time each day."))),
                new PatientSeed("MRN-10052", "Hiroshi", "Tanaka", 71, "+81-90-1234-5678",
                        "hiroshi.tanaka@example.com", "Atrial fibrillation", 8,
                        CarePlanType.HIGH_RISK, RiskLevel.HIGH, "shortness of breath", null,
                        List.of(new MedicationSeed("Apixaban", "5 mg", MedicationFrequency.TWICE_DAILY,
                                        "Do not skip doses."),
                                new MedicationSeed("Digoxin", "125 mcg", MedicationFrequency.ONCE_DAILY,
                                        "Take at the same time daily."))),
                new PatientSeed("MRN-10068", "Amara", "Diallo", 39, "+221-77-123-4567",
                        "amara.diallo@example.com", "Post-operative recovery", 5,
                        CarePlanType.POST_DISCHARGE, RiskLevel.LOW, "nausea", null,
                        List.of(new MedicationSeed("Paracetamol", "500 mg", MedicationFrequency.THREE_TIMES_DAILY,
                                "Do not exceed 4 g in 24 hours."))),
                new PatientSeed("MRN-10073", "Lukas", "Novak", 58, "+420-601-234-567",
                        "lukas.novak@example.com", "Chronic kidney disease", 25,
                        CarePlanType.STANDARD, RiskLevel.MEDIUM, "swelling", 14,
                        List.of(new MedicationSeed("Losartan", "50 mg", MedicationFrequency.ONCE_DAILY,
                                "Monitor blood pressure."))),
                new PatientSeed("MRN-10081", "Grace", "Whitfield", 67, "+1-415-555-0142",
                        "grace.whitfield@example.com", "COPD", 16,
                        CarePlanType.HIGH_RISK, RiskLevel.MEDIUM, "wheezing", null,
                        List.of(new MedicationSeed("Salbutamol", "100 mcg", MedicationFrequency.AS_NEEDED,
                                        "Use as a reliever inhaler."),
                                new MedicationSeed("Tiotropium", "18 mcg", MedicationFrequency.ONCE_DAILY,
                                        "Use the inhaler each morning."))),
                new PatientSeed("MRN-10094", "Omar", "Haddad", 44, "+961-3-123456",
                        "omar.haddad@example.com", "Post myocardial infarction", 3,
                        CarePlanType.HIGH_RISK, RiskLevel.LOW, "chest tightness", null,
                        List.of(new MedicationSeed("Atorvastatin", "40 mg", MedicationFrequency.ONCE_DAILY,
                                        "Take in the evening."),
                                new MedicationSeed("Aspirin", "75 mg", MedicationFrequency.ONCE_DAILY,
                                        "Take after food."))),
                new PatientSeed("MRN-10102", "Ingrid", "Larsen", 52, "+47-400-11223",
                        "ingrid.larsen@example.com", "Asthma", 29,
                        CarePlanType.STANDARD, RiskLevel.NONE, "cough", null,
                        List.of(new MedicationSeed("Budesonide", "200 mcg", MedicationFrequency.TWICE_DAILY,
                                "Rinse mouth after use."))),
                new PatientSeed("MRN-10117", "Kwame", "Mensah", 60, "+233-24-123-4567",
                        "kwame.mensah@example.com", "Heart failure", 18,
                        CarePlanType.STANDARD, RiskLevel.MEDIUM, "dizziness", null,
                        List.of(new MedicationSeed("Ramipril", "5 mg", MedicationFrequency.ONCE_DAILY,
                                "Take at bedtime."))));
    }

    private record PatientSeed(
            String mrn, String firstName, String lastName, int age, String phone, String email,
            String condition, int daysSinceDischarge, CarePlanType planType, RiskLevel riskLevel,
            String symptom, Integer missesFollowUpAtDay, List<MedicationSeed> medications) {
    }

    private record MedicationSeed(
            String name, String dosage, MedicationFrequency frequency, String instructions) {
    }
}
