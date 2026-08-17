package com.careflow.dashboard;

import com.careflow.adherence.AdherenceEventRepository;
import com.careflow.audit.AuditQueryService;
import com.careflow.audit.dto.AuditEventResponse;
import com.careflow.careplan.CarePlanRepository;
import com.careflow.common.enums.CarePlanStatus;
import com.careflow.common.enums.EscalationStatus;
import com.careflow.common.enums.FollowUpStatus;
import com.careflow.common.enums.RiskLevel;
import com.careflow.config.CareFlowProperties;
import com.careflow.dashboard.dto.AdherenceOverview;
import com.careflow.dashboard.dto.DashboardSummary;
import com.careflow.escalation.EscalationRepository;
import com.careflow.followup.FollowUpTaskRepository;
import com.careflow.patient.PatientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final List<EscalationStatus> OPEN_ESCALATIONS =
            List.of(EscalationStatus.PENDING, EscalationStatus.ASSIGNED, EscalationStatus.IN_REVIEW);
    private static final List<FollowUpStatus> OPEN_FOLLOW_UPS =
            List.of(FollowUpStatus.SCHEDULED, FollowUpStatus.PENDING);

    private final PatientRepository patientRepository;
    private final CarePlanRepository carePlanRepository;
    private final FollowUpTaskRepository followUpTaskRepository;
    private final EscalationRepository escalationRepository;
    private final AdherenceEventRepository adherenceEventRepository;
    private final AuditQueryService auditQueryService;
    private final CareFlowProperties properties;
    private final Clock clock;

    @Transactional(readOnly = true)
    public DashboardSummary summary() {
        LocalDate today = LocalDate.now(clock);
        Double overallAdherence = adherenceEventRepository.calculateOverallAdherencePercentage();

        return new DashboardSummary(
                patientRepository.countByActiveTrue(),
                carePlanRepository.countByStatus(CarePlanStatus.ACTIVE),
                followUpTaskRepository.countByScheduledDateAndStatusIn(today, OPEN_FOLLOW_UPS),
                round(overallAdherence),
                escalationRepository.countByStatusIn(OPEN_ESCALATIONS),
                patientRepository.countByCurrentRiskLevel(RiskLevel.HIGH),
                followUpTaskRepository.countByScheduledDateBeforeAndStatusIn(today, OPEN_FOLLOW_UPS));
    }

    @Transactional(readOnly = true)
    public List<AuditEventResponse> recentActivity() {
        return auditQueryService.recentActivity();
    }

    /**
     * Buckets each patient's adherence percentage into fixed bands so the
     * dashboard chart has a stable set of categories regardless of cohort size.
     */
    @Transactional(readOnly = true)
    public AdherenceOverview adherenceOverview() {
        int threshold = properties.getAdherence().getLowThresholdPercentage();
        List<Double> perPatient = adherenceEventRepository.findAdherencePercentagePerPatient();

        long below60 = countWithin(perPatient, 0, 60);
        long below80 = countWithin(perPatient, 60, 80);
        long below95 = countWithin(perPatient, 80, 95);
        long atLeast95 = countWithin(perPatient, 95, 101);

        return new AdherenceOverview(
                round(adherenceEventRepository.calculateOverallAdherencePercentage()),
                threshold,
                perPatient.stream().filter(value -> value != null && value < threshold).count(),
                List.of(
                        new AdherenceOverview.AdherenceBand("Below 60%", below60),
                        new AdherenceOverview.AdherenceBand("60-79%", below80),
                        new AdherenceOverview.AdherenceBand("80-94%", below95),
                        new AdherenceOverview.AdherenceBand("95%+", atLeast95)));
    }

    private long countWithin(List<Double> values, double lowerInclusive, double upperExclusive) {
        return values.stream()
                .filter(value -> value != null && value >= lowerInclusive && value < upperExclusive)
                .count();
    }

    private double round(Double value) {
        if (value == null) {
            return 0d;
        }
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
