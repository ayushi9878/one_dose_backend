package com.careflow.audit;

import com.careflow.common.enums.AuditAction;
import com.careflow.security.CareFlowUserDetails;
import com.careflow.security.CurrentUserProvider;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Writes the append-only workflow audit trail.
 *
 * <p>Audit rows join the caller's transaction so an action and its audit record
 * commit or roll back together — a recorded event must always correspond to a
 * change that actually happened.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditEventRepository auditEventRepository;
    private final CurrentUserProvider currentUserProvider;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(AuditAction action, Long patientId, String description) {
        record(action, patientId, description, Map.of());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(AuditAction action, Long patientId, String description, Map<String, ?> metadata) {
        AuditEvent event = AuditEvent.builder()
                .action(action)
                .patientId(patientId)
                .description(description)
                .metadata(serialize(metadata))
                .build();

        currentUserProvider.find().ifPresent(actor -> {
            event.setActorId(actor.getId());
            event.setActorName(actor.getFullName());
        });

        auditEventRepository.save(event);
    }

    /**
     * Used by background jobs and seeding, where no user is bound to the thread.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordSystemAction(AuditAction action, Long patientId, String description,
                                   Map<String, ?> metadata) {
        auditEventRepository.save(AuditEvent.builder()
                .action(action)
                .patientId(patientId)
                .description(description)
                .actorName("system")
                .metadata(serialize(metadata))
                .build());
    }

    private String serialize(Map<String, ?> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException ex) {
            log.warn("Dropping unserialisable audit metadata for action; keeping the audit row.", ex);
            return null;
        }
    }
}
