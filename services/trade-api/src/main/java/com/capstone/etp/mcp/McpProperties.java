package com.capstone.etp.mcp;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/** app.mcp.*: how the AI assistant (MCP) access behaves. Without a service key the internal API refuses everything. */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.mcp")
public class McpProperties {

    /** Shared with the MCP server, which presents it as X-Mcp-Service-Key. Empty means the internal API is off. */
    private String serviceKey = "";

    /** Where the MCP server is reachable from the customer's browser; the connector link is built from it. */
    private String publicUrl = "http://localhost:8090";

    private String tradeApiBaseUrl = "http://localhost:8084";
    private int connectTimeoutMs = 2000;
    private int readTimeoutMs = 4000;

    /** How long a prepared order can be confirmed. */
    private int pendingTtlSeconds = 120;

    /** Prepared orders a connector may have waiting at once. */
    private int maxPendingPerConnector = 5;

    private int maxConnectorsPerAccount = 5;
    private int maxExpiryDays = 90;
    private int defaultExpiryDays = 30;

    private BigDecimal defaultMaxOrderValue = new BigDecimal("10000");
    private BigDecimal maxOrderValueCeiling = new BigDecimal("1000000");
    private int defaultMaxOrdersPerDay = 5;
    private int maxOrdersPerDayCeiling = 50;
}
