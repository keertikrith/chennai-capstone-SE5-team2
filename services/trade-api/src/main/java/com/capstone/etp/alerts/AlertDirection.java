package com.capstone.etp.alerts;

import java.math.BigDecimal;


public enum AlertDirection {
    ABOVE,
    BELOW,
    GT,
    GTE,
    LT,
    LTE,
    EQ;

    public boolean isCrossedBy(BigDecimal price, BigDecimal threshold) {
        int comparison = price.compareTo(threshold);
        return switch (this) {
            case ABOVE, GTE -> comparison >= 0;
            case BELOW, LTE -> comparison <= 0;
            case GT -> comparison > 0;
            case LT -> comparison < 0;
            case EQ -> comparison == 0;
        };
    }

    public String relationText() {
        return switch (this) {
            case ABOVE -> "above";
            case BELOW -> "below";
            case GT -> "greater than";
            case GTE -> "greater than or equal to";
            case LT -> "less than";
            case LTE -> "less than or equal to";
            case EQ -> "equal to";
        };
    }

    public AlertDirection normalised() {
        return switch (this) {
            case ABOVE -> GTE;
            case BELOW -> LTE;
            default -> this;
        };
    }
}
