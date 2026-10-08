package com.capstone.etp.mcp;

import com.capstone.etp.automation.TradeApiClient.SubmissionResult;
import com.capstone.etp.dto.PlaceOrderRequest;
import com.capstone.etp.enums.OrderSide;
import com.capstone.etp.exception.ModuleException;
import com.capstone.etp.mcp.McpModels.PendingOrderView;
import com.capstone.etp.mcp.McpModels.PrepareOrderRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;

/**
 * Orders placed through an AI assistant, in two steps. prepare checks the connector's limits and stores what was
 * asked for; nothing trades. confirm, which the customer approves in the chat, places it exactly once: the row is
 * claimed with one guarded UPDATE and the Trade API call carries the row's id as its idempotency key. Every limit
 * is enforced here, so the MCP server cannot loosen one. The order e-mail comes from the normal order events.
 */
@Service
public class McpOrderService {

    private final McpMapper mapper;
    private final McpConnectorService connectors;
    private final McpOrderGateway gateway;
    private final McpProperties properties;
    private final Clock clock;

    public McpOrderService(McpMapper mapper, McpConnectorService connectors, McpOrderGateway gateway,
                           McpProperties properties, Clock clock) {
        this.mapper = mapper;
        this.connectors = connectors;
        this.gateway = gateway;
        this.properties = properties;
        this.clock = clock;
    }

    public PendingOrderView prepare(UUID connectorId, PrepareOrderRequest request) {
        McpConnector connector = connectors.requireActive(connectorId);
        requireTrade(connector);

        OrderSide side = parseSide(request.side());
        BigDecimal price = request.price();
        if (price.scale() > 2) {
            throw invalid("The price can have at most 2 decimal places");
        }
        BigDecimal value = price.multiply(BigDecimal.valueOf(request.quantity()));
        if (value.compareTo(connector.getMaxOrderValue()) > 0) {
            refused(connector, "prepare_order", "over the per-order limit");
            throw new ModuleException(HttpStatus.UNPROCESSABLE_ENTITY, "MCP-422",
                    "This order is worth " + value.toPlainString() + ", over this link's limit of "
                            + connector.getMaxOrderValue().toPlainString() + " per order.");
        }
        if (mapper.countPlacedToday(connectorId) >= connector.getMaxOrdersPerDay()) {
            refused(connector, "prepare_order", "daily order limit reached");
            throw new ModuleException(HttpStatus.UNPROCESSABLE_ENTITY, "MCP-422",
                    "This link has reached its limit of " + connector.getMaxOrdersPerDay() + " orders for today.");
        }
        if (mapper.countWaiting(connectorId) >= properties.getMaxPendingPerConnector()) {
            throw new ModuleException(HttpStatus.TOO_MANY_REQUESTS, "MCP-429",
                    "Too many prepared orders are waiting. Confirm or let them expire first.");
        }

        UUID pendingId = UUID.randomUUID();
        var expiresAt = clock.instant().plus(Duration.ofSeconds(properties.getPendingTtlSeconds()));
        String symbol = request.symbol().trim().toUpperCase(Locale.ROOT);
        mapper.insertPending(pendingId, connectorId, connector.getAccountId(), symbol, side.name(),
                request.quantity(), price, value, expiresAt);
        connectors.audit(connectorId, connector.getAccountId(), "prepare_order", "ok",
                side + " " + request.quantity() + " " + symbol + " @ " + price.toPlainString());

        return new PendingOrderView(pendingId, "PENDING", symbol, side.name(), request.quantity(), price, value,
                expiresAt, null,
                "Nothing has been traded. Show the customer this order and ask them to confirm it. "
                        + "It can be confirmed for the next " + properties.getPendingTtlSeconds() + " seconds.");
    }

    public PendingOrderView confirm(UUID connectorId, UUID pendingId) {
        McpConnector connector = connectors.requireActive(connectorId);
        requireTrade(connector);

        McpPendingOrder pending = mapper.findPending(pendingId, connectorId);
        if (pending == null) {
            throw new ModuleException(HttpStatus.NOT_FOUND, "MCP-404", "No such prepared order");
        }
        if (!"PENDING".equals(pending.getStatus())) {
            throw new ModuleException(HttpStatus.CONFLICT, "MCP-409",
                    "This prepared order is already " + pending.getStatus().toLowerCase(Locale.ROOT) + ".");
        }
        if (mapper.countPlacedToday(connectorId) >= connector.getMaxOrdersPerDay()) {
            refused(connector, "confirm_order", "daily order limit reached");
            throw new ModuleException(HttpStatus.UNPROCESSABLE_ENTITY, "MCP-422",
                    "This link has reached its limit of " + connector.getMaxOrdersPerDay() + " orders for today.");
        }
        if (mapper.claimPending(pendingId, connectorId) == 0) {
            throw new ModuleException(HttpStatus.CONFLICT, "MCP-409",
                    "This prepared order has expired or was already used. Prepare it again.");
        }

        PlaceOrderRequest order = PlaceOrderRequest.builder()
                .accountId(pending.getAccountId())
                .symbol(pending.getSymbol())
                .side(OrderSide.valueOf(pending.getSide()))
                .quantity(pending.getQuantity())
                .price(pending.getPrice())
                .idempotencyKey(pendingId)
                .build();
        SubmissionResult result = gateway.submit(order);

        String status;
        String message;
        switch (result.status()) {
            case SUBMITTED -> {
                status = "PLACED";
                message = "The order was placed. The customer will get an e-mail when it is filled.";
            }
            case REJECTED -> {
                status = "REJECTED";
                message = "The platform refused the order: " + result.detail();
            }
            default -> {
                status = "FAILED";
                message = "The outcome is unknown (" + result.detail() + "). Check the order list before trying again.";
            }
        }
        mapper.settlePending(pendingId, status, result.orderId(), result.detail());
        connectors.audit(connectorId, connector.getAccountId(), "confirm_order", status.toLowerCase(Locale.ROOT),
                pending.getSide() + " " + pending.getQuantity() + " " + pending.getSymbol());

        return new PendingOrderView(pendingId, status, pending.getSymbol(), pending.getSide(), pending.getQuantity(),
                pending.getPrice(), pending.getEstValue(), pending.getExpiresAt(), result.orderId(), message);
    }

    public PendingOrderView status(UUID connectorId, UUID pendingId) {
        McpConnector connector = connectors.requireActive(connectorId);
        McpPendingOrder pending = mapper.findPending(pendingId, connector.getId());
        if (pending == null) {
            throw new ModuleException(HttpStatus.NOT_FOUND, "MCP-404", "No such prepared order");
        }
        String status = pending.getStatus();
        if ("PENDING".equals(status) && !pending.getExpiresAt().isAfter(clock.instant())) {
            status = "EXPIRED";
        }
        return new PendingOrderView(pending.getId(), status, pending.getSymbol(), pending.getSide(),
                pending.getQuantity(), pending.getPrice(), pending.getEstValue(), pending.getExpiresAt(),
                pending.getOrderId(), null);
    }

    public McpOrderGateway.CancelResult cancel(UUID connectorId, UUID orderId) {
        McpConnector connector = connectors.requireActive(connectorId);
        requireTrade(connector);
        McpOrderGateway.CancelResult result = gateway.cancel(connector.getAccountId(), orderId);
        connectors.audit(connectorId, connector.getAccountId(), "cancel_order",
                result.cancelled() ? "ok" : "refused", result.detail());
        return result;
    }

    private void requireTrade(McpConnector connector) {
        if (!connector.has(McpScope.TRADE)) {
            throw new ModuleException(HttpStatus.FORBIDDEN, "MCP-403",
                    "This link was not given permission to trade.");
        }
    }

    private void refused(McpConnector connector, String tool, String reason) {
        connectors.audit(connector.getId(), connector.getAccountId(), tool, "refused", reason);
    }

    private static OrderSide parseSide(String side) {
        try {
            return OrderSide.valueOf(side.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw invalid("Side must be BUY or SELL");
        }
    }

    private static ModuleException invalid(String message) {
        return new ModuleException(HttpStatus.UNPROCESSABLE_ENTITY, "VAL-422", message);
    }
}
