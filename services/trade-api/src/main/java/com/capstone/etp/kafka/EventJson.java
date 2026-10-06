package com.capstone.etp.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

/**
 * Reads a message with money kept as exact decimals. A plain {@code readTree} parses 120.00 as a
 * double and strips trailing zeros from decimals, so prices lose their scale and long figures can lose
 * precision. Everything that does arithmetic on a price from an event or a quote reads it through here.
 */
public final class EventJson {

    private EventJson() {
    }

    public static JsonNode parse(ObjectMapper mapper, String json) throws JsonProcessingException {
        return mapper.reader()
                .with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .with(JsonNodeFactory.withExactBigDecimals(true))
                .readTree(json);
    }
}
