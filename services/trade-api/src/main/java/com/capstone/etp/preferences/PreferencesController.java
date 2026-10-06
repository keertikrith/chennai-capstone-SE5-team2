package com.capstone.etp.preferences;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The customer's own preferences. Both routes sit under {@code /api/v1/}, so the bearer token has
 * been verified before they run; what the service decides here is whether THIS caller may touch
 * THIS account, from the token's accountId claim, on every call.
 *
 * Channel resolution for notifications is deliberately not here: it is the in-process
 * {@link ChannelResolver} interface, not a route.
 */
@RestController
@RequestMapping("/api/v1/accounts/{accountId}/preferences")
@RequiredArgsConstructor
public class PreferencesController {

    private final PreferencesService service;

    @GetMapping
    public ResponseEntity<PreferencesResponse> get(@PathVariable Long accountId) {
        return ResponseEntity.ok(service.get(accountId));
    }

    @PutMapping
    public ResponseEntity<PreferencesResponse> put(
            @PathVariable Long accountId,
            @Valid @RequestBody UpdatePreferencesRequest request) {
        return ResponseEntity.ok(service.update(accountId, request));
    }
}
