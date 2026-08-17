package com.careflow.patient;

import com.careflow.common.PageResponse;
import com.careflow.common.enums.RiskLevel;
import com.careflow.patient.dto.PatientRequest;
import com.careflow.patient.dto.PatientResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/api/patients")
@RequiredArgsConstructor
@Tag(name = "Patients", description = "Patient roster and demographic records.")
public class PatientController {

    private final PatientService patientService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'CARE_MANAGER')")
    @Operation(summary = "Search patients",
            description = "Paginated roster search. Care managers see only their assigned caseload.")
    public PageResponse<PatientResponse> search(
            @Parameter(description = "Matches first name, last name or medical record number.")
            @RequestParam(required = false) String search,
            @RequestParam(required = false) RiskLevel riskLevel,
            @RequestParam(required = false) Boolean active,
            @PageableDefault(size = 20, sort = "lastName", direction = Sort.Direction.ASC)
            Pageable pageable) {
        return patientService.search(search, riskLevel, active, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a patient by id")
    public PatientResponse getById(@PathVariable Long id) {
        return patientService.getById(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'CARE_MANAGER')")
    @Operation(summary = "Register a new patient")
    public ResponseEntity<PatientResponse> create(@Valid @RequestBody PatientRequest request) {
        PatientResponse created = patientService.create(request);
        return ResponseEntity
                .created(UriComponentsBuilder.fromPath("/api/patients/{id}")
                        .buildAndExpand(created.id()).toUri())
                .body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CARE_MANAGER')")
    @Operation(summary = "Update a patient record")
    public PatientResponse update(@PathVariable Long id, @Valid @RequestBody PatientRequest request) {
        return patientService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Deactivate a patient",
            description = "Soft delete. The record is retained so care history and audit trail stay intact.")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        patientService.deactivate(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
