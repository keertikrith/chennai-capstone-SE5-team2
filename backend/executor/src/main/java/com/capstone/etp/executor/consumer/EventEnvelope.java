package com.capstone.etp.executor.consumer;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

@Data
public class EventEnvelope {
    private String eventId;
    private String eventType;
    @JsonAlias("occurredAt")
    private String eventTime;
    private String source;
    private Integer schemaVersion;
    private JsonNode payload;
}
