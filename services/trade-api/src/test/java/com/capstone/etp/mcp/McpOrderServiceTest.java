package com.capstone.etp.mcp;

import com.capstone.etp.automation.TradeApiClient.SubmissionResult;
import com.capstone.etp.dto.PlaceOrderRequest;
import com.capstone.etp.enums.OrderSide;
import com.capstone.etp.exception.ModuleException;
import com.capstone.etp.mcp.McpModels.PendingOrderView;
import com.capstone.etp.mcp.McpModels.PrepareOrderRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The rules an AI assistant cannot get around: scope, limits, and a prepared order trading at most once. */
class McpOrderServiceTest {

    private static final UUID CONNECTOR = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PENDING = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final Instant NOW = Instant.parse("2026-10-08T10:00:00Z");

    private McpMapper mapper;
    private McpConnectorService connectors;
    private McpOrderGateway gateway;
    private McpOrderService service;
    private McpConnector connector;

    @BeforeEach
    void setUp() {
        mapper = mock(McpMapper.class);
        connectors = mock(McpConnectorService.class);
        gateway = mock(McpOrderGateway.class);
        McpProperties properties = new McpProperties();
        service = new McpOrderService(mapper, connectors, gateway, properties, Clock.fixed(NOW, ZoneOffset.UTC));

        connector = new McpConnector();
        connector.setId(CONNECTOR);
        connector.setAccountId(7L);
        connector.setScopes("READ,TRADE");
        connector.setMaxOrderValue(new BigDecimal("1000"));
        connector.setMaxOrdersPerDay(2);
        when(connectors.requireActive(CONNECTOR)).thenReturn(connector);
    }

    private static PrepareOrderRequest prepare(int quantity, String price) {
        return new PrepareOrderRequest("aapl", "buy", quantity, new BigDecimal(price));
    }

    private static McpPendingOrder pending(String status) {
        McpPendingOrder p = new McpPendingOrder();
        p.setId(PENDING);
        p.setConnectorId(CONNECTOR);
        p.setAccountId(7L);
        p.setSymbol("AAPL");
        p.setSide("BUY");
        p.setQuantity(5);
        p.setPrice(new BigDecimal("100.00"));
        p.setEstValue(new BigDecimal("500.00"));
        p.setStatus(status);
        p.setExpiresAt(NOW.plusSeconds(60));
        return p;
    }

    @Test
    void prepareStoresTheOrderWithoutTradingAndUppercasesTheSymbol() {
        PendingOrderView view = service.prepare(CONNECTOR, prepare(5, "100.00"));

        assertEquals("PENDING", view.status());
        assertEquals("AAPL", view.symbol());
        assertEquals(new BigDecimal("500.00"), view.estimatedValue());
        assertEquals(NOW.plusSeconds(120), view.expiresAt());
        verify(mapper).insertPending(any(UUID.class), eq(CONNECTOR), eq(7L), eq("AAPL"), eq("BUY"), eq(5),
                eq(new BigDecimal("100.00")), eq(new BigDecimal("500.00")), eq(NOW.plusSeconds(120)));
        verify(gateway, never()).submit(any());
    }

    @Test
    void prepareRefusesALinkWithoutTheTradePermission() {
        connector.setScopes("READ");

        ModuleException e = assertThrows(ModuleException.class, () -> service.prepare(CONNECTOR, prepare(1, "10.00")));

        assertEquals("MCP-403", e.getCatalogueCode());
        verify(mapper, never()).insertPending(any(), any(), anyLong(), anyString(), anyString(), anyInt(), any(), any(), any());
    }

    @Test
    void prepareRefusesAnOrderOverThePerOrderLimit() {
        ModuleException e = assertThrows(ModuleException.class, () -> service.prepare(CONNECTOR, prepare(11, "100.00")));

        assertEquals("MCP-422", e.getCatalogueCode());
        verify(mapper, never()).insertPending(any(), any(), anyLong(), anyString(), anyString(), anyInt(), any(), any(), any());
    }

    @Test
    void prepareRefusesOnceTheDailyLimitIsReached() {
        when(mapper.countPlacedToday(CONNECTOR)).thenReturn(2);

        ModuleException e = assertThrows(ModuleException.class, () -> service.prepare(CONNECTOR, prepare(1, "10.00")));

        assertEquals("MCP-422", e.getCatalogueCode());
    }

    @Test
    void prepareRefusesAnUnknownSideAndTooManyDecimals() {
        assertThrows(ModuleException.class,
                () -> service.prepare(CONNECTOR, new PrepareOrderRequest("AAPL", "hold", 1, new BigDecimal("10.00"))));
        assertThrows(ModuleException.class, () -> service.prepare(CONNECTOR, prepare(1, "10.005")));
    }

    @Test
    void confirmPlacesTheOrderOnceWithThePendingIdAsTheIdempotencyKey() {
        UUID orderId = UUID.randomUUID();
        when(mapper.findPending(PENDING, CONNECTOR)).thenReturn(pending("PENDING"));
        when(mapper.claimPending(PENDING, CONNECTOR)).thenReturn(1);
        when(gateway.submit(any())).thenReturn(SubmissionResult.submitted(orderId, "status NEW"));

        PendingOrderView view = service.confirm(CONNECTOR, PENDING);

        assertEquals("PLACED", view.status());
        assertEquals(orderId, view.orderId());
        ArgumentCaptor<PlaceOrderRequest> sent = ArgumentCaptor.forClass(PlaceOrderRequest.class);
        verify(gateway).submit(sent.capture());
        assertEquals(PENDING, sent.getValue().getIdempotencyKey());
        assertEquals(7L, sent.getValue().getAccountId());
        assertEquals(OrderSide.BUY, sent.getValue().getSide());
        verify(mapper).settlePending(PENDING, "PLACED", orderId, "status NEW");
    }

    @Test
    void confirmDoesNothingWhenSomeoneElseClaimedTheRowFirst() {
        when(mapper.findPending(PENDING, CONNECTOR)).thenReturn(pending("PENDING"));
        when(mapper.claimPending(PENDING, CONNECTOR)).thenReturn(0);

        ModuleException e = assertThrows(ModuleException.class, () -> service.confirm(CONNECTOR, PENDING));

        assertEquals("MCP-409", e.getCatalogueCode());
        verify(gateway, never()).submit(any());
    }

    @Test
    void confirmRefusesAnOrderThatIsNoLongerPending() {
        when(mapper.findPending(PENDING, CONNECTOR)).thenReturn(pending("PLACED"));

        ModuleException e = assertThrows(ModuleException.class, () -> service.confirm(CONNECTOR, PENDING));

        assertEquals("MCP-409", e.getCatalogueCode());
        verify(mapper, never()).claimPending(any(), any());
        verify(gateway, never()).submit(any());
    }

    @Test
    void confirmRecordsAPlatformRefusalAndAnUnknownOutcomeDifferently() {
        when(mapper.findPending(PENDING, CONNECTOR)).thenReturn(pending("PENDING"));
        when(mapper.claimPending(PENDING, CONNECTOR)).thenReturn(1);

        when(gateway.submit(any())).thenReturn(SubmissionResult.rejected("HTTP 422 FUND-422"));
        assertEquals("REJECTED", service.confirm(CONNECTOR, PENDING).status());

        when(gateway.submit(any())).thenReturn(SubmissionResult.failed("ResourceAccessException"));
        assertEquals("FAILED", service.confirm(CONNECTOR, PENDING).status());
    }

    @Test
    void statusReportsAnUnconfirmedOrderPastItsExpiryAsExpired() {
        McpPendingOrder old = pending("PENDING");
        old.setExpiresAt(NOW.minusSeconds(1));
        when(mapper.findPending(PENDING, CONNECTOR)).thenReturn(old);

        assertEquals("EXPIRED", service.status(CONNECTOR, PENDING).status());
    }
}
