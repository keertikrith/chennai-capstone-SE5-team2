package com.capstone.etp.alerts;

import com.capstone.etp.dto.MarketQuote;
import com.capstone.etp.kafka.consumer.QuoteObserver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Locale;

/**
 * Checks every quote from {@code market-data} against the active alerts for its symbol.
 *
 * <p><b>Meaning of "crossed".</b> An ABOVE alert fires on the first quote whose price is at or above
 * the threshold; a BELOW alert on the first at or below it. A quote the provider flagged stale is
 * ignored: a price that old is not evidence the market crossed anything.
 *
 * <p><b>What happens next.</b> The alert deactivates itself. The transition ACTIVE to TRIGGERED is a
 * guarded update ({@code WHERE status = 'ACTIVE'}), so a redelivered quote, a rebalance, a second
 * instance or a cancel racing the quote can never fire it twice: whoever updates zero rows does nothing.
 * Firing on every quote past the level was rejected: it would send a customer dozens of messages in a
 * minute, through a module another team member owns (decision-log 0007).
 *
 * <p><b>Authorisation.</b> The evaluator has no caller and no token. It acts on a quote and on alerts
 * that were each created through an authorised route; it only reads and updates alert rows and calls
 * the in-process {@code AlertDelivery}, and it never serves a request.
 */
@Slf4j
@Component
class AlertEvaluator implements QuoteObserver {

    private final AlertIndex index;
    private final AlertMapper mapper;
    private final AlertHandover handover;

    AlertEvaluator(AlertIndex index, AlertMapper mapper, AlertHandover handover) {
        this.index = index;
        this.mapper = mapper;
        this.handover = handover;
    }

    @Override
    public void onQuote(MarketQuote quote) {
        if (quote == null || quote.getSymbol() == null || quote.getPrice() == null) {
            return;
        }
        if (Boolean.TRUE.equals(quote.getStale())) {
            return;
        }
        String symbol = quote.getSymbol().trim().toUpperCase(Locale.ROOT);
        for (AlertIndex.Entry alert : index.forSymbol(symbol)) {
            if (alert.direction().isCrossedBy(quote.getPrice(), alert.threshold())) {
                fire(alert, quote);
            }
        }
    }

    private void fire(AlertIndex.Entry alert, MarketQuote quote) {
        int rows;
        try {
            rows = mapper.markTriggered(alert.id(), quote.getPrice());
        } catch (RuntimeException e) {
            // Stays in the index: the next quote past the level tries again.
            log.warn("[ALERTS] could not record trigger of alert {}: {}", alert.id(), e.getMessage());
            return;
        }
        index.remove(alert.symbol(), alert.id());
        if (rows == 0) {
            log.debug("[ALERTS] alert {} already handled elsewhere", alert.id());
            return;
        }
        log.info("[ALERTS] alert {} triggered on {} at {}", alert.id(), alert.symbol(), quote.getPrice());
        handover.handOver(alert.id(), alert.accountId(), alert.symbol(), alert.direction(),
                alert.threshold(), quote.getPrice(),
                quote.getQuoteAsOf() != null ? quote.getQuoteAsOf() : Instant.now());
    }
}
