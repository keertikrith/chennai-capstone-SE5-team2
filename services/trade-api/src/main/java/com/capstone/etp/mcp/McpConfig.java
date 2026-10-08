package com.capstone.etp.mcp;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/** Wires AI assistant (MCP) access. The gateway is its own type, so the automation module's TradeApiClient stays the only one. */
@Configuration
@EnableConfigurationProperties(McpProperties.class)
class McpConfig {

    @Bean
    McpOrderGateway mcpOrderGateway(McpProperties properties, McpTokenIssuer tokens) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()));
        factory.setReadTimeout(Duration.ofMillis(properties.getReadTimeoutMs()));
        RestClient restClient = RestClient.builder()
                .baseUrl(properties.getTradeApiBaseUrl())
                .requestFactory(factory)
                .build();
        return new RestMcpOrderGateway(restClient, tokens);
    }
}
