package com.capstone.etp.notifications;

/** What a notification is about. The first three come from {@code trade-events}; PRICE_ALERT from watchlists. */
public enum NotificationKind {
    ORDER_FILLED,
    ORDER_REJECTED,
    ORDER_CANCELLED,
    PRICE_ALERT
}
