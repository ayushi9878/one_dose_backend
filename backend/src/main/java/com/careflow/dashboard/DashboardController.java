package com.careflow.dashboard;

import com.careflow.audit.dto.AuditEventResponse;
import com.careflow.common.PageResponse;
import com.careflow.common.enums.EscalationSeverity;
import com.careflow.common.enums.EscalationStatus;
import com.careflow.common.enums.FollowUpStatus;
import com.careflow.dashboard.dto.AdherenceOverview;
import com.careflow.dashboard.dto.DashboardSummary;
import com.careflow.escalation.EscalationService;
import com.careflow.escalation.dto.EscalationResponse;
import com.careflow.followup.FollowUpService;
import com.careflow.followup.dto.FollowUpResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'CARE_MANAGER')")
@Tag(name = "Dashboard", description = "Aggregated operational metrics.")
public class DashboardController {

    private final DashboardService dashboardService;
    private final FollowUpService followUpService;
    private final EscalationService escalationService;

    @GetMapping("/summary")
    @Operation(summary = "Headline operational metrics")
    public DashboardSummary summary() {
        return dashboardService.summary();
    }

    @GetMapping("/follow-ups")
    @Operation(summary = "Follow-ups for the dashboard timeline",
            description = "Defaults to today's open follow-ups when no date range is supplied.")
    public PageResponse<FollowUpResponse> followUps(
            @RequestParam(required = false) FollowUpStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "10") int size) {
        return followUpService.search(status, from, to,
                PageRequest.of(0, size, Sort.by(Sort.Direction.ASC, "scheduledDate")));
    }

    @GetMapping("/escalations")
    @Operation(summary = "Escalation queue for the dashboard")
    public PageResponse<EscalationResponse> escalations(
            @RequestParam(required = false) EscalationStatus status,
            @RequestParam(required = false) EscalationSeverity severity,
            @RequestParam(defaultValue = "10") int size) {
        return escalationService.search(status, severity, false,
                PageRequest.of(0, size, Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    @GetMapping("/adherence")
    @Operation(summary = "Platform-wide adherence rate and distribution")
    public AdherenceOverview adherence() {
        return dashboardService.adherenceOverview();
    }

    @GetMapping("/activity")
    @Operation(summary = "Most recent workflow activity across all patients")
    public List<AuditEventResponse> recentActivity() {
        return dashboardService.recentActivity();
    }
}
