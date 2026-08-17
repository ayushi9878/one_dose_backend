package com.careflow.careplan;

import com.careflow.careplan.dto.CarePlanRequest;
import com.careflow.careplan.dto.CarePlanResponse;
import com.careflow.careplan.dto.DischargeRequest;
import com.careflow.careplan.dto.DischargeResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "Care plans", description = "Care plans and the post-discharge workflow.")
public class CarePlanController {

    private final CarePlanService carePlanService;

    @GetMapping("/api/patients/{patientId}/care-plan")
    @Operation(summary = "Get the active care plan for a patient")
    public CarePlanResponse getActive(@PathVariable Long patientId) {
        return carePlanService.getActiveForPatient(patientId);
    }

    @GetMapping("/api/patients/{patientId}/care-plans")
    @Operation(summary = "List all care plans for a patient")
    public List<CarePlanResponse> list(@PathVariable Long patientId) {
        return carePlanService.listForPatient(patientId);
    }

    @PostMapping("/api/patients/{patientId}/care-plan")
    @PreAuthorize("hasAnyRole('ADMIN', 'CARE_MANAGER')")
    @Operation(summary = "Create a care plan",
            description = "Any existing active plan is completed so a patient holds one active plan at a time.")
    public ResponseEntity<CarePlanResponse> create(@PathVariable Long patientId,
                                                   @Valid @RequestBody CarePlanRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(carePlanService.create(patientId, request));
    }

    @PutMapping("/api/care-plans/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CARE_MANAGER')")
    @Operation(summary = "Update a care plan")
    public CarePlanResponse update(@PathVariable Long id, @Valid @RequestBody CarePlanRequest request) {
        return carePlanService.update(id, request);
    }

    @PostMapping("/api/patients/{patientId}/discharge")
    @PreAuthorize("hasAnyRole('ADMIN', 'CARE_MANAGER')")
    @Operation(summary = "Discharge a patient",
            description = """
                    Records the discharge, opens a post-discharge care plan and generates the
                    follow-up schedule for the chosen plan type in a single transaction.
                    STANDARD schedules days 1/7/14/30; HIGH_RISK additionally schedules day 3.
                    """)
    public ResponseEntity<DischargeResponse> discharge(@PathVariable Long patientId,
                                                       @Valid @RequestBody DischargeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(carePlanService.discharge(patientId, request));
    }
}
