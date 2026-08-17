package com.careflow.escalation;

import com.careflow.ai.CaseSummaryService;
import com.careflow.common.PageResponse;
import com.careflow.common.enums.EscalationSeverity;
import com.careflow.common.enums.EscalationStatus;
import com.careflow.escalation.dto.AssignEscalationRequest;
import com.careflow.escalation.dto.EscalationResponse;
import com.careflow.escalation.dto.ResolveEscalationRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/escalations")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'CARE_MANAGER')")
@Tag(name = "Escalations", description = "Cases routed to a human care manager for review.")
public class EscalationController {

    private final EscalationService escalationService;

    @GetMapping
    @Operation(summary = "List escalations",
            description = "Filter by status and severity. Unassigned cases stay visible to every care manager.")
    public PageResponse<EscalationResponse> search(
            @RequestParam(required = false) EscalationStatus status,
            @RequestParam(required = false) EscalationSeverity severity,
            @RequestParam(defaultValue = "false") boolean assignedToMe,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return escalationService.search(status, severity, assignedToMe, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an escalation by id")
    public EscalationResponse getById(@PathVariable Long id) {
        return escalationService.getById(id);
    }

    @GetMapping("/{id}/summary")
    @Operation(summary = "Get a briefing for this case",
            description = """
                    A short operational summary to orient the reviewing care manager. Generated
                    from facts already held by the platform; it never diagnoses or recommends
                    treatment, and falls back to a deterministic template when AI is disabled.
                    """)
    public CaseSummaryService.CaseSummary summary(@PathVariable Long id) {
        return escalationService.summarise(id);
    }

    @PostMapping("/{id}/assign")
    @Operation(summary = "Assign an escalation to a care manager")
    public EscalationResponse assign(@PathVariable Long id,
                                     @Valid @RequestBody AssignEscalationRequest request) {
        return escalationService.assign(id, request);
    }

    @PostMapping("/{id}/review")
    @Operation(summary = "Mark an escalation as in review")
    public EscalationResponse markInReview(@PathVariable Long id) {
        return escalationService.markInReview(id);
    }

    @PostMapping("/{id}/resolve")
    @Operation(summary = "Resolve an escalation",
            description = "Resolution notes are mandatory and recorded in the audit trail.")
    public EscalationResponse resolve(@PathVariable Long id,
                                      @Valid @RequestBody ResolveEscalationRequest request) {
        return escalationService.resolve(id, request);
    }
}
