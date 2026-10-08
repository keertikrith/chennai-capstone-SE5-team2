package com.capstone.etp.mcp;

import com.capstone.etp.exception.ModuleException;
import com.capstone.etp.mcp.McpModels.AuditView;
import com.capstone.etp.mcp.McpModels.ConnectorView;
import com.capstone.etp.mcp.McpModels.CreateConnectorRequest;
import com.capstone.etp.mcp.McpModels.CreatedConnector;
import com.capstone.etp.mcp.McpModels.ResolvedConnector;
import com.capstone.etp.security.AccountAuthorizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Makes, lists and revokes the links a customer gives an AI assistant, and resolves a presented link. The secret is
 * 256 random bits shown once; only its SHA-256 is stored, so a database leak does not leak working links. A secret
 * is never written to a log.
 */
@Slf4j
@Service
public class McpConnectorService {

    static final String PREFIX = "etpmcp_";
    private static final int MAX_TOKEN_LENGTH = 100;

    private final McpMapper mapper;
    private final AccountAuthorizer authorizer;
    private final McpProperties properties;
    private final McpTokenIssuer tokens;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public McpConnectorService(McpMapper mapper, AccountAuthorizer authorizer, McpProperties properties,
                               McpTokenIssuer tokens, Clock clock) {
        this.mapper = mapper;
        this.authorizer = authorizer;
        this.properties = properties;
        this.tokens = tokens;
        this.clock = clock;
    }

    public CreatedConnector create(Long accountId, CreateConnectorRequest request) {
        authorizer.ensureOwnAccount(accountId);

        Set<McpScope> scopes = parseScopes(request.scopes());
        BigDecimal maxValue = request.maxOrderValue() == null ? properties.getDefaultMaxOrderValue() : request.maxOrderValue();
        if (maxValue.compareTo(properties.getMaxOrderValueCeiling()) > 0) {
            throw invalid("The largest order value allowed is " + properties.getMaxOrderValueCeiling().toPlainString());
        }
        int perDay = request.maxOrdersPerDay() == null ? properties.getDefaultMaxOrdersPerDay() : request.maxOrdersPerDay();
        if (perDay > properties.getMaxOrdersPerDayCeiling()) {
            throw invalid("The most orders per day allowed is " + properties.getMaxOrdersPerDayCeiling());
        }
        int days = request.expiresInDays() == null ? properties.getDefaultExpiryDays() : request.expiresInDays();
        if (days > properties.getMaxExpiryDays()) {
            throw invalid("A link can last at most " + properties.getMaxExpiryDays() + " days");
        }

        String secret = newSecret();
        UUID id = UUID.randomUUID();
        Instant expiresAt = clock.instant().plus(Duration.ofDays(days));
        String csv = String.join(",", scopes.stream().map(Enum::name).toList());

        int inserted = mapper.insertConnectorWithinCap(id, accountId, request.label().trim(), hash(secret), csv,
                maxValue, perDay, expiresAt, properties.getMaxConnectorsPerAccount());
        if (inserted == 0) {
            throw new ModuleException(HttpStatus.CONFLICT, "MCP-409",
                    "You already have " + properties.getMaxConnectorsPerAccount() + " active links. Revoke one first.");
        }
        audit(id, accountId, "connector.create", "ok", "scopes=" + csv);

        McpConnector created = mapper.findActiveConnector(id);
        String url = properties.getPublicUrl().replaceAll("/+$", "") + "/t/" + secret + "/mcp";
        return new CreatedConnector(view(created), url);
    }

    public List<ConnectorView> list(Long accountId) {
        authorizer.ensureOwnAccount(accountId);
        return mapper.findConnectorsByAccount(accountId).stream().map(this::view).toList();
    }

    public void revoke(Long accountId, UUID connectorId) {
        authorizer.ensureOwnAccount(accountId);
        if (mapper.revokeConnector(connectorId, accountId) == 0) {
            throw new ModuleException(HttpStatus.NOT_FOUND, "MCP-404", "Link not found");
        }
        audit(connectorId, accountId, "connector.revoke", "ok", null);
    }

    public List<AuditView> recentAudit(Long accountId, int limit) {
        authorizer.ensureOwnAccount(accountId);
        int bounded = Math.max(1, Math.min(limit, 200));
        return mapper.recentAudit(accountId, bounded).stream().map(row -> new AuditView(
                ((Number) row.get("id")).longValue(),
                (UUID) row.get("connector_id"),
                (String) row.get("connector_label"),
                (String) row.get("tool"),
                (String) row.get("outcome"),
                (String) row.get("detail"),
                toInstant(row.get("created_at")))).toList();
    }

    /** The connector behind a presented secret, with a one-minute Trade API token. Empty for anything not usable. */
    public Optional<ResolvedConnector> resolve(String secret) {
        if (secret == null || !secret.startsWith(PREFIX) || secret.length() > MAX_TOKEN_LENGTH) {
            return Optional.empty();
        }
        McpConnector connector = mapper.findConnectorByHash(hash(secret));
        if (connector == null || !"ACTIVE".equals(connector.status(clock.instant()))) {
            return Optional.empty();
        }
        mapper.touchConnector(connector.getId());
        return Optional.of(new ResolvedConnector(
                connector.getId(),
                connector.getAccountId(),
                connector.getLabel(),
                connector.scopeSet().stream().map(Enum::name).toList(),
                connector.getMaxOrderValue(),
                connector.getMaxOrdersPerDay(),
                tokens.tokenFor(connector.getAccountId()),
                (int) McpTokenIssuer.LIFETIME.toSeconds()));
    }

    /** A connector that may still be used, or a 401: a revoked link stops working on the very next call. */
    public McpConnector requireActive(UUID connectorId) {
        McpConnector connector = mapper.findActiveConnector(connectorId);
        if (connector == null) {
            throw new ModuleException(HttpStatus.UNAUTHORIZED, "AUTH-401", "Unauthorised");
        }
        return connector;
    }

    public void audit(UUID connectorId, Long accountId, String tool, String outcome, String detail) {
        try {
            mapper.insertAudit(connectorId, accountId, tool, outcome, detail);
        } catch (RuntimeException e) {
            // An audit failure must not turn a read into an error, but it must not be silent either.
            log.warn("[MCP_AUDIT_FAILED] tool={} reason={}", tool, e.getClass().getSimpleName());
        }
    }

    private ConnectorView view(McpConnector c) {
        return new ConnectorView(c.getId(), c.getLabel(), c.scopeSet().stream().map(Enum::name).toList(),
                c.getMaxOrderValue(), c.getMaxOrdersPerDay(), c.status(clock.instant()),
                c.getCreatedAt(), c.getExpiresAt(), c.getLastUsedAt());
    }

    private Set<McpScope> parseScopes(List<String> requested) {
        Set<McpScope> scopes = EnumSet.of(McpScope.READ);
        for (String name : requested) {
            try {
                scopes.add(McpScope.valueOf(name == null ? "" : name.trim().toUpperCase()));
            } catch (IllegalArgumentException e) {
                throw invalid("Unknown permission: " + name);
            }
        }
        return scopes;
    }

    private String newSecret() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String secret) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static Instant toInstant(Object value) {
        if (value instanceof java.sql.Timestamp ts) {
            return ts.toInstant();
        }
        if (value instanceof java.time.OffsetDateTime odt) {
            return odt.toInstant();
        }
        return value instanceof Instant i ? i : null;
    }

    private static ModuleException invalid(String message) {
        return new ModuleException(HttpStatus.UNPROCESSABLE_ENTITY, "VAL-422", message);
    }
}
