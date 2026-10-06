package com.capstone.etp.alerts;

import com.capstone.etp.dto.MarketQuote;
import com.capstone.etp.notifications.AlertDelivery;
import com.capstone.etp.notifications.AlertDeliveryException;
import com.capstone.etp.notifications.AlertDeliveryRequest;
import com.capstone.etp.notifications.DeliveryReceipt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AlertEvaluatorTest {

    private InMemoryAlertMapper alerts;
    private AlertIndex index;
    private AlertDelivery delivery;
    private AlertEvaluator evaluator;

    @BeforeEach
    void setUp() {
        alerts = new InMemoryAlertMapper();
        index = new AlertIndex(alerts);
        delivery = mock(AlertDelivery.class);
        when(delivery.deliver(any())).thenReturn(new DeliveryReceipt(55L, false));
        evaluator = new AlertEvaluator(index, alerts, new AlertHandover(delivery, alerts));
    }

    private PriceAlert alert(String symbol, AlertDirection direction, String threshold) {
        PriceAlert alert = alerts.seed(1L, symbol, direction, threshold);
        index.add(alert);
        return alert;
    }

    private static MarketQuote quote(String symbol, String price) {
        return MarketQuote.builder().symbol(symbol).price(new BigDecimal(price))
                .stale(false).quoteAsOf(Instant.parse("2026-10-06T09:15:00Z")).build();
    }

    // ---------------------------------------------------------------- crossing

    @Test
    void aQuoteCrossingTheThresholdTriggersTheAlert() {
        PriceAlert alert = alert("AAPL", AlertDirection.ABOVE, "235.00");

        evaluator.onQuote(quote("AAPL", "235.40"));

        assertEquals(AlertStatus.TRIGGERED, alerts.rows.get(alert.getId()).getStatus());
        assertEquals(new BigDecimal("235.40"), alerts.rows.get(alert.getId()).getTriggeredPrice());
    }

    @Test
    void aQuoteShortOfTheThresholdDoesNotTriggerIt() {
        PriceAlert alert = alert("AAPL", AlertDirection.ABOVE, "235.00");

        evaluator.onQuote(quote("AAPL", "234.99"));

        assertEquals(AlertStatus.ACTIVE, alerts.rows.get(alert.getId()).getStatus());
        verify(delivery, never()).deliver(any());
    }

    @Test
    void aBelowAlertTriggersWhenThePriceFallsThroughIt() {
        PriceAlert alert = alert("MSFT", AlertDirection.BELOW, "300.00");

        evaluator.onQuote(quote("MSFT", "300.01"));
        assertEquals(AlertStatus.ACTIVE, alerts.rows.get(alert.getId()).getStatus());
        evaluator.onQuote(quote("MSFT", "299.99"));

        assertEquals(AlertStatus.TRIGGERED, alerts.rows.get(alert.getId()).getStatus());
    }

    @Test
    void thePriceExactlyAtTheLevelCountsAsCrossed() {
        PriceAlert alert = alert("AAPL", AlertDirection.ABOVE, "235.00");

        evaluator.onQuote(quote("AAPL", "235.00"));

        assertEquals(AlertStatus.TRIGGERED, alerts.rows.get(alert.getId()).getStatus());
    }

    @Test
    void aQuoteForAnotherSymbolIsIgnored() {
        PriceAlert alert = alert("AAPL", AlertDirection.ABOVE, "1.00");

        evaluator.onQuote(quote("MSFT", "999.00"));

        assertEquals(AlertStatus.ACTIVE, alerts.rows.get(alert.getId()).getStatus());
    }

    @Test
    void aStaleQuoteIsNotEvidenceThatAnythingWasCrossed() {
        PriceAlert alert = alert("AAPL", AlertDirection.ABOVE, "235.00");
        MarketQuote stale = quote("AAPL", "240.00");
        stale.setStale(true);

        evaluator.onQuote(stale);

        assertEquals(AlertStatus.ACTIVE, alerts.rows.get(alert.getId()).getStatus());
    }

    @Test
    void malformedQuotesAreIgnored() {
        alert("AAPL", AlertDirection.ABOVE, "1.00");

        evaluator.onQuote(null);
        evaluator.onQuote(MarketQuote.builder().symbol("AAPL").build());
        evaluator.onQuote(MarketQuote.builder().price(BigDecimal.TEN).build());

        verify(delivery, never()).deliver(any());
    }

    @Test
    void theSymbolMatchIsCaseInsensitive() {
        PriceAlert alert = alert("INFY.NS", AlertDirection.ABOVE, "1500");

        evaluator.onQuote(quote("infy.ns", "1600"));

        assertEquals(AlertStatus.TRIGGERED, alerts.rows.get(alert.getId()).getStatus());
    }

    // ---------------------------------------------------------------- once, not forty times

    @Test
    void anAlertFiresOnceNotOnEveryQuotePastTheLevel() {
        alert("AAPL", AlertDirection.ABOVE, "235.00");

        for (int i = 0; i < 40; i++) {
            evaluator.onQuote(quote("AAPL", "236." + (10 + i)));
        }

        verify(delivery, times(1)).deliver(any());
    }

    @Test
    void aRedeliveredQuoteCannotFireTheSameAlertTwice() {
        PriceAlert alert = alert("AAPL", AlertDirection.ABOVE, "235.00");
        // Simulate a stale index on another instance: the entry is still there but the row already fired.
        evaluator.onQuote(quote("AAPL", "236.00"));
        index.add(alert);

        evaluator.onQuote(quote("AAPL", "237.00"));

        verify(delivery, times(1)).deliver(any());
    }

    @Test
    void aCancelledAlertDoesNotFireEvenIfTheIndexWasStale() {
        PriceAlert alert = alert("AAPL", AlertDirection.ABOVE, "235.00");
        alerts.cancel(alert.getId(), 1L);

        evaluator.onQuote(quote("AAPL", "240.00"));

        verify(delivery, never()).deliver(any());
        assertEquals(AlertStatus.CANCELLED, alerts.rows.get(alert.getId()).getStatus());
    }

    // ---------------------------------------------------------------- delivered through notifications

    @Test
    void aTriggeredAlertIsDeliveredThroughNotificationsNotToALog() {
        PriceAlert alert = alert("AAPL", AlertDirection.ABOVE, "235.00");

        evaluator.onQuote(quote("AAPL", "235.40"));

        ArgumentCaptor<AlertDeliveryRequest> sent = ArgumentCaptor.forClass(AlertDeliveryRequest.class);
        verify(delivery).deliver(sent.capture());
        assertEquals(alert.getId(), sent.getValue().alertId());
        assertEquals(1L, sent.getValue().accountId());
        assertEquals("AAPL", sent.getValue().symbol());
        assertEquals(AlertDeliveryRequest.Direction.ABOVE, sent.getValue().direction());
        assertEquals(new BigDecimal("235.40"), sent.getValue().price());
        assertEquals(DeliveryState.HANDED_OVER, alerts.rows.get(alert.getId()).getDeliveryState());
    }

    @Test
    void whenDeliveryRaisesTheAlertStaysFiredAndSaysItWasNotHandedOver() {
        when(delivery.deliver(any())).thenThrow(new AlertDeliveryException("recording failed"));
        PriceAlert alert = alert("AAPL", AlertDirection.ABOVE, "235.00");

        evaluator.onQuote(quote("AAPL", "235.40"));

        PriceAlert stored = alerts.rows.get(alert.getId());
        assertEquals(AlertStatus.TRIGGERED, stored.getStatus(), "the price did cross; that is a fact");
        assertEquals(DeliveryState.HANDOVER_FAILED, stored.getDeliveryState(), "and the customer can see it was not handed over");
    }

    @Test
    void aFailedHandoverIsRetriedAndThenCompleted() {
        when(delivery.deliver(any())).thenThrow(new AlertDeliveryException("down")).thenReturn(new DeliveryReceipt(9L, false));
        PriceAlert alert = alert("AAPL", AlertDirection.ABOVE, "235.00");
        evaluator.onQuote(quote("AAPL", "235.40"));
        AlertHandoverRetrier retrier = new AlertHandoverRetrier(alerts, new AlertHandover(delivery, alerts), 5);

        retrier.retryPending();

        assertEquals(DeliveryState.HANDED_OVER, alerts.rows.get(alert.getId()).getDeliveryState());
        assertTrue(alerts.rows.get(alert.getId()).getDeliveryAttempts() >= 2);
    }

    @Test
    void theRetrierGivesUpAfterTheAttemptLimitButLeavesTheStateVisible() {
        when(delivery.deliver(any())).thenThrow(new AlertDeliveryException("down"));
        PriceAlert alert = alert("AAPL", AlertDirection.ABOVE, "235.00");
        evaluator.onQuote(quote("AAPL", "235.40"));
        AlertHandoverRetrier retrier = new AlertHandoverRetrier(alerts, new AlertHandover(delivery, alerts), 3);

        for (int i = 0; i < 10; i++) {
            retrier.retryPending();
        }

        verify(delivery, times(3)).deliver(any());
        assertEquals(DeliveryState.HANDOVER_FAILED, alerts.rows.get(alert.getId()).getDeliveryState());
    }

    @Test
    void anAlertThatCannotBeRecordedAsTriggeredStaysActiveForTheNextQuote() {
        AlertMapper flaky = mock(AlertMapper.class);
        when(flaky.markTriggered(any(), any())).thenThrow(new IllegalStateException("db")).thenReturn(1);
        AlertIndex flakyIndex = new AlertIndex(flaky);
        PriceAlert alert = new PriceAlert();
        alert.setId(1L);
        alert.setAccountId(1L);
        alert.setSymbol("AAPL");
        alert.setDirection(AlertDirection.ABOVE);
        alert.setThreshold(new BigDecimal("235"));
        flakyIndex.add(alert);
        AlertEvaluator flakyEvaluator = new AlertEvaluator(flakyIndex, flaky, new AlertHandover(delivery, flaky));

        flakyEvaluator.onQuote(quote("AAPL", "240"));
        verify(delivery, never()).deliver(any());
        flakyEvaluator.onQuote(quote("AAPL", "241"));

        verify(delivery, times(1)).deliver(any());
    }
}
