package com.capstone.etp.alerts;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Re-drives fired alerts whose hand-over to notifications is not confirmed: the call raised, or the
 * service stopped between "fired" and "handed over". Notifications is idempotent on the alert id, so a
 * retry can only ever complete a delivery, never repeat one.
 */
@Slf4j
@Component
class AlertHandoverRetrier {

    private static final int BATCH = 50;

    private final AlertMapper mapper;
    private final AlertHandover handover;
    private final int maxAttempts;

    AlertHandoverRetrier(AlertMapper mapper,
                         AlertHandover handover,
                         @Value("${app.alerts.max-handover-attempts:5}") int maxAttempts) {
        this.mapper = mapper;
        this.handover = handover;
        this.maxAttempts = maxAttempts;
    }

    @Scheduled(fixedDelayString = "${app.alerts.handover-retry-ms:30000}", initialDelay = 20_000)
    void retryPending() {
        List<PriceAlert> pending;
        try {
            pending = mapper.findPendingHandover(maxAttempts, BATCH);
        } catch (RuntimeException e) {
            log.warn("[ALERTS] could not read pending hand-overs: {}", e.getMessage());
            return;
        }
        for (PriceAlert alert : pending) {
            handover.handOver(alert.getId(), alert.getAccountId(), alert.getSymbol(), alert.getDirection(),
                    alert.getThreshold(), alert.getTriggeredPrice(),
                    alert.getTriggeredAt() != null ? alert.getTriggeredAt() : Instant.now());
        }
    }
}
