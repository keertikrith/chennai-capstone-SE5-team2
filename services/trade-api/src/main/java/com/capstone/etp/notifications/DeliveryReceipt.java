package com.capstone.etp.notifications;

/**
 * What {@link AlertDelivery#deliver} answers.
 *
 * @param notificationId the notification the alert became
 * @param duplicate      true when this alert had already been handed over and nothing new was recorded
 */
public record DeliveryReceipt(long notificationId, boolean duplicate) {
}
