package com.capstone.etp.mcp;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** An order the assistant prepared. It is placed only when the customer confirms it in the chat. */
@Getter
@Setter
@NoArgsConstructor
public class McpPendingOrder {

    private UUID id;
    private UUID connectorId;
    private Long accountId;
    private String symbol;
    private String side;
    private Integer quantity;
    private BigDecimal price;
    private BigDecimal estValue;
    private String status;
    private UUID orderId;
    private String detail;
    private Instant createdAt;
    private Instant expiresAt;
    private Instant decidedAt;
}
