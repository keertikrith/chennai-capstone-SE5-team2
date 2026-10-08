package com.capstone.etp.mcp;

import com.capstone.etp.automation.TradeApiClient.SubmissionResult;
import com.capstone.etp.dto.PlaceOrderRequest;

import java.util.UUID;

/** How a confirmed order, or a cancellation, reaches the Trade REST API: the same door every customer order uses. */
public interface McpOrderGateway {

    /** One attempt, never retried here; the order's idempotency key makes a repeat harmless. */
    SubmissionResult submit(PlaceOrderRequest request);

    /** The status the Trade API reports after cancelling, or a short reason it refused. */
    CancelResult cancel(Long accountId, UUID orderId);

    record CancelResult(boolean cancelled, String detail) {
    }
}
