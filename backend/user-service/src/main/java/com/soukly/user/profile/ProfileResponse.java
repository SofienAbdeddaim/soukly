package com.soukly.user.profile;

import java.time.Instant;
import java.util.UUID;

import com.soukly.user.account.UserAccount;

/**
 * Profile of the signed-in account ({@code GET /users/me}).
 *
 * @param pendingEmail new address waiting for its confirmation, {@code null} when none
 * @param phone        E.164, {@code +21620123456}
 */
public record ProfileResponse(UUID id, String email, String pendingEmail, String displayName, String phone,
        String preferredLanguage, Instant createdAt) {

    static ProfileResponse of(UserAccount account) {
        return new ProfileResponse(account.id(), account.email(), account.pendingEmail(), account.displayName(),
                account.phoneE164(), account.preferredLanguage().code(), account.createdAt());
    }
}
