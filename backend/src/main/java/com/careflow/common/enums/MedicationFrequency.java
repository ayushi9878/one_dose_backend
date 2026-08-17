package com.careflow.common.enums;

import lombok.Getter;

/**
 * Dosing cadence for a medication. {@code dosesPerDay} drives expected-dose
 * projection in adherence calculation, so every constant must carry a value.
 */
@Getter
public enum MedicationFrequency {

    ONCE_DAILY(1),
    TWICE_DAILY(2),
    THREE_TIMES_DAILY(3),
    FOUR_TIMES_DAILY(4),
    EVERY_OTHER_DAY(0.5),
    WEEKLY(1.0 / 7.0),
    AS_NEEDED(0);

    private final double dosesPerDay;

    MedicationFrequency(double dosesPerDay) {
        this.dosesPerDay = dosesPerDay;
    }
}
