package com.capstone.etp.mcp;

import com.capstone.etp.exception.ModuleException;
import com.capstone.etp.mcp.McpModels.AuditRequest;
import com.capstone.etp.mcp.McpModels.PendingOrderView;
import com.capstone.etp.mcp.McpModels.PrepareOrderRequest;
import com.capstone.etp.mcp.McpModels.ResolveRequest;
import com.capstone.etp.mcp.McpModels.ResolvedConnector;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.UUID;

/**
 * The MCP server's door. It is not a customer API: every call must carry the shared service key, and without a
 * configured key every call is refused. Order rules live here, in the Trade API, never in the MCP server.
 */
@RestController
@RequestMapping("/internal/mcp")
@RequiredArgsConstructor
public class McpInternalController {

    static final String KEY_HEADER = "X-Mcp-Service-Key";

    private final McpConnectorService connectors;
    private final McpOrderService orders;
    private final McpProperties properties;

    @PostMapping("/resolve")
    public ResponseEntity<ResolvedConnector> resolve(@RequestHeader(value = KEY_HEADER, required = false) String key,
                                                     @Valid @RequestBody ResolveRequest request) {
        requireKey(key);
        return connectors.resolve(request.token())
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ModuleException(HttpStatus.UNAUTHORIZED, "AUTH-401", "Unauthorised"));
    }

    @PostMapping("/connectors/{connectorId}/orders/prepare")
    public ResponseEntity<PendingOrderView> prepare(@RequestHeader(value = KEY_HEADER, required = false) String key,
                                                    @PathVariable UUID connectorId,
                                                    @Valid @RequestBody PrepareOrderRequest request) {
        requireKey(key);
        return ResponseEntity.status(HttpStatus.CREATED).body(orders.prepare(connectorId, request));
    }

    @PostMapping("/connectors/{connectorId}/orders/{pendingId}/confirm")
    public ResponseEntity<PendingOrderView> confirm(@RequestHeader(value = KEY_HEADER, required = false) String key,
                                                    @PathVariable UUID connectorId,
                                                    @PathVariable UUID pendingId) {
        requireKey(key);
        return ResponseEntity.ok(orders.confirm(connectorId, pendingId));
    }

    @GetMapping("/connectors/{connectorId}/orders/{pendingId}")
    public ResponseEntity<PendingOrderView> status(@RequestHeader(value = KEY_HEADER, required = false) String key,
                                                   @PathVariable UUID connectorId,
                                                   @PathVariable UUID pendingId) {
        requireKey(key);
        return ResponseEntity.ok(orders.status(connectorId, pendingId));
    }

    @DeleteMapping("/connectors/{connectorId}/placed-orders/{orderId}")
    public ResponseEntity<Map<String, Object>> cancel(@RequestHeader(value = KEY_HEADER, required = false) String key,
                                                      @PathVariable UUID connectorId,
                                                      @PathVariable UUID orderId) {
        requireKey(key);
        McpOrderGateway.CancelResult result = orders.cancel(connectorId, orderId);
        return ResponseEntity.ok(Map.of("cancelled", result.cancelled(), "detail", result.detail()));
    }

    @PostMapping("/connectors/{connectorId}/audit")
    public ResponseEntity<Void> audit(@RequestHeader(value = KEY_HEADER, required = false) String key,
                                      @PathVariable UUID connectorId,
                                      @Valid @RequestBody AuditRequest request) {
        requireKey(key);
        McpConnector connector = connectors.requireActive(connectorId);
        connectors.audit(connectorId, connector.getAccountId(), request.tool(), request.outcome(), request.detail());
        return ResponseEntity.noContent().build();
    }

    /** Compares digests so the comparison takes the same time whatever the key looks like. */
    private void requireKey(String presented) {
        String expected = properties.getServiceKey();
        if (expected == null || expected.isBlank() || presented == null
                || !MessageDigest.isEqual(digest(expected), digest(presented))) {
            throw new ModuleException(HttpStatus.UNAUTHORIZED, "AUTH-401", "Unauthorised");
        }
    }

    private static byte[] digest(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
