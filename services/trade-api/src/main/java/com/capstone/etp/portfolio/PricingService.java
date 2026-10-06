package com.capstone.etp.portfolio;

import com.capstone.etp.dto.MarketQuote;
import com.capstone.etp.service.MarketQuoteCache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Turns a set of symbols into prices, spending as little of the shared Fauxnance quota as it can.
 *
 * <ol>
 *   <li><b>Cache.</b> A quote fetched within {@code app.portfolio.quote-ttl-seconds} is reused, so a
 *       customer refreshing the screen, or several customers holding the same symbols, share one call.</li>
 *   <li><b>Batch.</b> Whatever is not cached goes in ONE call per 25 symbols, never one call per position.
 *       Ten holdings cost one unit of quota, not ten.</li>
 *   <li><b>Cooldown.</b> After a failed call the provider is left alone for
 *       {@code app.portfolio.provider-cooldown-seconds}: it saves quota and means an outage costs one
 *       slow request, not one per customer.</li>
 *   <li><b>Fallback.</b> If a symbol could not be fetched, the last price this service holds for it is
 *       served, marked stale: first the expired cache entry, then the last quote the poller put on
 *       {@code market-data}. Only a symbol with no price from any source is left unpriced.</li>
 * </ol>
 *
 * Stale is a state, not an error: every figure carries the time it was observed and whether it is
 * stale, either because the provider said so or because it is older than
 * {@code app.portfolio.stale-after-seconds}.
 */
@Slf4j
@Service
public class PricingService {

    /** A price ready to be served. {@code stale} is already decided. */
    public record PricedQuote(String symbol, BigDecimal price, String currency, Instant asOf, boolean stale) {
    }

    private record CachedQuote(QuoteProvider.ProviderQuote quote, Instant fetchedAt) {
    }

    private final QuoteProvider provider;
    private final MarketQuoteCache topicCache;
    private final Clock clock;
    private final Duration ttl;
    private final Duration staleAfter;
    private final Duration cooldown;

    private final Map<String, CachedQuote> cache = new ConcurrentHashMap<>();
    private volatile Instant providerUnavailableUntil = Instant.MIN;

    PricingService(QuoteProvider provider,
                   MarketQuoteCache topicCache,
                   Clock clock,
                   @Value("${app.portfolio.quote-ttl-seconds:60}") long ttlSeconds,
                   @Value("${app.portfolio.stale-after-seconds:900}") long staleAfterSeconds,
                   @Value("${app.portfolio.provider-cooldown-seconds:15}") long cooldownSeconds) {
        this.provider = provider;
        this.topicCache = topicCache;
        this.clock = clock;
        this.ttl = Duration.ofSeconds(ttlSeconds);
        this.staleAfter = Duration.ofSeconds(staleAfterSeconds);
        this.cooldown = Duration.ofSeconds(cooldownSeconds);
    }

    /** Prices for as many of the symbols as any source can supply. Symbols with no price are absent. */
    public Map<String, PricedQuote> price(Collection<String> symbols) {
        Instant now = clock.instant();
        Map<String, PricedQuote> priced = new LinkedHashMap<>();
        List<String> missing = new ArrayList<>();

        for (String symbol : new LinkedHashSet<>(symbols.stream().map(PricingService::normalise).toList())) {
            CachedQuote cached = cache.get(symbol);
            if (cached != null && cached.fetchedAt().plus(ttl).isAfter(now)) {
                priced.put(symbol, toPriced(cached.quote(), now, false));
            } else {
                missing.add(symbol);
            }
        }

        if (!missing.isEmpty()) {
            fetchInto(priced, missing, now);
        }
        return priced;
    }

    /** How many symbols are currently held in the cache (for tests and diagnostics). */
    int cachedCount() {
        return cache.size();
    }

    private void fetchInto(Map<String, PricedQuote> priced, List<String> missing, Instant now) {
        if (!now.isBefore(providerUnavailableUntil)) {
            for (List<String> chunk : chunks(missing)) {
                try {
                    Map<String, QuoteProvider.ProviderQuote> fetched = provider.fetch(chunk);
                    fetched.forEach((symbol, quote) -> {
                        String key = normalise(symbol);
                        cache.put(key, new CachedQuote(quote, now));
                        priced.put(key, toPriced(quote, now, false));
                    });
                } catch (QuoteProvider.QuoteProviderException e) {
                    providerUnavailableUntil = now.plus(cooldown);
                    log.warn("[PORTFOLIO] quote provider unavailable, pausing calls for {}s: {}",
                            cooldown.toSeconds(), e.getMessage());
                    break;
                }
            }
        }

        for (String symbol : missing) {
            if (!priced.containsKey(symbol)) {
                fallback(symbol, now).ifPresent(quote -> priced.put(symbol, quote));
            }
        }
    }

    /** The best price this service still holds for a symbol that could not be fetched. Always stale. */
    private Optional<PricedQuote> fallback(String symbol, Instant now) {
        CachedQuote expired = cache.get(symbol);
        Optional<MarketQuote> fromTopic = topicCache.get(symbol)
                .filter(quote -> quote.getPrice() != null && quote.getPrice().signum() > 0);

        Instant expiredAsOf = expired == null ? null : observedAt(expired.quote().asOf(), expired.fetchedAt());
        Instant topicAsOf = fromTopic.map(q -> observedAt(q.getQuoteAsOf(), q.getReceivedAt())).orElse(null);

        if (expired != null && (topicAsOf == null || !expiredAsOf.isBefore(topicAsOf))) {
            return Optional.of(toPriced(expired.quote(), now, true));
        }
        return fromTopic.map(quote -> new PricedQuote(
                symbol, quote.getPrice(), quote.getCurrency(), topicAsOf, true));
    }

    private PricedQuote toPriced(QuoteProvider.ProviderQuote quote, Instant now, boolean forceStale) {
        Instant asOf = quote.asOf();
        boolean old = asOf != null && Duration.between(asOf, now).compareTo(staleAfter) > 0;
        return new PricedQuote(normalise(quote.symbol()), quote.price(), quote.currency(), asOf,
                forceStale || quote.stale() || old);
    }

    private static Instant observedAt(Instant asOf, Instant fallback) {
        return asOf != null ? asOf : (fallback != null ? fallback : Instant.EPOCH);
    }

    private static List<List<String>> chunks(List<String> symbols) {
        List<List<String>> chunks = new ArrayList<>();
        for (int from = 0; from < symbols.size(); from += QuoteProvider.MAX_BATCH) {
            chunks.add(symbols.subList(from, Math.min(from + QuoteProvider.MAX_BATCH, symbols.size())));
        }
        return chunks;
    }

    private static String normalise(String symbol) {
        return symbol.trim().toUpperCase(Locale.ROOT);
    }
}
