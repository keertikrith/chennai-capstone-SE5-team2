package com.capstone.etp.alerts;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * An alert as the customer sees it. {@code status} says whether it has fired; for a fired alert
 * {@code deliveryState} says whether it was handed to notifications, so an alert is never silent
 * about whether the customer was told.
 */
public record AlertResponse(
        Long id,
        String symbol,
        AlertDirection direction,
        BigDecimal threshold,
        AlertStatus status,
        DeliveryState deliveryState,
        BigDecimal triggeredPrice,
        Instant triggeredAt,
        Instant createdAt) {

    static AlertResponse from(PriceAlert alert) {
        return new AlertResponse(
                alert.getId(),
                alert.getSymbol(),
                alert.getDirection(),
                alert.getThreshold(),
                alert.getStatus(),
                alert.getDeliveryState(),
                alert.getTriggeredPrice(),
                alert.getTriggeredAt(),
                alert.getCreatedAt());
    }
}
