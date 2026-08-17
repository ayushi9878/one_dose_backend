package com.careflow.common.enums;

import lombok.Getter;

/**
 * Operational risk tiers. These describe workflow urgency only — they are not a
 * clinical assessment and must never be presented as a diagnosis.
 */
@Getter
public enum RiskLevel {

    NONE(0),
    LOW(1),
    MEDIUM(2),
    HIGH(3);

    private final int severity;

    RiskLevel(int severity) {
        this.severity = severity;
    }

    public boolean isAtLeast(RiskLevel other) {
        return this.severity >= other.severity;
    }

    public RiskLevel escalateTo(RiskLevel candidate) {
        return candidate.severity > this.severity ? candidate : this;
    }
}
