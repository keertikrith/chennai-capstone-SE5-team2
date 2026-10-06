package com.capstone.etp.notifications;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A crossed price threshold, handed to notifications for delivery. It carries what the message needs
 * and nothing else: there is deliberately no field for a channel, an address or a URL, because only
 * the preferences module decides where a customer is reached.
 *
 * @param accountId  the account that owns the alert (and so receives the message)
 * @param alertId    the alert's id; the idempotency key of the delivery
 * @param symbol     the instrument
 * @param direction  which way the price had to move to cross
 * @param threshold  the level the customer set
 * @param price      the price that crossed it
 * @param observedAt when that price was observed
 */
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
        BELOW
    }
}
