package com.capstone.etp.notifications;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.UUID;


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
        return switch (r.direction()) {
            case ABOVE -> "Price alert: " + r.symbol() + " is above " + plain(r.threshold());
            case BELOW -> "Price alert: " + r.symbol() + " is below " + plain(r.threshold());
            case EQUALS -> "Price alert: " + r.symbol() + " is equal to " + plain(r.threshold());
        };
    }

    static String body(AlertDeliveryRequest r) {
        return switch (r.direction()) {
            case ABOVE -> r.symbol() + " rose above " + plain(r.threshold())
                    + ". The latest price is " + plain(r.price()) + ".";
            case BELOW -> r.symbol() + " fell below " + plain(r.threshold())
                    + ". The latest price is " + plain(r.price()) + ".";
            case EQUALS -> r.symbol() + " reached " + plain(r.threshold())
                    + ". The latest price is " + plain(r.price()) + ".";
        };
    }

    private static String plain(BigDecimal value) {
        return value == null ? "n/a" : value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
