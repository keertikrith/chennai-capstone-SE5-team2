package com.capstone.etp.mcp;

import com.capstone.etp.automation.RestTradeApiClient;
import com.capstone.etp.automation.TradeApiClient;
import com.capstone.etp.automation.TradeApiClient.SubmissionResult;
import com.capstone.etp.dto.ErrorResponse;
import com.capstone.etp.dto.OrderResponse;
import com.capstone.etp.dto.PlaceOrderRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.UUID;

/** POST and DELETE on /api/v1/orders with a one-minute token for the connector's own account. */
public class RestMcpOrderGateway implements McpOrderGateway {

    private final RestClient restClient;
    private final McpTokenIssuer tokens;
    private final TradeApiClient submitter;

    public RestMcpOrderGateway(RestClient restClient, McpTokenIssuer tokens) {
        this.restClient = restClient;
        this.tokens = tokens;
        this.submitter = new RestTradeApiClient(restClient, tokens::tokenFor);
    }

    @Override
    public SubmissionResult submit(PlaceOrderRequest request) {
        return submitter.submit(request);
    }

    @Override
    public CancelResult cancel(Long accountId, UUID orderId) {
        try {
            OrderResponse response = restClient.delete()
                    .uri("/api/v1/orders/{id}", orderId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.tokenFor(accountId))
                    .retrieve()
                    .body(OrderResponse.class);
            return new CancelResult(true, response == null ? "cancelled" : "status " + response.getStatus());
        } catch (RestClientResponseException e) {
            String code = "";
            try {
                ErrorResponse body = e.getResponseBodyAs(ErrorResponse.class);
                code = body == null || body.getErrorCode() == null ? "" : " " + body.getErrorCode();
            } catch (RuntimeException ignored) {
                // The status alone is enough.
            }
            return new CancelResult(false, "HTTP " + e.getStatusCode().value() + code);
        } catch (RestClientException e) {
            return new CancelResult(false, "the Trade API could not be reached");
        }
    }
}
