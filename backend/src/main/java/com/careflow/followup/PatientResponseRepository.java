package com.careflow.followup;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PatientResponseRepository extends JpaRepository<PatientResponse, Long> {

    Optional<PatientResponse> findByFollowUpTaskId(Long followUpTaskId);

    boolean existsByFollowUpTaskId(Long followUpTaskId);

    Optional<PatientResponse> findFirstByPatientIdOrderByCreatedAtDesc(Long patientId);

    List<PatientResponse> findByPatientIdOrderByCreatedAtDesc(Long patientId);
}
