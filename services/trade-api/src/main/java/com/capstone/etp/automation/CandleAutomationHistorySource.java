package com.capstone.etp.automation;

import com.capstone.etp.candles.CandleService;
import com.capstone.etp.candles.CandlesResponse;
import com.capstone.etp.exception.ModuleException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/** Daily closes from the existing candle service, used to warm up automation history. */
@Slf4j
@Component
class CandleAutomationHistorySource implements AutomationHistorySource {

    private static final int MAX_LIMIT = 500;

    private final CandleService candles;

    CandleAutomationHistorySource(CandleService candles) {
        this.candles = candles;
    }

    @Override
    public List<Observation> latest(String symbol, int count) {
        int limit = Math.min(Math.max(count, 1), MAX_LIMIT);
        try {
            CandlesResponse response = candles.candles(symbol, "1d", limit);
            if (response == null || response.candles() == null) {
                return List.of();
            }
            return response.candles().stream()
                    .filter(candle -> candle.close() != null && candle.close().signum() > 0 && candle.timestamp() != null)
                    .sorted(Comparator.comparing(CandlesResponse.Candle::timestamp))
                    .map(candle -> new Observation(candle.close(), candle.timestamp()))
                    .toList();
        } catch (ModuleException e) {
            log.warn("[AUTOMATION] historical warm-up unavailable for {}: {}", symbol, e.getClass().getSimpleName());
            return List.of();
        }
    }
}
