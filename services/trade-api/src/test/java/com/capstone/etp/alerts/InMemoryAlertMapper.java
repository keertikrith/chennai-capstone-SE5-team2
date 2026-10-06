package com.capstone.etp.alerts;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/** The price_alerts table's rules in memory: the cap is part of the insert, the transition is guarded. */
class InMemoryAlertMapper implements AlertMapper {

    final Map<Long, PriceAlert> rows = new LinkedHashMap<>();
    private final AtomicLong sequence = new AtomicLong(0);

    PriceAlert seed(long accountId, String symbol, AlertDirection direction, String threshold) {
        Long id = insertWithinCap(accountId, symbol, direction, new BigDecimal(threshold), Integer.MAX_VALUE);
        return rows.get(id);
    }

    @Override
    public Long insertWithinCap(Long accountId, String symbol, AlertDirection direction, BigDecimal threshold, int cap) {
        long held = rows.values().stream().filter(a -> a.getAccountId().equals(accountId)).count();
        if (held >= cap) {
            return null;
        }
        PriceAlert alert = new PriceAlert();
        alert.setId(sequence.incrementAndGet());
        alert.setAccountId(accountId);
        alert.setSymbol(symbol);
        alert.setDirection(direction);
        alert.setThreshold(threshold);
        alert.setStatus(AlertStatus.ACTIVE);
        alert.setDeliveryState(DeliveryState.NONE);
        alert.setCreatedAt(Instant.now());
        rows.put(alert.getId(), alert);
        return alert.getId();
    }

    @Override
    public List<PriceAlert> findByAccount(Long accountId) {
        List<PriceAlert> found = new ArrayList<>(rows.values().stream()
                .filter(a -> a.getAccountId().equals(accountId)).toList());
        found.sort(Comparator.comparing(PriceAlert::getId).reversed());
        return found;
    }

    @Override
    public PriceAlert findByIdAndAccount(Long id, Long accountId) {
        PriceAlert alert = rows.get(id);
        return alert != null && alert.getAccountId().equals(accountId) ? alert : null;
    }

    @Override
    public List<PriceAlert> findAllActive() {
        return rows.values().stream().filter(a -> a.getStatus() == AlertStatus.ACTIVE).toList();
    }

    @Override
    public int cancel(Long id, Long accountId) {
        PriceAlert alert = findByIdAndAccount(id, accountId);
        if (alert == null || alert.getStatus() != AlertStatus.ACTIVE) {
            return 0;
        }
        alert.setStatus(AlertStatus.CANCELLED);
        return 1;
    }

    @Override
    public int deleteFinished(Long id, Long accountId) {
        PriceAlert alert = findByIdAndAccount(id, accountId);
        if (alert == null || alert.getStatus() == AlertStatus.ACTIVE
                || alert.getDeliveryState() == DeliveryState.PENDING_HANDOVER
                || alert.getDeliveryState() == DeliveryState.HANDOVER_FAILED) {
            return 0;
        }
        rows.remove(id);
        return 1;
    }

    @Override
    public int markTriggered(Long id, BigDecimal price) {
        PriceAlert alert = rows.get(id);
        if (alert == null || alert.getStatus() != AlertStatus.ACTIVE) {
            return 0;
        }
        alert.setStatus(AlertStatus.TRIGGERED);
        alert.setDeliveryState(DeliveryState.PENDING_HANDOVER);
        alert.setTriggeredPrice(price);
        alert.setTriggeredAt(Instant.now());
        return 1;
    }

    @Override
    public int markHandedOver(Long id) {
        PriceAlert alert = rows.get(id);
        alert.setDeliveryState(DeliveryState.HANDED_OVER);
        alert.setDeliveryAttempts(alert.getDeliveryAttempts() + 1);
        return 1;
    }

    @Override
    public int markHandoverFailed(Long id) {
        PriceAlert alert = rows.get(id);
        alert.setDeliveryState(DeliveryState.HANDOVER_FAILED);
        alert.setDeliveryAttempts(alert.getDeliveryAttempts() + 1);
        return 1;
    }

    @Override
    public List<PriceAlert> findPendingHandover(int maxAttempts, int limit) {
        return rows.values().stream()
                .filter(a -> a.getStatus() == AlertStatus.TRIGGERED)
                .filter(a -> a.getDeliveryState() == DeliveryState.PENDING_HANDOVER
                        || a.getDeliveryState() == DeliveryState.HANDOVER_FAILED)
                .filter(a -> a.getDeliveryAttempts() < maxAttempts)
                .limit(limit).toList();
    }
}
