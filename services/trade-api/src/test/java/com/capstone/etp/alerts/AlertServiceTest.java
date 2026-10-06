package com.capstone.etp.alerts;

import com.capstone.etp.dto.MarketQuote;
import com.capstone.etp.exception.AccountAccessDeniedException;
import com.capstone.etp.exception.InstrumentNotFoundException;
import com.capstone.etp.exception.ModuleException;
import com.capstone.etp.mapper.AccountMapper;
import com.capstone.etp.security.AccountAuthorizer;
import com.capstone.etp.service.AccountService;
import com.capstone.etp.service.MarketQuoteCache;
import com.capstone.etp.testsupport.TestIdentity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AlertServiceTest {

    private static final int CAP = 3;

    private InMemoryAlertMapper alerts;
    private AlertIndex index;
    private AccountMapper accountMapper;
    private MarketQuoteCache quoteCache;
    private AlertService service;

    @BeforeEach
    void setUp() {
        alerts = new InMemoryAlertMapper();
        index = new AlertIndex(alerts);
        accountMapper = mock(AccountMapper.class);
        when(accountMapper.countInstrumentsBySymbol("AAPL")).thenReturn(1);
        when(accountMapper.countInstrumentsBySymbol("MSFT")).thenReturn(1);
        quoteCache = new MarketQuoteCache();
        service = new AlertService(alerts, index, new AccountAuthorizer(), mock(AccountService.class),
                accountMapper, quoteCache, CAP);
        TestIdentity.customer(1L);
    }

    @AfterEach
    void tearDown() {
        TestIdentity.clear();
    }

    private static CreateAlertRequest above(String symbol, String threshold) {
        return new CreateAlertRequest(symbol, AlertDirection.ABOVE, new BigDecimal(threshold));
    }

    @Test
    void anAlertIsCreatedForTheAuthenticatedAccountAndIndexed() {
        AlertResponse created = service.create(1L, above("aapl", "235.00"));

        assertEquals("AAPL", created.symbol(), "symbols are normalised");
        assertEquals(AlertStatus.ACTIVE, created.status());
        assertEquals(DeliveryState.NONE, created.deliveryState());
        assertEquals(1L, alerts.rows.get(created.id()).getAccountId());
        assertEquals(1, index.forSymbol("AAPL").size(), "the quote consumer will see it");
    }

    @Test
    void listShowsOnlyMyAlerts() {
        service.create(1L, above("AAPL", "235"));
        alerts.seed(2L, "AAPL", AlertDirection.ABOVE, "100");

        List<AlertResponse> mine = service.list(1L);

        assertEquals(1, mine.size());
    }

    @Test
    void anotherAccountsAlertsAreRefusedNotListedAsEmpty() {
        assertThrows(AccountAccessDeniedException.class, () -> service.list(2L));
        assertThrows(AccountAccessDeniedException.class, () -> service.create(2L, above("AAPL", "1")));
        assertThrows(AccountAccessDeniedException.class, () -> service.remove(2L, 1L));
    }

    @Test
    void creationIsCappedPerAccount() {
        service.create(1L, above("AAPL", "201"));
        service.create(1L, above("AAPL", "202"));
        service.create(1L, above("AAPL", "203"));

        ModuleException refused = assertThrows(ModuleException.class, () -> service.create(1L, above("AAPL", "204")));

        assertEquals("ALR-409", refused.getCatalogueCode());
        assertEquals(CAP, alerts.rows.size());
    }

    @Test
    void theCapIsPerAccountSoAnotherCustomerIsNotBlocked() {
        for (int i = 0; i < CAP; i++) {
            alerts.seed(2L, "AAPL", AlertDirection.ABOVE, "10" + i);
        }

        assertEquals(AlertStatus.ACTIVE, service.create(1L, above("AAPL", "300")).status());
    }

    @Test
    void removingAnAlertMakesRoomUnderTheCap() {
        AlertResponse first = service.create(1L, above("AAPL", "201"));
        service.create(1L, above("AAPL", "202"));
        service.create(1L, above("AAPL", "203"));

        service.remove(1L, first.id());   // cancels (active)
        service.remove(1L, first.id());   // removes (finished)

        assertEquals(AlertStatus.ACTIVE, service.create(1L, above("AAPL", "204")).status());
    }

    @Test
    void anUnknownInstrumentIsRefused() {
        when(accountMapper.countInstrumentsBySymbol("NOPE")).thenReturn(0);

        assertThrows(InstrumentNotFoundException.class, () -> service.create(1L, above("NOPE", "1")));
    }

    @Test
    void anAlertTheCurrentPriceHasAlreadyCrossedIsRefused() {
        quoteCache.put(MarketQuote.builder().symbol("AAPL").price(new BigDecimal("240"))
                .stale(false).quoteAsOf(Instant.now()).build());

        ModuleException refused = assertThrows(ModuleException.class, () -> service.create(1L, above("AAPL", "235")));

        assertEquals("VAL-422", refused.getCatalogueCode());
        assertTrue(alerts.rows.isEmpty());
    }

    @Test
    void aStaleCachedPriceDoesNotBlockCreation() {
        quoteCache.put(MarketQuote.builder().symbol("AAPL").price(new BigDecimal("240"))
                .stale(true).quoteAsOf(Instant.now()).build());

        assertEquals(AlertStatus.ACTIVE, service.create(1L, above("AAPL", "235")).status());
    }

    // ---------------------------------------------------------------- cancel and remove

    @Test
    void cancellingAnActiveAlertRemovesItFromTheIndex() {
        AlertResponse created = service.create(1L, above("AAPL", "235"));

        AlertResponse cancelled = service.remove(1L, created.id());

        assertEquals(AlertStatus.CANCELLED, cancelled.status());
        assertTrue(index.forSymbol("AAPL").isEmpty(), "a cancelled alert can no longer fire");
    }

    @Test
    void anotherAccountsAlertLooksLikeItDoesNotExist() {
        PriceAlert theirs = alerts.seed(2L, "AAPL", AlertDirection.ABOVE, "100");

        ModuleException refused = assertThrows(ModuleException.class, () -> service.remove(1L, theirs.getId()));

        assertEquals("ALR-404", refused.getCatalogueCode());
        assertEquals(AlertStatus.ACTIVE, alerts.rows.get(theirs.getId()).getStatus(), "untouched");
    }

    @Test
    void aFiredAlertWhoseHandoverIsUnresolvedCannotBeRemoved() {
        PriceAlert fired = alerts.seed(1L, "AAPL", AlertDirection.ABOVE, "100");
        alerts.markTriggered(fired.getId(), new BigDecimal("101"));
        alerts.markHandoverFailed(fired.getId());

        ModuleException refused = assertThrows(ModuleException.class, () -> service.remove(1L, fired.getId()));

        assertEquals("ALR-409", refused.getCatalogueCode());
        assertTrue(alerts.rows.containsKey(fired.getId()), "the customer must still be able to see it was not delivered");
    }

    @Test
    void aDeliveredFiredAlertCanBeRemoved() {
        PriceAlert fired = alerts.seed(1L, "AAPL", AlertDirection.ABOVE, "100");
        alerts.markTriggered(fired.getId(), new BigDecimal("101"));
        alerts.markHandedOver(fired.getId());

        service.remove(1L, fired.getId());

        assertTrue(alerts.rows.isEmpty());
    }
}
