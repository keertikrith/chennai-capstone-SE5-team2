package com.capstone.etp.preferences;

import java.time.Instant;
import java.util.List;

/**
 * A customer's preferences as the settings screen shows them.
 *
 * {@code contactDetail} is the destination for the chosen channel, MASKED ("j***@example.com",
 * "******3210"): the screen can show where alerts go without the full address or number leaving the
 * server. {@code source} says whether the channel was chosen or is the platform default.
 */
public record PreferencesResponse(
        Long accountId,
        Long defaultAccountId,
        AlertChannel alertChannel,
        String contactDetail,
        ChannelSource source,
        List<ChannelOption> channels,
        Instant updatedAt) {

    /** One channel the customer could pick, and whether the profile can serve it. */
    public record ChannelOption(AlertChannel channel, boolean available, String hint) {
    }
}
