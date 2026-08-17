package com.careflow.common.enums;

import java.util.List;

/**
 * Care plan template. The follow-up cadence is expressed as day offsets from the
 * discharge date and is the single source of truth for follow-up generation.
 */
public enum CarePlanType {

    STANDARD(List.of(1, 7, 14, 30), 30),
    HIGH_RISK(List.of(1, 3, 7, 14, 30), 30),
    POST_DISCHARGE(List.of(1, 7, 14, 30), 30);

    private final List<Integer> followUpDayOffsets;
    private final int durationDays;

    CarePlanType(List<Integer> followUpDayOffsets, int durationDays) {
        this.followUpDayOffsets = followUpDayOffsets;
        this.durationDays = durationDays;
    }

    public List<Integer> getFollowUpDayOffsets() {
        return followUpDayOffsets;
    }

    public int getDurationDays() {
        return durationDays;
    }
}
