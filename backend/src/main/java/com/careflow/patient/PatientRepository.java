package com.careflow.patient;

import com.careflow.common.enums.RiskLevel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PatientRepository extends JpaRepository<Patient, Long> {

    Optional<Patient> findByMedicalRecordNumber(String medicalRecordNumber);

    boolean existsByMedicalRecordNumber(String medicalRecordNumber);

    Optional<Patient> findByUserId(Long userId);

    @Query("""
            SELECT p FROM Patient p
            WHERE (:search IS NULL
                   OR LOWER(p.firstName) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(p.lastName) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(p.medicalRecordNumber) LIKE LOWER(CONCAT('%', :search, '%')))
              AND (:riskLevel IS NULL OR p.currentRiskLevel = :riskLevel)
              AND (:careManagerId IS NULL OR p.careManager.id = :careManagerId)
              AND (:active IS NULL OR p.active = :active)
            """)
    Page<Patient> search(@Param("search") String search,
                         @Param("riskLevel") RiskLevel riskLevel,
                         @Param("careManagerId") Long careManagerId,
                         @Param("active") Boolean active,
                         Pageable pageable);

    long countByActiveTrue();

    long countByCurrentRiskLevel(RiskLevel riskLevel);
}
