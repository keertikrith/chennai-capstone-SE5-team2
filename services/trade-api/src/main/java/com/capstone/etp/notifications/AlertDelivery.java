package com.capstone.etp.notifications;

/**
 * THE SEAM between watchlists/price alerts and customer notifications.
 *
 * Watchlists calls {@link #deliver} when a threshold has been crossed and does nothing else about
 * telling the customer: notifications resolves the channel (through preferences), records the
 * message, and sends it. Watchlists never asks for a channel and never reads a notification table.
 *
 * <p>This is a Java interface, not an HTTP route. Publishing it as a route would let a customer's
 * token send itself (or anyone) an arbitrary message; nothing under {@code /api/v1/} calls it.
 *
 * <h3>Contract</h3>
 * <ul>
 *   <li><b>Idempotent on {@link AlertDeliveryRequest#alertId()}.</b> Calling it again for the same
 *       alert returns the original receipt with {@link DeliveryReceipt#duplicate()} set and records
 *       nothing new. A retry after a crash or a timeout therefore cannot message the customer twice.</li>
 *   <li><b>Returns once the message is durably recorded for delivery</b> (status QUEUED), not once the
 *       e-mail/SMS provider has accepted it. The provider is slow and sometimes down; the alert must
 *       not wait for it. Delivery state after that is the notification's own (QUEUED, SENT, FAILED).</li>
 *   <li><b>Raises {@link AlertDeliveryException}</b> when the message could not be recorded (the
 *       database is unreachable, the content was refused). The caller keeps the alert, marks the
 *       handover as failed, and retries; the customer asked to be told, so it is never dropped.</li>
 * </ul>
 */
public interface AlertDelivery {

    DeliveryReceipt deliver(AlertDeliveryRequest request) throws AlertDeliveryException;
}
