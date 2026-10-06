package com.capstone.etp.notifications;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * The notifications side of the {@link AlertDelivery} seam. An alert becomes one notification whose
 * idempotency key is derived from the alert's id, so handing the same alert over twice (a retry
 * after a timeout, a restart between "fired" and "handed over") records one message.
 */
@Slf4j
@Service
@RequiredArgsConstructor
class NotificationAlertDelivery implements AlertDelivery {

    private final NotificationService notifications;

    @Override
    public DeliveryReceipt deliver(AlertDeliveryRequest request) throws AlertDeliveryException {
        try {
            NotificationService.Enqueued result = notifications.enqueue(
                    eventIdFor(request.alertId()),
                    request.accountId(),
                    NotificationKind.PRICE_ALERT,
                    subject(request),
                    body(request));
            return new DeliveryReceipt(result.notificationId(), !result.created());
        } catch (ContentGuard.UnsafeContentException e) {
            throw new AlertDeliveryException(e.getMessage(), e);
        } catch (RuntimeException e) {
            // The alert stays with the caller, who will try again; the customer asked to be told.
            log.error("[NOTIFY] alert {} could not be recorded for delivery: {}", request.alertId(), e.getMessage());
            throw new AlertDeliveryException("The alert could not be recorded for delivery", e);
        }
    }

    /** The same alert always maps to the same id. */
    static UUID eventIdFor(long alertId) {
        return UUID.nameUUIDFromBytes(("price-alert:" + alertId).getBytes(StandardCharsets.UTF_8));
    }

    static String subject(AlertDeliveryRequest r) {
        return "Price alert: " + r.symbol() + (r.direction() == AlertDeliveryRequest.Direction.ABOVE
                ? " is above " : " is below ") + plain(r.threshold());
    }

    static String body(AlertDeliveryRequest r) {
        return r.symbol() + (r.direction() == AlertDeliveryRequest.Direction.ABOVE
                ? " rose above " : " fell below ")
                + plain(r.threshold()) + ". The latest price is " + plain(r.price()) + ".";
    }

    private static String plain(BigDecimal value) {
        return value == null ? "n/a" : value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
