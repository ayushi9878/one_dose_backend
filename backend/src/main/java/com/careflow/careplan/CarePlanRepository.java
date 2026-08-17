package com.careflow.careplan;

import com.careflow.common.enums.CarePlanStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CarePlanRepository extends JpaRepository<CarePlan, Long> {

    List<CarePlan> findByPatientIdOrderByStartDateDesc(Long patientId);

    Optional<CarePlan> findFirstByPatientIdAndStatusOrderByStartDateDesc(Long patientId, CarePlanStatus status);

    long countByStatus(CarePlanStatus status);
}
