package com.capstone.etp.alerts;

import java.math.BigDecimal;

/**
 * Which way the price has to travel to cross the threshold.
 *
 * ABOVE fires on the first quote at or above the level, BELOW on the first at or below it. An
 * alert is created only when the price has not already crossed (see {@link AlertService}), so
 * "crossed" always means the price moved through the level after the customer asked.
 */
public enum AlertDirection {
    ABOVE,
    BELOW;

    public boolean isCrossedBy(BigDecimal price, BigDecimal threshold) {
        int comparison = price.compareTo(threshold);
        return this == ABOVE ? comparison >= 0 : comparison <= 0;
    }
}
