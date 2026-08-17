package com.careflow.followup;

import com.careflow.common.PageResponse;
import com.careflow.common.enums.FollowUpStatus;
import com.careflow.followup.dto.FollowUpResponse;
import com.careflow.followup.dto.PatientResponseRequest;
import com.careflow.followup.dto.PatientResponseResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "Follow-ups", description = "Scheduled follow-ups and patient responses.")
public class FollowUpController {

    private final FollowUpService followUpService;

    @GetMapping("/api/patients/{patientId}/follow-ups")
    @Operation(summary = "List a patient's follow-ups",
            description = "Ordered by scheduled date, forming the care journey timeline.")
    public List<FollowUpResponse> listForPatient(@PathVariable Long patientId) {
        return followUpService.listForPatient(patientId);
    }

    @GetMapping("/api/follow-ups")
    @PreAuthorize("hasAnyRole('ADMIN', 'CARE_MANAGER')")
    @Operation(summary = "Search follow-ups across patients")
    public PageResponse<FollowUpResponse> search(
            @RequestParam(required = false) FollowUpStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 20, sort = "scheduledDate", direction = Sort.Direction.ASC)
            Pageable pageable) {
        return followUpService.search(status, from, to, pageable);
    }

    @GetMapping("/api/follow-ups/{id}")
    @Operation(summary = "Get a follow-up by id")
    public FollowUpResponse getById(@PathVariable Long id) {
        return followUpService.getById(id);
    }

    @PostMapping("/api/follow-ups/{id}/response")
    @Operation(summary = "Submit a follow-up response",
            description = """
                    Stores the response, records an adherence event, runs the deterministic
                    operational risk rules and opens a case for human review when required —
                    all in a single transaction. The response body shows every step taken.
                    """)
    public ResponseEntity<PatientResponseResult> submitResponse(
            @PathVariable Long id,
            @Valid @RequestBody PatientResponseRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(followUpService.submitResponse(id, request));
    }
}
