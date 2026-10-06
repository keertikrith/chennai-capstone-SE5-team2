package com.capstone.etp.portfolio;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Where a price comes from. The only implementation calls Fauxnance from the server; this is an
 * interface so the pricing rules (batching, caching, staleness, fallback) are tested without a network.
 */
interface QuoteProvider {

    /**
     * One call for up to {@link #MAX_BATCH} symbols. Symbols the provider cannot price are simply
     * absent from the result.
     *
     * @throws QuoteProviderException when the provider could not answer at all (unreachable,
     *         quota exhausted, a server error)
     */
    Map<String, ProviderQuote> fetch(List<String> symbols) throws QuoteProviderException;

    int MAX_BATCH = 25;

    /** A price as the provider reported it. {@code stale} is the provider's own flag. */
    record ProviderQuote(String symbol, BigDecimal price, String currency, Instant asOf, boolean stale) {
    }

    /** The provider did not answer. Carries a reason for the log and never a key or a URL with one. */
    class QuoteProviderException extends Exception {
        QuoteProviderException(String message) {
            super(message);
        }

        QuoteProviderException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
