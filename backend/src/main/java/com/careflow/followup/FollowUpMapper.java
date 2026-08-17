package com.careflow.followup;

import com.careflow.followup.dto.FollowUpResponse;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;

@Component
public class FollowUpMapper {

    private final Clock clock;

    public FollowUpMapper(Clock clock) {
        this.clock = clock;
    }

    public FollowUpResponse toResponse(FollowUpTask task) {
        return new FollowUpResponse(
                task.getId(),
                task.getPatient().getId(),
                task.getPatient().getFullName(),
                task.getCarePlan() != null ? task.getCarePlan().getId() : null,
                task.getScheduledDate(),
                task.getCompletedDate(),
                task.getStatus(),
                task.getType(),
                task.getDayOffset(),
                task.getTitle(),
                isOverdue(task),
                task.getCreatedAt());
    }

    private boolean isOverdue(FollowUpTask task) {
        return task.isOpen() && task.getScheduledDate().isBefore(LocalDate.now(clock));
    }
}
