package com.careflow.dashboard.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Platform-wide adherence, with the distribution used by the dashboard chart.")
public record AdherenceOverview(

        @Schema(example = "87.4")
        double overallAdherenceRate,

        @Schema(description = "Configured threshold below which adherence is treated as a risk signal.")
        int lowThresholdPercentage,

        long patientsBelowThreshold,

        @Schema(description = "Patient counts bucketed by adherence band.")
        List<AdherenceBand> distribution) {

    @Schema(description = "A single adherence band for charting.")
    public record AdherenceBand(String label, long patientCount) {
    }
}
