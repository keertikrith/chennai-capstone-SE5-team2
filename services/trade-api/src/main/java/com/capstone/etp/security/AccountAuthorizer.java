package com.capstone.etp.security;

import com.capstone.etp.exception.AccountAccessDeniedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Slf4j
@Component
public class AccountAuthorizer {

    public void ensureAccountAccess(Long accountId) {
        AuthenticatedUser user = getAuthenticatedUser();
        if (isAdmin(user)) {
            return;
        }

        List<Long> accountIds = user.accountIds();
        if (accountIds == null || !accountIds.contains(accountId)) {
            logDenied(user, accountId);
            throw new AccountAccessDeniedException();
        }
    }

    /**
     * For routes that return one customer's own data (preferences, notifications, alerts, portfolio).
     * Unlike {@link #ensureAccountAccess} an ADMIN gets no exception: the {@code accountId} claim of the
     * verified token must contain the account in the path, whoever the caller is. A mismatch is
     * logged and refused as ACC-403, never answered as "not found" or an empty list.
     */
    public void ensureOwnAccount(Long accountId) {
        AuthenticatedUser user = getAuthenticatedUser();
        List<Long> accountIds = user.accountIds();
        if (accountId == null || accountIds == null || !accountIds.contains(accountId)) {
            logDenied(user, accountId);
            throw new AccountAccessDeniedException();
        }
    }

    private void logDenied(AuthenticatedUser user, Long requestedAccountId) {
        // The subject is the token's own identifier; nothing the caller typed is written to the log.
        log.warn("[ACCESS_DENIED] subject={} requestedAccountId={} tokenAccountIds={}",
                user.subject(), requestedAccountId, user.accountIds());
    }

    /** For endpoints that only an admin may call. */
    public void ensureAdmin() {
        if (!isAdmin(getAuthenticatedUser())) {
            throw new AccountAccessDeniedException("Admin access required");
        }
    }

    public void ensureOrderManagementAllowed() {
        AuthenticatedUser user = getAuthenticatedUser();
        if (isAdmin(user)) {
            throw new AccountAccessDeniedException(
                    "Admin users can only access read endpoints");
        }
    }

    private AuthenticatedUser getAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            throw new UnauthorizedException("Unauthorised");
        }
        return user;
    }

    private boolean isAdmin(AuthenticatedUser user) {
        if (user.roles() == null) {
            return false;
        }
        return user.roles().stream()
                .filter(role -> role != null && !role.isBlank())
                .map(role -> role.trim().toUpperCase(Locale.ROOT))
                .map(role -> role.startsWith("ROLE_") ? role.substring(5) : role)
                .anyMatch("ADMIN"::equals);
    }
}
