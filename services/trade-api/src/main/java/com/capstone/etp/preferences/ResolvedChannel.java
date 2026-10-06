package com.capstone.etp.preferences;

/**
 * Where one message for one account is to be sent right now.
 *
 * {@code destination} is personal data (an e-mail address or a telephone number). It is read from the
 * customer's profile for this one send and is not stored anywhere by the caller. {@link #toString()}
 * therefore never prints it, so a stray log line cannot leak it.
 */
public record ResolvedChannel(AlertChannel channel, String destination, ChannelSource source) {

    @Override
    public String toString() {
        return "ResolvedChannel[channel=" + channel + ", source=" + source + ", destination=<not logged>]";
    }
}
