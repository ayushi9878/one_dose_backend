package com.careflow.adherence.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@Schema(description = "A single adherence reading, used to plot the adherence trend.")
public record AdherencePoint(
        LocalDate date,
        int expectedDoses,
        int takenDoses,
        int missedDoses,
        double adherencePercentage) {
}
