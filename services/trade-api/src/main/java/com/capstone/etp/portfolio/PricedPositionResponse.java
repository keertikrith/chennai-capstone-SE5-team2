package com.capstone.etp.portfolio;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * contracts/portfolio-api.yaml PricedPosition. Amounts are in the instrument's own currency
 * ({@code currency}); only the summary and the P&L totals are converted to the base currency.
 * A position that could not be priced has null price fields and {@code stale = true}.
 */
public record PricedPositionResponse(
        Long accountId,
        String symbol,
        int quantity,
        BigDecimal averageCost,
        BigDecimal costBasis,
        BigDecimal lastPrice,
        BigDecimal marketValue,
        BigDecimal unrealisedPnl,
        BigDecimal unrealisedPnlPercent,
        String currency,
        Instant priceAsOf,
        boolean stale) {
}
