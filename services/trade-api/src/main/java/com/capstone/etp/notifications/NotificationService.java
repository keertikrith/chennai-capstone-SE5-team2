package com.capstone.etp.notifications;

import com.capstone.etp.exception.ModuleException;
import com.capstone.etp.security.AccountAuthorizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Recording and reading notifications.
 *
 * <p>{@link #enqueue} is the single way a message enters the ledger, used by the trade-event listener
 * and by the alert delivery. It is idempotent on the event id: the unique constraint turns a second
 * delivery into a no-op. {@link #list}, {@link #unreadCount} and {@link #markRead} are the customer
 * face and authorise every call against the verified token's account.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    static final int DEFAULT_LIMIT = 50;
    static final int MAX_LIMIT = 100;

    private final NotificationMapper mapper;
    private final AccountAuthorizer authorizer;

    /** The outcome of recording a message: its id, and whether it was newly created. */
    record Enqueued(long notificationId, boolean created) {
    }

    /**
     * Records a message for delivery. Nothing is sent here: the offset (or the alert) can be
     * released as soon as this returns, because the notification is now durable and the dispatcher
     * will deliver it.
     *
     * @throws ContentGuard.UnsafeContentException if the text looks like it carries something sensitive
     */
    Enqueued enqueue(UUID eventId, long accountId, NotificationKind kind, String subject, String body) {
        ContentGuard.requireSafe(subject);
        ContentGuard.requireSafe(body);

        int inserted = mapper.insertIfAbsent(eventId, accountId, kind, subject, body);
        if (inserted == 0) {
            Long existing = mapper.findIdByEventId(eventId);
            log.info("[NOTIFY] duplicate event ignored eventId={} notificationId={}", eventId, existing);
            return new Enqueued(existing == null ? -1L : existing, false);
        }
        Long id = mapper.findIdByEventId(eventId);
        log.info("[NOTIFY] recorded eventId={} notificationId={} accountId={} kind={}", eventId, id, accountId, kind);
        return new Enqueued(id == null ? -1L : id, true);
    }

    public List<NotificationResponse> list(Long accountId, boolean unreadOnly, Integer limit) {
        authorizer.ensureOwnAccount(accountId);
        int bounded = limit == null ? DEFAULT_LIMIT : Math.max(1, Math.min(limit, MAX_LIMIT));
        return mapper.findByAccount(accountId, unreadOnly, bounded).stream()
                .map(NotificationResponse::from)
                .toList();
    }

    public int unreadCount(Long accountId) {
        authorizer.ensureOwnAccount(accountId);
        return mapper.countUnread(accountId);
    }

    public NotificationResponse markRead(Long accountId, Long notificationId) {
        authorizer.ensureOwnAccount(accountId);
        // The update is keyed on the account as well as the id, so another account's notification
        // is indistinguishable from one that does not exist.
        if (mapper.markRead(notificationId, accountId) == 0) {
            throw notFound();
        }
        Notification notification = mapper.findByIdAndAccount(notificationId, accountId);
        if (notification == null) {
            throw notFound();
        }
        return NotificationResponse.from(notification);
    }

    private static ModuleException notFound() {
        return new ModuleException(HttpStatus.NOT_FOUND, "NTF-404", "Notification not found");
    }
}
