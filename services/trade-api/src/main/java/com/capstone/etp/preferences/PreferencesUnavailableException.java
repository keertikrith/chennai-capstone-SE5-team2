package com.capstone.etp.preferences;

/**
 * Raised by {@link ChannelResolver#resolve} only when the preference store itself cannot be read
 * (the database is down). It is not the answer for "nothing stored" or "no usable contact": those
 * are ordinary results. A caller that gets this keeps what it was about to send and tries again.
 */
public class PreferencesUnavailableException extends RuntimeException {

    public PreferencesUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
