package com.careflow.audit;

import com.careflow.audit.dto.AuditEventResponse;
import com.careflow.common.PageResponse;
import com.careflow.patient.PatientService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Read side of the audit trail, kept separate from {@link AuditService} so the
 * write path stays free of query concerns.
 */
@Service
@RequiredArgsConstructor
public class AuditQueryService {

    private final AuditEventRepository auditEventRepository;
    private final PatientService patientService;

    @Transactional(readOnly = true)
    public PageResponse<AuditEventResponse> listForPatient(Long patientId, Pageable pageable) {
        patientService.requireAccessiblePatient(patientId);
        return PageResponse.from(
                auditEventRepository.findByPatientIdOrderByCreatedAtDesc(patientId, pageable),
                this::toResponse);
    }

    @Transactional(readOnly = true)
    public List<AuditEventResponse> recentActivity() {
        return auditEventRepository.findTop20ByOrderByCreatedAtDesc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private AuditEventResponse toResponse(AuditEvent event) {
        return new AuditEventResponse(
                event.getId(),
                event.getPatientId(),
                event.getActorId(),
                event.getActorName(),
                event.getAction(),
                event.getDescription(),
                event.getMetadata(),
                event.getCreatedAt());
    }
}
