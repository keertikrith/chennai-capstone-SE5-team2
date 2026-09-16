package com.capstone.etp.executor.consumer;

import com.capstone.etp.executor.service.OrderExecutionService;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderMessageConsumer {

    private final OrderExecutionService orderExecutionService;
    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @KafkaListener(
            topics = "${app.kafka.topics.orders:orders}",
            groupId = "${app.kafka.consumer-group:trade-executor}")
    public void onMessage(String rawMessage) {
        EventEnvelope envelope;
        try {
            envelope = mapper.readValue(rawMessage, EventEnvelope.class);
        } catch (Exception ex) {
            log.error("Malformed ORDER_PLACED event: {}", ex.getMessage());
            return;
        }

        if (!"ORDER_PLACED".equals(envelope.getEventType())) {
            log.warn("Ignoring unexpected eventType={}", envelope.getEventType());
            return;
        }
        if (envelope.getEventTime() == null || envelope.getEventTime().isBlank()) {
            log.warn("Ignoring ORDER_PLACED event with missing eventTime/occurredAt");
            return;
        }

        OrderPlacedPayload payload = mapper.convertValue(envelope.getPayload(), OrderPlacedPayload.class);
        if (payload.getOrderId() == null) {
            log.warn("Ignoring ORDER_PLACED event with missing orderId");
            return;
        }

        orderExecutionService.handleOrderPlaced(payload.getOrderId());
    }
}
