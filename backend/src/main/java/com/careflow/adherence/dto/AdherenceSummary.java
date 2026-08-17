package com.careflow.adherence.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Aggregated medication adherence for a patient.")
public record AdherenceSummary(
        Long patientId,
        String patientName,

        @Schema(example = "30")
        int expectedDoses,

        @Schema(example = "26")
        int takenDoses,

        @Schema(example = "4")
        int missedDoses,

        @Schema(example = "86.67", description = "takenDoses / expectedDoses * 100, rounded to two decimals.")
        double adherencePercentage,

        @Schema(description = "True when adherence sits below the configured operational threshold.")
        boolean belowThreshold,

        @Schema(description = "Chronological adherence readings for charting.")
        List<AdherencePoint> history) {
}
