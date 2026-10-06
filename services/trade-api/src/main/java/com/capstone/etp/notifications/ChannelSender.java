package com.capstone.etp.notifications;

import com.capstone.etp.preferences.AlertChannel;

/**
 * The last mile for one channel. There is exactly one sender per {@link AlertChannel} and the set is
 * fixed in code: the platform decides which providers it talks to, and a customer cannot add one.
 * A sender is handed the destination for ONE message (read from the profile by preferences) and
 * never a URL or host taken from a request, which is what keeps the module free of server-side
 * request forgery.
 */
interface ChannelSender {

    AlertChannel channel();

    /** @throws DeliveryException when the provider refused or could not be reached */
    Mode send(OutboundMessage message) throws DeliveryException;

    /** Whether the message really left the platform, or was only logged because the channel has no provider here. */
    enum Mode {
        LIVE,
        STUB
    }

    /** A provider failure. Carries no destination and no message text. */
    class DeliveryException extends Exception {
        DeliveryException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
