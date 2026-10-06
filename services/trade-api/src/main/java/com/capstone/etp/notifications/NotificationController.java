package com.capstone.etp.notifications;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * The customer's notification history, shown in the Angular application as an inbox.
 *
 * Read-only apart from marking a message read. There is no route that creates or sends a
 * notification: messages enter through the {@code trade-events} listener and the in-process
 * {@link AlertDelivery} interface, so a customer's token can never be used to send anything.
 * Every route compares the token's accountId claim with the account in the path.
 */
@RestController
@RequestMapping("/api/v1/accounts/{accountId}/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService service;

    @GetMapping
    public ResponseEntity<List<NotificationResponse>> list(
            @PathVariable Long accountId,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(required = false) Integer limit) {
        return ResponseEntity.ok(service.list(accountId, unreadOnly, limit));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Integer>> unreadCount(@PathVariable Long accountId) {
        return ResponseEntity.ok(Map.of("unread", service.unreadCount(accountId)));
    }

    @PostMapping("/{notificationId}/read")
    public ResponseEntity<NotificationResponse> markRead(
            @PathVariable Long accountId,
            @PathVariable Long notificationId) {
        return ResponseEntity.ok(service.markRead(accountId, notificationId));
    }
}
