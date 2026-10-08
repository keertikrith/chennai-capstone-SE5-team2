package com.capstone.etp.mcp;

import com.capstone.etp.mcp.McpModels.AuditView;
import com.capstone.etp.mcp.McpModels.ConnectorView;
import com.capstone.etp.mcp.McpModels.CreateConnectorRequest;
import com.capstone.etp.mcp.McpModels.CreatedConnector;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** The customer's side: make, list and revoke the links that let an AI assistant use their account. */
@RestController
@RequestMapping("/api/v1/accounts/{accountId}/mcp")
@RequiredArgsConstructor
public class McpController {

    private final McpConnectorService service;

    @PostMapping("/connectors")
    public ResponseEntity<CreatedConnector> create(@PathVariable Long accountId,
                                                   @Valid @RequestBody CreateConnectorRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(accountId, request));
    }

    @GetMapping("/connectors")
    public ResponseEntity<List<ConnectorView>> list(@PathVariable Long accountId) {
        return ResponseEntity.ok(service.list(accountId));
    }

    @DeleteMapping("/connectors/{connectorId}")
    public ResponseEntity<Void> revoke(@PathVariable Long accountId, @PathVariable UUID connectorId) {
        service.revoke(accountId, connectorId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/audit")
    public ResponseEntity<List<AuditView>> audit(@PathVariable Long accountId,
                                                 @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(service.recentAudit(accountId, limit));
    }
}
