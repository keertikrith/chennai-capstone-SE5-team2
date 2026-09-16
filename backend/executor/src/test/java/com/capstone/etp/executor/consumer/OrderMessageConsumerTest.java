package com.capstone.etp.executor.consumer;

import com.capstone.etp.executor.service.OrderExecutionService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.UUID;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class OrderMessageConsumerTest {

    @Test
    void processesOrderPlacedAndIgnoresUnknownFields() {
        OrderExecutionService service = Mockito.mock(OrderExecutionService.class);
        OrderMessageConsumer consumer = new OrderMessageConsumer(service);
        UUID orderId = UUID.randomUUID();

        String message = """
                {
                  "eventId":"evt-1",
                  "eventType":"ORDER_PLACED",
                  "eventTime":"2026-09-16T10:00:00Z",
                  "source":"trade-api",
                  "schemaVersion":1,
                  "futureField":"ignore-me",
                  "payload":{"orderId":"%s","newPayloadField":"ignore-me-too"}
                }
                """.formatted(orderId);

        consumer.onMessage(message);

        verify(service).handleOrderPlaced(orderId);
    }

    @Test
    void ignoresUnexpectedEventType() {
        OrderExecutionService service = Mockito.mock(OrderExecutionService.class);
        OrderMessageConsumer consumer = new OrderMessageConsumer(service);

        consumer.onMessage("""
                {"eventType":"ORDER_CANCELLED","payload":{"orderId":"d290f1ee-6c54-4b01-90e6-d701748f0851"}}
                """);

        verify(service, never()).handleOrderPlaced(Mockito.any());
    }

    @Test
    void acceptsOccurredAtAliasForCompatibility() {
        OrderExecutionService service = Mockito.mock(OrderExecutionService.class);
        OrderMessageConsumer consumer = new OrderMessageConsumer(service);
        UUID orderId = UUID.randomUUID();

        consumer.onMessage("""
                {
                  "eventId":"evt-2",
                  "eventType":"ORDER_PLACED",
                  "occurredAt":"2026-09-16T10:00:00Z",
                  "source":"trade-api",
                  "payload":{"orderId":"%s"}
                }
                """.formatted(orderId));

        verify(service).handleOrderPlaced(orderId);
    }
}
