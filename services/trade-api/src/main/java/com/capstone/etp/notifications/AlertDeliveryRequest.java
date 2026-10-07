package com.capstone.etp.notifications;

import java.math.BigDecimal;
import java.time.Instant;


public record AlertDeliveryRequest(
        long accountId,
        long alertId,
        String symbol,
        Direction direction,
        BigDecimal threshold,
        BigDecimal price,
        Instant observedAt) {

    /** The way the price had to move to cross the threshold. Owned here so notifications needs nothing from alerts. */
    public enum Direction {
        ABOVE,
        BELOW,
        EQUALS
    }
}
