package com.capstone.etp.mcp;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/** One link a customer made for an AI assistant. Only the hash of its secret is kept. */
@Getter
@Setter
@NoArgsConstructor
public class McpConnector {

    private UUID id;
    private Long accountId;
    private String label;
    private String tokenHash;
    private String scopes;
    private BigDecimal maxOrderValue;
    private Integer maxOrdersPerDay;
    private Instant createdAt;
    private Instant expiresAt;
    private Instant lastUsedAt;
    private Instant revokedAt;

    public Set<McpScope> scopeSet() {
        Set<McpScope> result = EnumSet.noneOf(McpScope.class);
        if (scopes != null && !scopes.isBlank()) {
            Arrays.stream(scopes.split(",")).map(String::trim).forEach(s -> result.add(McpScope.valueOf(s)));
        }
        return result;
    }

    public boolean has(McpScope scope) {
        return scopeSet().contains(scope);
    }

    public String status(Instant now) {
        if (revokedAt != null) {
            return "REVOKED";
        }
        return expiresAt != null && !expiresAt.isAfter(now) ? "EXPIRED" : "ACTIVE";
    }
}
