package com.careflow.followup;

import com.careflow.common.enums.FollowUpStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface FollowUpTaskRepository extends JpaRepository<FollowUpTask, Long> {

    List<FollowUpTask> findByPatientIdOrderByScheduledDateAsc(Long patientId);

    Optional<FollowUpTask> findFirstByPatientIdAndStatusInOrderByScheduledDateAsc(
            Long patientId, Collection<FollowUpStatus> statuses);

    long countByScheduledDateAndStatusIn(LocalDate scheduledDate, Collection<FollowUpStatus> statuses);

    List<FollowUpTask> findByScheduledDateBeforeAndStatusIn(
            LocalDate cutoff, Collection<FollowUpStatus> statuses);

    long countByScheduledDateBeforeAndStatusIn(
            LocalDate cutoff, Collection<FollowUpStatus> statuses);

    @Query("""
            SELECT f FROM FollowUpTask f
            JOIN FETCH f.patient
            WHERE (:status IS NULL OR f.status = :status)
              AND (:from IS NULL OR f.scheduledDate >= :from)
              AND (:to IS NULL OR f.scheduledDate <= :to)
            """)
    Page<FollowUpTask> searchWithPatient(@Param("status") FollowUpStatus status,
                                         @Param("from") LocalDate from,
                                         @Param("to") LocalDate to,
                                         Pageable pageable);

    long countByStatus(FollowUpStatus status);
}
