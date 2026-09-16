package com.capstone.etp.executor.integration;

import com.capstone.etp.executor.domain.AccountSnapshot;
import com.capstone.etp.executor.domain.AccountStatus;
import com.capstone.etp.executor.domain.ExecutionOrder;
import com.capstone.etp.executor.domain.InstrumentSnapshot;
import com.capstone.etp.executor.domain.OrderSide;
import com.capstone.etp.executor.domain.OrderStatus;
import com.capstone.etp.executor.pricing.FauxnanceQuoteClient;
import com.capstone.etp.executor.repository.ExecutionMapper;
import com.capstone.etp.executor.service.LimitOrderFillRule;
import com.capstone.etp.executor.service.OrderExecutionService;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.web.client.RestTemplateBuilder;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderExecutionWithMockedQuoteSourceTest {

    private MockWebServer server;
    private ExecutionMapper mapper;
    private OrderExecutionService service;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        mapper = mock(ExecutionMapper.class);

        FauxnanceQuoteClient quoteClient = new FauxnanceQuoteClient(
                new RestTemplateBuilder(),
                server.url("/").toString(),
                "test-api-key",
                2,
                1);

        service = new OrderExecutionService(mapper, quoteClient, new LimitOrderFillRule());
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void coversFillRejectAndPricingUnavailablePaths() {
        UUID fillOrderId = UUID.randomUUID();
        UUID rejectOrderId = UUID.randomUUID();
        UUID unavailableOrderId = UUID.randomUUID();

        stubCommonData(fillOrderId, OrderSide.BUY, "101.00");
        stubCommonData(rejectOrderId, OrderSide.BUY, "95.00");
        stubCommonData(unavailableOrderId, OrderSide.BUY, "101.00");
        when(mapper.updateOrderStatusIfCurrentStatusMatches(any(), eq(OrderStatus.NEW), any(), any(Instant.class)))
                .thenReturn(1);

        server.enqueue(new MockResponse().setResponseCode(200).setBody("""
                {
                  "data":{"symbol":"AAPL","bid":100.00,"ask":100.50,"price":100.25},
                  "meta":{"stale":false}
                }
                """).addHeader("Content-Type", "application/json"));
        service.handleOrderPlaced(fillOrderId);
        assertLastStatus(fillOrderId, OrderStatus.FILLED);

        reset(mapper);
        stubCommonData(rejectOrderId, OrderSide.BUY, "95.00");
        when(mapper.updateOrderStatusIfCurrentStatusMatches(any(), eq(OrderStatus.NEW), any(), any(Instant.class)))
                .thenReturn(1);

        server.enqueue(new MockResponse().setResponseCode(200).setBody("""
                {
                  "data":{"symbol":"AAPL","bid":100.00,"ask":100.50,"price":100.25},
                  "meta":{"stale":false}
                }
                """).addHeader("Content-Type", "application/json"));
        service.handleOrderPlaced(rejectOrderId);
        assertLastStatus(rejectOrderId, OrderStatus.REJECTED);

        reset(mapper);
        stubCommonData(unavailableOrderId, OrderSide.BUY, "101.00");
        when(mapper.updateOrderStatusIfCurrentStatusMatches(any(), eq(OrderStatus.NEW), any(), any(Instant.class)))
                .thenReturn(1);

        server.enqueue(new MockResponse().setResponseCode(503));
        server.enqueue(new MockResponse().setResponseCode(503));
        service.handleOrderPlaced(unavailableOrderId);
        assertLastStatus(unavailableOrderId, OrderStatus.REJECTED);
    }

    private void stubCommonData(UUID orderId, OrderSide side, String limit) {
        when(mapper.findOrderById(orderId)).thenReturn(ExecutionOrder.builder()
                .id(orderId)
                .accountId(1L)
                .instrumentId(10L)
                .symbol("AAPL")
                .side(side)
                .quantity(10)
                .limitPrice(new BigDecimal(limit))
                .status(OrderStatus.NEW)
                .build());

        when(mapper.findAccountById(1L)).thenReturn(AccountSnapshot.builder()
                .id(1L)
                .status(AccountStatus.ACTIVE)
                .cashBalance(new BigDecimal("10000.00"))
                .build());

        when(mapper.findInstrumentById(10L)).thenReturn(InstrumentSnapshot.builder()
                .id(10L)
                .symbol("AAPL")
                .tradable(true)
                .build());
    }

    private void assertLastStatus(UUID orderId, OrderStatus expected) {
        ArgumentCaptor<OrderStatus> statusCaptor = ArgumentCaptor.forClass(OrderStatus.class);
        verify(mapper).updateOrderStatusIfCurrentStatusMatches(eq(orderId), eq(OrderStatus.NEW), statusCaptor.capture(), any(Instant.class));
        assertThat(statusCaptor.getValue()).isEqualTo(expected);
    }
}
