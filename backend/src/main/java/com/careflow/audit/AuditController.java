package com.careflow.audit;

import com.careflow.audit.dto.AuditEventResponse;
import com.careflow.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Audit", description = "Immutable trail of workflow actions.")
public class AuditController {

    private final AuditQueryService auditQueryService;

    @GetMapping("/api/patients/{patientId}/audit-log")
    @PreAuthorize("hasAnyRole('ADMIN', 'CARE_MANAGER')")
    @Operation(summary = "Get a patient's audit trail")
    public PageResponse<AuditEventResponse> listForPatient(
            @PathVariable Long patientId,
            @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return auditQueryService.listForPatient(patientId, pageable);
    }
}
