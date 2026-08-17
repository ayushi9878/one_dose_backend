package com.careflow.adherence;

import com.careflow.adherence.dto.AdherenceSummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Adherence", description = "Medication adherence, calculated server-side from dose records.")
public class AdherenceController {

    private final AdherenceService adherenceService;

    @GetMapping("/api/patients/{patientId}/adherence")
    @Operation(summary = "Get a patient's adherence summary",
            description = "Percentages are derived from stored adherence events; clients never supply them.")
    public AdherenceSummary getForPatient(@PathVariable Long patientId) {
        return adherenceService.getForPatient(patientId);
    }
}
