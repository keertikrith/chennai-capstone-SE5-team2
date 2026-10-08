package com.capstone.etp.mcp;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** The request and response shapes of the AI assistant (MCP) access endpoints. */
public final class McpModels {

    private McpModels() {
    }

    /** What the customer chooses when making a link. Anything left out takes the configured default. */
    public record CreateConnectorRequest(
            @NotBlank @Size(max = 60) String label,
            @NotEmpty List<String> scopes,
            @DecimalMin("1") BigDecimal maxOrderValue,
            @Min(1) Integer maxOrdersPerDay,
            @Min(1) Integer expiresInDays) {
    }

    public record ConnectorView(
            UUID id,
            String label,
            List<String> scopes,
            BigDecimal maxOrderValue,
            int maxOrdersPerDay,
            String status,
            Instant createdAt,
            Instant expiresAt,
            Instant lastUsedAt) {
    }

    /** Returned once, at creation. The link carries the secret and cannot be shown again. */
    public record CreatedConnector(ConnectorView connector, String url) {
    }

    public record AuditView(Long id, UUID connectorId, String connectorLabel, String tool, String outcome,
                            String detail, Instant createdAt) {
    }

    /** What the MCP server learns about a link it has been given, with a one-minute token to call the Trade API. */
    public record ResolvedConnector(
            UUID connectorId,
            Long accountId,
            String label,
            List<String> scopes,
            BigDecimal maxOrderValue,
            int maxOrdersPerDay,
            String jwt,
            int jwtTtlSeconds) {
    }

    public record ResolveRequest(@NotBlank @Size(max = 128) String token) {
    }

    public record PrepareOrderRequest(
            @NotBlank @Size(max = 20) @Pattern(regexp = "^[A-Za-z0-9.:^=\\-]+$") String symbol,
            @NotBlank String side,
            @NotNull @Min(1) @Max(1_000_000) Integer quantity,
            @NotNull @DecimalMin("0.01") BigDecimal price) {
    }

    public record AuditRequest(
            @NotBlank @Size(max = 60) String tool,
            @NotBlank @Size(max = 20) String outcome,
            @Size(max = 300) String detail) {
    }

    public record PendingOrderView(
            UUID pendingId,
            String status,
            String symbol,
            String side,
            int quantity,
            BigDecimal price,
            BigDecimal estimatedValue,
            Instant expiresAt,
            UUID orderId,
            String message) {
    }
}
