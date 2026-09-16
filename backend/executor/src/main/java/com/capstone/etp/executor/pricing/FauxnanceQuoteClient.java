package com.capstone.etp.executor.pricing;

import com.capstone.etp.executor.domain.Quote;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Duration;

@Slf4j
@Component
public class FauxnanceQuoteClient implements QuoteClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String apiKey;
    private final int maxAttempts;
    private final long initialBackoffMillis;

    public FauxnanceQuoteClient(
            RestTemplateBuilder builder,
            @Value("${app.fauxnance.base-url}") String baseUrl,
            @Value("${FAUXNANCE_API_KEY}") String apiKey,
            @Value("${app.fauxnance.quote-retry.max-attempts:3}") int maxAttempts,
            @Value("${app.fauxnance.quote-retry.initial-backoff-ms:300}") long initialBackoffMillis) {
        this.restTemplate = builder.connectTimeout(Duration.ofSeconds(5)).readTimeout(Duration.ofSeconds(5))
                .build();
        this.baseUrl = trimTrailingSlash(baseUrl);
        this.apiKey = apiKey;
        this.maxAttempts = maxAttempts;
        this.initialBackoffMillis = initialBackoffMillis;
    }

    @Override
    public Quote fetchQuote(String symbol) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Api-Key", apiKey);

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                ResponseEntity<JsonNode> response = restTemplate.exchange(
                        baseUrl + "/quotes/" + symbol,
                        HttpMethod.GET,
                        new HttpEntity<>(headers),
                        JsonNode.class);
                return parseQuote(symbol, response.getBody());
            } catch (HttpStatusCodeException ex) {
                HttpStatusCode status = ex.getStatusCode();
                if (isNoPriceStatus(status)) {
                    throw new PricingUnavailableException("No price available for symbol " + symbol);
                }
                if (!isTransientStatus(status) || attempt == maxAttempts) {
                    throw new PricingUnavailableException("Failed to fetch quote for " + symbol + " status=" + status.value(), ex);
                }
                backoff(attempt);
            } catch (ResourceAccessException ex) {
                if (attempt == maxAttempts) {
                    throw new PricingUnavailableException("Failed to fetch quote for " + symbol + " after retries", ex);
                }
                backoff(attempt);
            }
        }

        throw new PricingUnavailableException("No price available for symbol " + symbol);
    }

    private Quote parseQuote(String symbol, JsonNode body) {
        if (body == null) {
            throw new PricingUnavailableException("Quote payload is empty for symbol " + symbol);
        }

        JsonNode quoteNode = body.path("data");
        if (quoteNode.isMissingNode() || quoteNode.isNull()) {
            quoteNode = body;
        }

        BigDecimal bid = decimalField(quoteNode, "bid");
        BigDecimal ask = decimalField(quoteNode, "ask");
        BigDecimal price = decimalField(quoteNode, "price");
        String quoteSymbol = textField(quoteNode, "symbol");
        if (quoteSymbol == null || quoteSymbol.isBlank()) {
            quoteSymbol = symbol;
        }

        if (bid == null || ask == null) {
            throw new PricingUnavailableException("Quote is missing bid/ask for symbol " + symbol);
        }

        return Quote.builder()
                .symbol(quoteSymbol)
                .bid(bid)
                .ask(ask)
                .last(price)
                .build();
    }

    private BigDecimal decimalField(JsonNode body, String field) {
        JsonNode node = body.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.decimalValue();
        }
        if (node.isTextual()) {
            try {
                return new BigDecimal(node.asText());
            } catch (NumberFormatException ex) {
                throw new PricingUnavailableException("Quote field is not numeric: " + field, ex);
            }
        }
        throw new PricingUnavailableException("Quote field has unsupported shape: " + field);
    }

    private String textField(JsonNode body, String field) {
        JsonNode node = body.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return node.asText();
        }
        return String.valueOf(node.asText());
    }

    private boolean isTransientStatus(HttpStatusCode status) {
        int code = status.value();
        return code == 429 || code >= 500;
    }

    private boolean isNoPriceStatus(HttpStatusCode status) {
        int code = status.value();
        return code == 404 || code == 422;
    }

    private void backoff(int attempt) {
        long sleepMillis = initialBackoffMillis * (1L << (attempt - 1));
        try {
            Thread.sleep(sleepMillis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new PricingUnavailableException("Interrupted while retrying quote request", ex);
        }
    }

    private String trimTrailingSlash(String value) {
        if (value.endsWith("/")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }
}
