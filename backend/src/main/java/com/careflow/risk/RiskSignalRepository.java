package com.careflow.risk;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RiskSignalRepository extends JpaRepository<RiskSignal, Long> {

    List<RiskSignal> findByPatientIdOrderByCreatedAtDesc(Long patientId);

    List<RiskSignal> findByPatientResponseIdOrderByCreatedAtDesc(Long patientResponseId);
}
