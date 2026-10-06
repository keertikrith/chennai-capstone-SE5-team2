package com.capstone.etp.alerts;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlertIndexTest {

    @Test
    void aSymbolNobodyWatchesCostsOneLookupAndReturnsNothing() {
        InMemoryAlertMapper alerts = new InMemoryAlertMapper();
        alerts.seed(1L, "AAPL", AlertDirection.ABOVE, "200");
        AlertIndex index = new AlertIndex(alerts);
        index.refresh();

        assertTrue(index.forSymbol("TSLA").isEmpty());
        assertEquals(1, index.forSymbol("AAPL").size());
    }

    @Test
    void refreshPicksUpAlertsCreatedElsewhereAndDropsFinishedOnes() {
        InMemoryAlertMapper alerts = new InMemoryAlertMapper();
        PriceAlert first = alerts.seed(1L, "AAPL", AlertDirection.ABOVE, "200");
        AlertIndex index = new AlertIndex(alerts);
        index.refresh();
        assertEquals(1, index.size());

        alerts.seed(2L, "MSFT", AlertDirection.BELOW, "300");   // created on another instance
        alerts.markTriggered(first.getId(), java.math.BigDecimal.TEN);   // fired on another instance
        index.refresh();

        assertTrue(index.forSymbol("AAPL").isEmpty());
        assertEquals(1, index.forSymbol("MSFT").size());
    }

    @Test
    void aFailedRefreshKeepsTheIndexItHas() {
        AlertMapper broken = org.mockito.Mockito.mock(AlertMapper.class);
        org.mockito.Mockito.when(broken.findAllActive()).thenThrow(new IllegalStateException("db"));
        InMemoryAlertMapper alerts = new InMemoryAlertMapper();
        AlertIndex index = new AlertIndex(broken);
        index.add(alerts.seed(1L, "AAPL", AlertDirection.ABOVE, "200"));

        index.refresh();

        assertEquals(1, index.forSymbol("AAPL").size());
    }

    @Test
    void lookupReturnsASnapshotSoEvaluationCanRemoveEntriesSafely() {
        InMemoryAlertMapper alerts = new InMemoryAlertMapper();
        AlertIndex index = new AlertIndex(alerts);
        index.add(alerts.seed(1L, "AAPL", AlertDirection.ABOVE, "200"));
        index.add(alerts.seed(1L, "AAPL", AlertDirection.ABOVE, "210"));

        for (AlertIndex.Entry entry : index.forSymbol("AAPL")) {
            index.remove("AAPL", entry.id());
        }

        assertTrue(index.forSymbol("AAPL").isEmpty());
    }
}
