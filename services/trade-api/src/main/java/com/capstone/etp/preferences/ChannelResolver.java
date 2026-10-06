package com.capstone.etp.preferences;

import java.util.Optional;

/**
 * THE SEAM between customer preferences and customer notifications.
 *
 * This is a Java interface, not an HTTP route: it is called in-process by the notification
 * dispatcher before every send, and nothing a customer's token can reach exposes it. Only the
 * preferences module implements it; callers never read the preference tables or the contact columns
 * themselves. Its only caller is {@code notifications}.
 *
 * <h3>What it answers</h3>
 * <ul>
 *   <li><b>Preference stored:</b> the channel the customer chose and where to send to on it
 *       ({@link ChannelSource#STORED}).</li>
 *   <li><b>Nothing stored:</b> the documented default, not an error ({@link ChannelSource#DEFAULT}):
 *       e-mail to the address on the customer's profile, or the in-app/push channel when the profile
 *       has no e-mail address. The customer is reached on a sensible channel instead of the
 *       message being lost.</li>
 *   <li><b>No usable contact</b> (the stored channel is SMS but the profile has no phone number, or
 *       the account does not exist): {@link Optional#empty()}. The caller must hold the message and
 *       say why. It must not silently pick a different channel than the one the customer chose.</li>
 * </ul>
 *
 * <h3>When it fails</h3>
 * It throws {@link PreferencesUnavailableException} only if the store cannot be read. The caller
 * fails safe: the message stays queued and is tried again, so a preferences outage delays a
 * notification and never drops one.
 */
public interface ChannelResolver {

    Optional<ResolvedChannel> resolve(long accountId);
}
