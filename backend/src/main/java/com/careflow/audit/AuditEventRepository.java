package com.careflow.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

    Page<AuditEvent> findByPatientIdOrderByCreatedAtDesc(Long patientId, Pageable pageable);

    List<AuditEvent> findTop20ByOrderByCreatedAtDesc();
}
