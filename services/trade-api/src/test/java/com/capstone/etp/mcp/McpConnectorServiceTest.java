package com.capstone.etp.mcp;

import com.capstone.etp.exception.ModuleException;
import com.capstone.etp.mcp.McpModels.CreateConnectorRequest;
import com.capstone.etp.mcp.McpModels.CreatedConnector;
import com.capstone.etp.security.AccountAuthorizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** A link's secret is shown once and stored only as a hash; a revoked or expired link stops resolving. */
class McpConnectorServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-08T10:00:00Z");

    private McpMapper mapper;
    private McpTokenIssuer tokens;
    private McpConnectorService service;

    @BeforeEach
    void setUp() {
        mapper = mock(McpMapper.class);
        tokens = mock(McpTokenIssuer.class);
        McpProperties properties = new McpProperties();
        properties.setPublicUrl("https://mcp.example.test/");
        service = new McpConnectorService(mapper, mock(AccountAuthorizer.class), properties, tokens,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createReturnsTheSecretLinkOnceAndStoresOnlyItsHash() {
        when(mapper.insertConnectorWithinCap(any(), any(), any(), any(), any(), any(), anyInt(), any(), anyInt())).thenReturn(1);
        McpConnector stored = new McpConnector();
        stored.setId(UUID.randomUUID());
        stored.setScopes("READ,ALERTS");
        stored.setMaxOrderValue(new BigDecimal("10000"));
        stored.setMaxOrdersPerDay(5);
        stored.setExpiresAt(NOW.plusSeconds(3600));
        when(mapper.findActiveConnector(any())).thenReturn(stored);

        CreatedConnector created = service.create(7L,
                new CreateConnectorRequest("My Claude", List.of("alerts"), null, null, null));

        String url = created.url();
        assertTrue(url.startsWith("https://mcp.example.test/t/etpmcp_"));
        assertTrue(url.endsWith("/mcp"));
        String secret = url.substring(url.indexOf("/t/") + 3, url.length() - "/mcp".length());
        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> scopes = ArgumentCaptor.forClass(String.class);
        verify(mapper).insertConnectorWithinCap(any(), eq(7L), eq("My Claude"), hash.capture(), scopes.capture(),
                eq(new BigDecimal("10000")), eq(5), any(), eq(5));
        assertEquals(McpConnectorService.hash(secret), hash.getValue());
        assertFalse(hash.getValue().contains(secret));
        assertEquals("READ,ALERTS", scopes.getValue());
    }

    @Test
    void createRefusesAnUnknownPermissionAndAnOversizedLimit() {
        assertThrows(ModuleException.class, () -> service.create(7L,
                new CreateConnectorRequest("x", List.of("withdraw"), null, null, null)));
        assertThrows(ModuleException.class, () -> service.create(7L,
                new CreateConnectorRequest("x", List.of("trade"), new BigDecimal("99999999"), null, null)));
    }

    @Test
    void createRefusesWhenTheAccountAlreadyHasTheMostLinks() {
        when(mapper.insertConnectorWithinCap(any(), any(), any(), any(), any(), any(), anyInt(), any(), anyInt())).thenReturn(0);

        ModuleException e = assertThrows(ModuleException.class, () -> service.create(7L,
                new CreateConnectorRequest("x", List.of("trade"), null, null, null)));

        assertEquals("MCP-409", e.getCatalogueCode());
    }

    @Test
    void resolveRejectsAnythingThatIsNotAWellFormedActiveSecret() {
        assertTrue(service.resolve(null).isEmpty());
        assertTrue(service.resolve("not-a-link").isEmpty());
        assertTrue(service.resolve("etpmcp_" + "a".repeat(200)).isEmpty());
        when(mapper.findConnectorByHash(any())).thenReturn(null);
        assertTrue(service.resolve("etpmcp_unknown").isEmpty());
    }

    @Test
    void resolveRefusesARevokedOrExpiredLinkAndServesAnActiveOne() {
        McpConnector c = new McpConnector();
        c.setId(UUID.randomUUID());
        c.setAccountId(7L);
        c.setLabel("L");
        c.setScopes("READ");
        c.setMaxOrderValue(BigDecimal.TEN);
        c.setMaxOrdersPerDay(1);
        c.setExpiresAt(NOW.plusSeconds(60));
        when(mapper.findConnectorByHash(any())).thenReturn(c);
        when(tokens.tokenFor(7L)).thenReturn("jwt");

        Optional<McpModels.ResolvedConnector> ok = service.resolve("etpmcp_secret");
        assertTrue(ok.isPresent());
        assertEquals("jwt", ok.get().jwt());
        assertEquals(List.of("READ"), ok.get().scopes());

        c.setRevokedAt(NOW.minusSeconds(1));
        assertTrue(service.resolve("etpmcp_secret").isEmpty());

        c.setRevokedAt(null);
        c.setExpiresAt(NOW);
        assertTrue(service.resolve("etpmcp_secret").isEmpty());
    }
}
