package com.capstone.etp.portfolio;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * contracts/portfolio-api.yaml PortfolioSummary. Field names are the contract's.
 *
 * When {@code partial} is true at least one holding could not be priced (or converted to the base
 * currency). Such a holding adds to {@code positionCount} and {@code costBasis} but contributes
 * nothing to {@code marketValue}, {@code unrealisedPnl} or {@code totalValue}, so those figures
 * understate the portfolio; the flag is how a client knows.
 */
public record PortfolioSummaryResponse(
        Long accountId,
        String baseCurrency,
        BigDecimal cashBalance,
        BigDecimal marketValue,
        BigDecimal costBasis,
        BigDecimal unrealisedPnl,
        BigDecimal unrealisedPnlPercent,
        BigDecimal realisedPnl,
        BigDecimal totalValue,
        int positionCount,
        boolean partial,
        Instant asOf) {
}
