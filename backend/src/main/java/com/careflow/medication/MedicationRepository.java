package com.careflow.medication;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MedicationRepository extends JpaRepository<Medication, Long> {

    List<Medication> findByPatientIdOrderByActiveDescMedicineNameAsc(Long patientId);

    List<Medication> findByPatientIdAndActiveTrue(Long patientId);

    long countByPatientIdAndActiveTrue(Long patientId);
}
