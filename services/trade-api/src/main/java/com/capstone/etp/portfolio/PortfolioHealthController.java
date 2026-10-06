package com.capstone.etp.portfolio;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.OptionalInt;

/**
 * {@code GET /health} from contracts/portfolio-api.yaml: liveness plus the state of the two things the
 * module depends on. It answers 200 while the service is running; a failing dependency degrades
 * {@code status} and does not fail the response. It is public (the contract says {@code security: []})
 * and carries no customer data and no key.
 *
 * Fauxnance's {@code /usage} costs quota, so the answer is cached for
 * {@code app.portfolio.usage-cache-seconds} and the endpoint can be polled freely.
 */
@Slf4j
@RestController
class PortfolioHealthController {

    private final RealisedPnlMapper database;
    private final FauxnanceQuoteClient fauxnance;
    private final Clock clock;
    private final Duration usageCache;

    private volatile HealthResponse.Dependency cachedFauxnance;
    private volatile Instant cachedAt = Instant.MIN;

    PortfolioHealthController(RealisedPnlMapper database,
                              FauxnanceQuoteClient fauxnance,
                              Clock clock,
                              @Value("${app.portfolio.usage-cache-seconds:300}") long usageCacheSeconds) {
        this.database = database;
        this.fauxnance = fauxnance;
        this.clock = clock;
        this.usageCache = Duration.ofSeconds(usageCacheSeconds);
    }

    @GetMapping("/health")
    HealthResponse health() {
        HealthResponse.Dependency postgres = postgres();
        HealthResponse.Dependency provider = fauxnance();
        boolean ok = "ok".equals(postgres.status()) && "ok".equals(provider.status());
        return new HealthResponse(ok ? "ok" : "degraded", List.of(postgres, provider), clock.instant());
    }

    private HealthResponse.Dependency postgres() {
        try {
            database.ping();
            return new HealthResponse.Dependency("postgres", "ok", null);
        } catch (RuntimeException e) {
            return new HealthResponse.Dependency("postgres", "down", null);
        }
    }

    private HealthResponse.Dependency fauxnance() {
        Instant now = clock.instant();
        HealthResponse.Dependency cached = cachedFauxnance;
        if (cached != null && cachedAt.plus(usageCache).isAfter(now)) {
            return cached;
        }
        HealthResponse.Dependency fresh;
        try {
            OptionalInt remaining = fauxnance.usageRemaining();
            fresh = new HealthResponse.Dependency("fauxnance", "ok",
                    remaining.isPresent() ? remaining.getAsInt() : null);
        } catch (QuoteProvider.QuoteProviderException e) {
            log.warn("[PORTFOLIO] Fauxnance usage check failed: {}", e.getMessage());
            fresh = new HealthResponse.Dependency("fauxnance", "down", null);
        }
        cachedFauxnance = fresh;
        cachedAt = now;
        return fresh;
    }
}
