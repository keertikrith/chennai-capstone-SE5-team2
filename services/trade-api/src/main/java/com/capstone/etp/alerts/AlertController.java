package com.capstone.etp.alerts;

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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Price alerts for one account. The watchlist itself is the account's existing watchlist
 * (GET/PUT/DELETE /api/v1/accounts/{id}/watchlist), which is not duplicated here.
 *
 * Every route compares the token's accountId claim with the account in the path. Delivery of a fired
 * alert is not a route: it is the in-process {@code AlertDelivery} interface.
 */
@RestController
@RequestMapping("/api/v1/accounts/{accountId}/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final AlertService service;

    @GetMapping
    public ResponseEntity<List<AlertResponse>> list(@PathVariable Long accountId) {
        return ResponseEntity.ok(service.list(accountId));
    }

    @PostMapping
    public ResponseEntity<AlertResponse> create(
            @PathVariable Long accountId,
            @Valid @RequestBody CreateAlertRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(accountId, request));
    }

    @DeleteMapping("/{alertId}")
    public ResponseEntity<AlertResponse> remove(
            @PathVariable Long accountId,
            @PathVariable Long alertId) {
        return ResponseEntity.ok(service.remove(accountId, alertId));
    }
}
