package com.careflow.careplan;

import com.careflow.careplan.dto.CarePlanResponse;
import org.springframework.stereotype.Component;

@Component
public class CarePlanMapper {

    public CarePlanResponse toResponse(CarePlan carePlan) {
        return new CarePlanResponse(
                carePlan.getId(),
                carePlan.getPatient().getId(),
                carePlan.getPatient().getFullName(),
                carePlan.getStartDate(),
                carePlan.getEndDate(),
                carePlan.getPlanType(),
                carePlan.getStatus(),
                carePlan.getNotes(),
                carePlan.getCreatedAt(),
                carePlan.getUpdatedAt());
    }
}
