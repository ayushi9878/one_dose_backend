package com.careflow.escalation;

import com.careflow.common.enums.EscalationSeverity;
import com.careflow.common.enums.EscalationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface EscalationRepository extends JpaRepository<Escalation, Long> {

    @Query("""
            SELECT e FROM Escalation e
            JOIN FETCH e.patient
            WHERE (:status IS NULL OR e.status = :status)
              AND (:severity IS NULL OR e.severity = :severity)
              AND (:careManagerId IS NULL OR e.assignedCareManager.id = :careManagerId)
            """)
    Page<Escalation> search(@Param("status") EscalationStatus status,
                            @Param("severity") EscalationSeverity severity,
                            @Param("careManagerId") Long careManagerId,
                            Pageable pageable);

    List<Escalation> findByPatientIdOrderByCreatedAtDesc(Long patientId);

    long countByStatusIn(Collection<EscalationStatus> statuses);

    long countBySeverityAndStatusIn(EscalationSeverity severity, Collection<EscalationStatus> statuses);
}
