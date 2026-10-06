package com.capstone.etp.preferences;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * What a customer may set: which of their accounts to open on, and which channel to be alerted on.
 * There is deliberately no field for an address, a number or a URL. The application's JSON mapper
 * rejects unknown fields, so a request that tries to supply one is refused as VAL-422.
 */
public record UpdatePreferencesRequest(
        @NotNull @Positive Long defaultAccountId,
        @NotNull AlertChannel alertChannel) {
}
