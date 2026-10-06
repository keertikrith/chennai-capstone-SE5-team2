package com.capstone.etp.alerts;

import com.capstone.etp.notifications.AlertDelivery;
import com.capstone.etp.notifications.AlertDeliveryRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Hands a fired alert to notifications through the published {@link AlertDelivery} interface and
 * records what happened on the alert.
 *
 * <p><b>What happens when the hand-over raises.</b> The alert has already fired (that is a fact
 * about the price) and stays TRIGGERED. Its delivery state becomes HANDOVER_FAILED and the
 * {@link AlertHandoverRetrier} calls again, up to a limit. The call is idempotent on the alert id,
 * so retrying cannot message the customer twice. The customer sees the state on the alert
 * ("fired, not yet handed over") instead of silence, and an alert is never silently dropped.
 */
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
                    direction == AlertDirection.ABOVE
                            ? AlertDeliveryRequest.Direction.ABOVE
                            : AlertDeliveryRequest.Direction.BELOW,
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
}
