package com.capstone.etp.alerts;

import com.capstone.etp.notifications.AlertDelivery;
import com.capstone.etp.notifications.AlertDeliveryRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;


@Slf4j
@Component
class AlertHandover {

    private final AlertDelivery delivery;
    private final AlertMapper mapper;

    AlertHandover(AlertDelivery delivery, AlertMapper mapper) {
        this.delivery = delivery;
        this.mapper = mapper;
    }

    /** @return true when notifications has the message */
    boolean handOver(long alertId, long accountId, String symbol, AlertDirection direction,
                     BigDecimal threshold, BigDecimal price, Instant observedAt) {
        try {
            delivery.deliver(new AlertDeliveryRequest(
                    accountId,
                    alertId,
                    symbol,
                    toDeliveryDirection(direction),
                    threshold,
                    price,
                    observedAt));
            mapper.markHandedOver(alertId);
            log.info("[ALERTS] alert {} handed to notifications", alertId);
            return true;
        } catch (RuntimeException e) {
            log.error("[ALERTS] hand-over of alert {} failed ({}); it will be retried",
                    alertId, e.getClass().getSimpleName());
            try {
                mapper.markHandoverFailed(alertId);
            } catch (RuntimeException inner) {
                // The alert stays PENDING_HANDOVER, which the retrier also picks up.
                log.error("[ALERTS] could not record the failed hand-over of alert {}", alertId);
            }
            return false;
        }
    }

    private static AlertDeliveryRequest.Direction toDeliveryDirection(AlertDirection direction) {
        return switch (direction.normalised()) {
            case GT, GTE -> AlertDeliveryRequest.Direction.ABOVE;
            case LT, LTE -> AlertDeliveryRequest.Direction.BELOW;
            case EQ -> AlertDeliveryRequest.Direction.EQUALS;
            default -> throw new IllegalStateException("Unexpected alert direction: " + direction);
        };
    }
}
