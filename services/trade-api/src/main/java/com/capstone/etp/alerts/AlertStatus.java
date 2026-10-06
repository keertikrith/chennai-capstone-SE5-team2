package com.capstone.etp.alerts;

/**
 * ACTIVE until the price crosses the level, then TRIGGERED once (an alert deactivates itself: it never
 * fires again, so a price hovering around the level cannot send forty messages in a minute).
 * CANCELLED when the customer withdrew it.
 */
public enum AlertStatus {
    ACTIVE,
    TRIGGERED,
    CANCELLED
}
