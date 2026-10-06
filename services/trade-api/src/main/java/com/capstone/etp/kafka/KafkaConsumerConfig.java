package com.capstone.etp.kafka;

import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Failure handling shared by every consumer in this service, as contracts/kafka-topics.md asks.
 *
 * A malformed message goes to {@code <topic>.DLT} on the first attempt. A transient failure (the
 * database for a moment unavailable) is retried a bounded number of times and then dead-lettered,
 * so one bad message can never hold a partition for ever. Offsets are only committed after the
 * listener has returned, never before (auto-commit is off).
 */
@Configuration
public class KafkaConsumerConfig {

    static final long RETRY_INTERVAL_MS = 1_000L;
    static final long MAX_RETRIES = 4L;

    @Bean
    public DefaultErrorHandler kafkaConsumerErrorHandler(KafkaTemplate<String, String> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, exception) -> new TopicPartition(record.topic() + ".DLT", -1));

        DefaultErrorHandler handler = new DefaultErrorHandler(
                recoverer, new FixedBackOff(RETRY_INTERVAL_MS, MAX_RETRIES));
        handler.addNotRetryableExceptions(MalformedEventException.class, IllegalArgumentException.class);
        return handler;
    }
}
