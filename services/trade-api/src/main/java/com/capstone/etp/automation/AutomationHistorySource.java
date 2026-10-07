package com.capstone.etp.automation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Historical prices used to pre-fill strategy history before live ticks continue it. */
@FunctionalInterface
interface AutomationHistorySource {

    record Observation(BigDecimal price, Instant asOf) {
    }

    AutomationHistorySource NONE = (symbol, count) -> List.of();

    /** Up to {@code count} latest observations for {@code symbol}, oldest first. */
    List<Observation> latest(String symbol, int count);
}
