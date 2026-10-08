package com.capstone.etp.mcp;

import com.capstone.etp.TradeApiApplication;
import com.capstone.etp.automation.TradeApiClient.SubmissionResult;
import com.capstone.etp.dto.PlaceOrderRequest;
import com.capstone.etp.security.JwtTestFixture;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AI assistant (MCP) access over real HTTP and a real database: the customer's routes, the internal door the MCP
 * server uses, the SQL, and the rules that make an order two steps and at most once. Only the final call to the
 * order endpoint is replaced, so no order, balance or Kafka message is touched.
 */
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
@SpringBootTest(classes = TradeApiApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "app.auth.jwt.secret=integration-test-secret-integration-test-secret",
                "app.market-data.consumer.enabled=false",
                "app.notifications.consumer.enabled=false",
                "app.portfolio.consumer.enabled=false",
                "app.notifications.dispatch-interval-ms=3600000",
                "app.alerts.handover-retry-ms=3600000",
                "app.mcp.service-key=test-service-key",
                "app.mcp.public-url=https://mcp.example.test",
                "spring.kafka.bootstrap-servers=localhost:1"
        })
@Import(McpChainIT.StubGateway.class)
class McpChainIT {

    private static final String SECRET = "integration-test-secret-integration-test-secret";
    private static final String KEY = "test-service-key";
    private static final ObjectMapper JSON = new ObjectMapper();

    private static final AtomicReference<SubmissionResult> NEXT_RESULT = new AtomicReference<>();
    private static final List<PlaceOrderRequest> SUBMITTED = new CopyOnWriteArrayList<>();

    @TestConfiguration
    static class StubGateway {
        @Bean
        @Primary
        McpOrderGateway stubOrderGateway() {
            return new McpOrderGateway() {
                @Override
                public SubmissionResult submit(PlaceOrderRequest request) {
                    SUBMITTED.add(request);
                    return NEXT_RESULT.get();
                }

                @Override
                public CancelResult cancel(Long accountId, UUID orderId) {
                    return new CancelResult(true, "status CANCELLED");
                }
            };
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("TEST_DB_URL"));
        registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("TEST_DB_USER", "postgres"));
        registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("TEST_DB_PASSWORD", "postgres"));
    }

    @LocalServerPort
    int port;
    @Autowired
    JdbcTemplate jdbc;

    private final HttpClient http = HttpClient.newHttpClient();

    private record Reply(int status, JsonNode body) {
    }

    @BeforeEach
    void clean() {
        jdbc.execute("TRUNCATE mcp_audit_log, mcp_pending_orders, mcp_connectors RESTART IDENTITY CASCADE");
        SUBMITTED.clear();
        NEXT_RESULT.set(SubmissionResult.submitted(UUID.randomUUID(), "status NEW"));
    }

    private static String token(long accountId) {
        Instant now = Instant.now();
        return JwtTestFixture.token(SECRET, "auth-service", accountId, List.of("CUSTOMER"), now, now.plusSeconds(900));
    }

    private Reply call(String method, String path, Long asAccount, String serviceKey, String json) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("Content-Type", "application/json")
                .method(method, json == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json));
        if (asAccount != null) {
            request.header("Authorization", "Bearer " + token(asAccount));
        }
        if (serviceKey != null) {
            request.header("X-Mcp-Service-Key", serviceKey);
        }
        HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        return new Reply(response.statusCode(), response.body().isBlank() ? JSON.nullNode() : JSON.readTree(response.body()));
    }

    private Reply createLink(long account, String scopesJson, String extra) throws Exception {
        return call("POST", "/api/v1/accounts/" + account + "/mcp/connectors", account, null,
                "{\"label\":\"My Claude\",\"scopes\":" + scopesJson + extra + "}");
    }

    private static String secretOf(Reply created) {
        String url = created.body().path("url").asText();
        return url.substring(url.indexOf("/t/") + 3, url.length() - "/mcp".length());
    }

    private Reply resolve(String secret) throws Exception {
        return call("POST", "/internal/mcp/resolve", null, KEY, "{\"token\":\"" + secret + "\"}");
    }

    private Reply prepare(String connectorId, int quantity, String price) throws Exception {
        return call("POST", "/internal/mcp/connectors/" + connectorId + "/orders/prepare", null, KEY,
                "{\"symbol\":\"aapl\",\"side\":\"BUY\",\"quantity\":" + quantity + ",\"price\":" + price + "}");
    }

    private Reply confirm(String connectorId, String pendingId) throws Exception {
        return call("POST", "/internal/mcp/connectors/" + connectorId + "/orders/" + pendingId + "/confirm", null, KEY, null);
    }

    // ---------------------------------------------------------------- the customer's side

    @Test
    void aCustomerMakesAListsAndRevokesALinkAndOnlyTheHashIsStored() throws Exception {
        Reply created = createLink(1, "[\"alerts\",\"trade\"]", ",\"maxOrderValue\":2000,\"maxOrdersPerDay\":2");

        assertEquals(201, created.status());
        String secret = secretOf(created);
        assertTrue(created.body().path("url").asText().startsWith("https://mcp.example.test/t/etpmcp_"));
        assertEquals("ACTIVE", created.body().path("connector").path("status").asText());

        String stored = jdbc.queryForObject("SELECT token_hash FROM mcp_connectors", String.class);
        assertNotEquals(secret, stored);
        assertFalse(stored.contains(secret));
        assertEquals(64, stored.length());

        Reply list = call("GET", "/api/v1/accounts/1/mcp/connectors", 1L, null, null);
        assertEquals(1, list.body().size());
        assertFalse(list.body().toString().contains(secret), "the list must never carry the secret");

        String id = created.body().path("connector").path("id").asText();
        assertEquals(204, call("DELETE", "/api/v1/accounts/1/mcp/connectors/" + id, 1L, null, null).status());
        assertEquals("REVOKED", call("GET", "/api/v1/accounts/1/mcp/connectors", 1L, null, null).body().get(0).path("status").asText());
        assertEquals(404, call("DELETE", "/api/v1/accounts/1/mcp/connectors/" + id, 1L, null, null).status());
    }

    @Test
    void anotherCustomerCannotMakeListOrRevokeLinksOnMyAccount() throws Exception {
        assertEquals(403, createLinkAs(5, 1));
        assertEquals(403, call("GET", "/api/v1/accounts/1/mcp/connectors", 5L, null, null).status());
        assertEquals(403, call("GET", "/api/v1/accounts/1/mcp/audit", 5L, null, null).status());
        assertEquals(401, call("GET", "/api/v1/accounts/1/mcp/connectors", null, null, null).status());
    }

    private int createLinkAs(long asAccount, long forAccount) throws Exception {
        return call("POST", "/api/v1/accounts/" + forAccount + "/mcp/connectors", asAccount, null,
                "{\"label\":\"x\",\"scopes\":[\"trade\"]}").status();
    }

    @Test
    void refusesAnUnknownPermissionAndAnOversizedLimit() throws Exception {
        assertEquals(422, createLink(1, "[\"withdraw\"]", "").status());
        assertEquals(422, createLink(1, "[\"trade\"]", ",\"maxOrderValue\":99999999").status());
    }

    @Test
    void anAccountCannotHoldMoreActiveLinksThanTheCap() throws Exception {
        for (int i = 0; i < 5; i++) {
            assertEquals(201, createLink(1, "[\"alerts\"]", "").status());
        }
        assertEquals(409, createLink(1, "[\"alerts\"]", "").status());
    }

    // ---------------------------------------------------------------- the internal door

    @Test
    void theInternalDoorRefusesAnythingWithoutTheServiceKey() throws Exception {
        assertEquals(401, call("POST", "/internal/mcp/resolve", null, null, "{\"token\":\"etpmcp_x\"}").status());
        assertEquals(401, call("POST", "/internal/mcp/resolve", null, "wrong-key", "{\"token\":\"etpmcp_x\"}").status());
        assertEquals(401, call("POST", "/internal/mcp/resolve", 1L, null, "{\"token\":\"etpmcp_x\"}").status(),
                "a customer's own login token is not the service key");
    }

    @Test
    void resolveGivesTheAccountScopesLimitsAndAShortToken_andStopsAfterRevocation() throws Exception {
        Reply created = createLink(1, "[\"alerts\"]", "");
        String secret = secretOf(created);
        String id = created.body().path("connector").path("id").asText();

        Reply resolved = resolve(secret);
        assertEquals(200, resolved.status());
        assertEquals(1, resolved.body().path("accountId").asInt());
        assertEquals(List.of("READ", "ALERTS"), List.of(resolved.body().path("scopes").get(0).asText(), resolved.body().path("scopes").get(1).asText()));
        assertFalse(resolved.body().path("jwt").asText().isBlank());

        // the short token really is accepted by the Trade API for that account, and only that account
        assertEquals(200, callWithBearer("/api/v1/accounts/1/alerts", resolved.body().path("jwt").asText()));
        assertEquals(403, callWithBearer("/api/v1/accounts/5/alerts", resolved.body().path("jwt").asText()));

        assertEquals(401, resolve("etpmcp_notarealsecretnotarealsecretnotarealsecret1").status());

        call("DELETE", "/api/v1/accounts/1/mcp/connectors/" + id, 1L, null, null);
        assertEquals(401, resolve(secret).status());
    }

    private int callWithBearer(String path, String jwt) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("Authorization", "Bearer " + jwt).GET().build();
        return http.send(request, HttpResponse.BodyHandlers.ofString()).statusCode();
    }

    // ---------------------------------------------------------------- two-step orders

    @Test
    void prepareDoesNotTradeAndConfirmPlacesTheOrderExactlyOnce() throws Exception {
        String secret = secretOf(createLink(1, "[\"trade\"]", ",\"maxOrderValue\":2000,\"maxOrdersPerDay\":5"));
        String id = resolve(secret).body().path("connectorId").asText();

        Reply prepared = prepare(id, 5, "100.00");
        assertEquals(201, prepared.status());
        assertEquals("PENDING", prepared.body().path("status").asText());
        assertEquals("AAPL", prepared.body().path("symbol").asText());
        assertEquals(500.0, prepared.body().path("estimatedValue").asDouble());
        assertTrue(SUBMITTED.isEmpty(), "preparing must never trade");
        String pendingId = prepared.body().path("pendingId").asText();

        Reply placed = confirm(id, pendingId);
        assertEquals(200, placed.status());
        assertEquals("PLACED", placed.body().path("status").asText());
        assertEquals(1, SUBMITTED.size());
        assertEquals(UUID.fromString(pendingId), SUBMITTED.get(0).getIdempotencyKey());
        assertEquals(1L, SUBMITTED.get(0).getAccountId());

        assertEquals(409, confirm(id, pendingId).status(), "a second confirm must not place a second order");
        assertEquals(1, SUBMITTED.size());

        Reply status = call("GET", "/internal/mcp/connectors/" + id + "/orders/" + pendingId, null, KEY, null);
        assertEquals("PLACED", status.body().path("status").asText());
    }

    @Test
    void aLinkWithoutTheTradePermissionCannotPrepare() throws Exception {
        String secret = secretOf(createLink(1, "[\"alerts\"]", ""));
        String id = resolve(secret).body().path("connectorId").asText();

        assertEquals(403, prepare(id, 1, "10.00").status());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM mcp_pending_orders", Integer.class));
    }

    @Test
    void thePerOrderAndDailyLimitsAreEnforcedByTheServer() throws Exception {
        String secret = secretOf(createLink(1, "[\"trade\"]", ",\"maxOrderValue\":1000,\"maxOrdersPerDay\":1"));
        String id = resolve(secret).body().path("connectorId").asText();

        assertEquals(422, prepare(id, 11, "100.00").status(), "1100 is over the 1000 limit");

        String pendingA = prepare(id, 1, "100.00").body().path("pendingId").asText();
        String pendingB = prepare(id, 1, "100.00").body().path("pendingId").asText();
        assertEquals(200, confirm(id, pendingA).status());
        assertEquals(422, confirm(id, pendingB).status(), "the day's one order is used");
        assertEquals(422, prepare(id, 1, "100.00").status());
        assertEquals(1, SUBMITTED.size());
    }

    @Test
    void anExpiredPreparedOrderCannotBeConfirmed() throws Exception {
        String secret = secretOf(createLink(1, "[\"trade\"]", ""));
        String id = resolve(secret).body().path("connectorId").asText();
        String pendingId = prepare(id, 1, "10.00").body().path("pendingId").asText();

        jdbc.update("UPDATE mcp_pending_orders SET expires_at = CURRENT_TIMESTAMP - INTERVAL '1 second'");

        assertEquals(409, confirm(id, pendingId).status());
        assertTrue(SUBMITTED.isEmpty());
        assertEquals("EXPIRED", call("GET", "/internal/mcp/connectors/" + id + "/orders/" + pendingId, null, KEY, null)
                .body().path("status").asText());
    }

    @Test
    void aPreparedOrderBelongsToItsLinkAlone() throws Exception {
        String idA = resolve(secretOf(createLink(1, "[\"trade\"]", ""))).body().path("connectorId").asText();
        String idB = resolve(secretOf(createLink(1, "[\"trade\"]", ""))).body().path("connectorId").asText();
        String pendingId = prepare(idA, 1, "10.00").body().path("pendingId").asText();

        assertEquals(404, confirm(idB, pendingId).status());
        assertTrue(SUBMITTED.isEmpty());
    }

    @Test
    void aRevokedLinkCannotPrepareOrConfirm() throws Exception {
        Reply created = createLink(1, "[\"trade\"]", "");
        String id = resolve(secretOf(created)).body().path("connectorId").asText();
        String pendingId = prepare(id, 1, "10.00").body().path("pendingId").asText();

        call("DELETE", "/api/v1/accounts/1/mcp/connectors/" + id, 1L, null, null);

        assertEquals(401, confirm(id, pendingId).status());
        assertEquals(401, prepare(id, 1, "10.00").status());
        assertTrue(SUBMITTED.isEmpty());
    }

    @Test
    void aPlatformRefusalAndAnUnknownOutcomeAreRecordedDifferently() throws Exception {
        String id = resolve(secretOf(createLink(1, "[\"trade\"]", ""))).body().path("connectorId").asText();

        NEXT_RESULT.set(SubmissionResult.rejected("HTTP 422 FUND-422"));
        assertEquals("REJECTED", confirm(id, prepare(id, 1, "10.00").body().path("pendingId").asText()).body().path("status").asText());

        NEXT_RESULT.set(SubmissionResult.failed("ResourceAccessException"));
        assertEquals("FAILED", confirm(id, prepare(id, 1, "10.00").body().path("pendingId").asText()).body().path("status").asText());
    }

    // ---------------------------------------------------------------- audit

    @Test
    void everyStepIsAuditedAndTheOwnerCanReadItButNoOneElse() throws Exception {
        String secret = secretOf(createLink(1, "[\"trade\"]", ""));
        String id = resolve(secret).body().path("connectorId").asText();
        confirm(id, prepare(id, 1, "10.00").body().path("pendingId").asText());
        call("POST", "/internal/mcp/connectors/" + id + "/audit", null, KEY, "{\"tool\":\"get_balance\",\"outcome\":\"ok\"}");

        Reply audit = call("GET", "/api/v1/accounts/1/mcp/audit", 1L, null, null);
        assertEquals(200, audit.status());
        String tools = audit.body().toString();
        assertTrue(tools.contains("connector.create"));
        assertTrue(tools.contains("prepare_order"));
        assertTrue(tools.contains("confirm_order"));
        assertTrue(tools.contains("get_balance"));
        assertTrue(tools.contains("My Claude"));
        assertFalse(tools.contains(secret), "the audit trail must never carry the secret");
        assertEquals(403, call("GET", "/api/v1/accounts/1/mcp/audit", 5L, null, null).status());
    }
}
