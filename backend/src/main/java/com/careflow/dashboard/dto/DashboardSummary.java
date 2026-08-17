package com.careflow.dashboard.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Headline operational metrics for the dashboard.")
public record DashboardSummary(
        long totalPatients,
        long activeCarePlans,
        long todayFollowUps,
        double adherenceRate,
        long pendingEscalations,
        long highRiskPatients,
        long overdueFollowUps) {
}
