package com.capstone.etp.alerts;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The per-symbol index the quote consumer looks in.
 *
 * <p>Every quote for every symbol on the platform arrives at the evaluator, and for most of them
 * nobody has an alert. Asking the database per quote would put a query on the hot path of the
 * busiest topic; instead the active alerts are held in memory keyed by symbol, so "does anyone care
 * about this quote" is one hash lookup and, for the common case of a symbol nobody watches, returns
 * an empty list. The table is indexed on (symbol) for ACTIVE rows for the load and for the refresh.
 *
 * <p>The index is a cache of the table, not a second source of truth. It is changed on every create,
 * cancel and trigger made by this instance, rebuilt at start-up, and rebuilt every
 * {@code app.alerts.index-refresh-ms} so that an alert created on another instance becomes visible
 * here within that window. The trigger itself is a guarded database update, so a stale entry can
 * never cause a double fire.
 */
@Slf4j
@Component
class AlertIndex {

    /** What the evaluator needs to decide, nothing more. */
    record Entry(long id, long accountId, String symbol, AlertDirection direction, BigDecimal threshold) {
    }

    private final AlertMapper mapper;
    private volatile Map<String, List<Entry>> bySymbol = new ConcurrentHashMap<>();

    AlertIndex(AlertMapper mapper) {
        this.mapper = mapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    void loadAtStartup() {
        refresh();
    }

    @Scheduled(fixedDelayString = "${app.alerts.index-refresh-ms:30000}", initialDelay = 30_000)
    void refresh() {
        try {
            Map<String, List<Entry>> fresh = new ConcurrentHashMap<>();
            for (PriceAlert alert : mapper.findAllActive()) {
                fresh.computeIfAbsent(alert.getSymbol(), key -> new CopyOnWriteArrayList<>())
                        .add(toEntry(alert));
            }
            bySymbol = fresh;
            log.debug("[ALERTS] index rebuilt: {} symbols", fresh.size());
        } catch (RuntimeException e) {
            // Keep serving the index we have; the next refresh will try again.
            log.warn("[ALERTS] index refresh failed, keeping the current one: {}", e.getMessage());
        }
    }

    void add(PriceAlert alert) {
        bySymbol.computeIfAbsent(alert.getSymbol(), key -> new CopyOnWriteArrayList<>()).add(toEntry(alert));
    }

    void remove(String symbol, long id) {
        List<Entry> entries = bySymbol.get(symbol);
        if (entries != null) {
            entries.removeIf(entry -> entry.id() == id);
        }
    }

    /** A snapshot of the active alerts for a symbol; empty (no allocation of note) when nobody watches it. */
    List<Entry> forSymbol(String symbol) {
        List<Entry> entries = bySymbol.get(symbol);
        return entries == null || entries.isEmpty() ? List.of() : new ArrayList<>(entries);
    }

    int size() {
        return bySymbol.values().stream().mapToInt(List::size).sum();
    }

    private static Entry toEntry(PriceAlert alert) {
        return new Entry(alert.getId(), alert.getAccountId(), alert.getSymbol(),
                alert.getDirection(), alert.getThreshold());
    }
}
