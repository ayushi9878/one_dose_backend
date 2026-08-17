package com.careflow.common.enums;

public enum EscalationSeverity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL;

    public static EscalationSeverity fromRiskLevel(RiskLevel riskLevel) {
        return switch (riskLevel) {
            case HIGH -> HIGH;
            case MEDIUM -> MEDIUM;
            case LOW, NONE -> LOW;
        };
    }
}
