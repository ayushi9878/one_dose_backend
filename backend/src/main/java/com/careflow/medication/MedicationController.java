package com.careflow.medication;

import com.careflow.medication.dto.MedicationRequest;
import com.careflow.medication.dto.MedicationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "Medications", description = "Prescribed medications for a patient.")
public class MedicationController {

    private final MedicationService medicationService;

    @GetMapping("/api/patients/{patientId}/medications")
    @Operation(summary = "List a patient's medications")
    public List<MedicationResponse> listForPatient(@PathVariable Long patientId) {
        return medicationService.listForPatient(patientId);
    }

    @PostMapping("/api/patients/{patientId}/medications")
    @PreAuthorize("hasAnyRole('ADMIN', 'CARE_MANAGER')")
    @Operation(summary = "Add a medication to a patient")
    public ResponseEntity<MedicationResponse> create(@PathVariable Long patientId,
                                                     @Valid @RequestBody MedicationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(medicationService.create(patientId, request));
    }

    @PutMapping("/api/medications/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CARE_MANAGER')")
    @Operation(summary = "Update a medication")
    public MedicationResponse update(@PathVariable Long id,
                                     @Valid @RequestBody MedicationRequest request) {
        return medicationService.update(id, request);
    }

    @DeleteMapping("/api/medications/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CARE_MANAGER')")
    @Operation(summary = "Discontinue a medication",
            description = "Marks the prescription inactive; the record is retained for adherence history.")
    public ResponseEntity<Void> discontinue(@PathVariable Long id) {
        medicationService.discontinue(id);
        return ResponseEntity.noContent().build();
    }
}
