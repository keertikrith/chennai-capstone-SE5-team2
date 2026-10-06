package com.capstone.etp.preferences;

/**
 * The ways a customer can ask to be reached. A fixed set on purpose: a channel names a delivery
 * mechanism the platform operates, never a destination the customer supplies. There is no value for
 * "webhook" and no field for a URL, so the platform cannot be told to post to an address of a
 * customer's choosing.
 */
public enum AlertChannel {
    EMAIL,
    SMS,
    PUSH
}
