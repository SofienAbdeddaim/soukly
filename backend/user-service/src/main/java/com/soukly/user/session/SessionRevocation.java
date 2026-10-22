package com.soukly.user.session;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.soukly.user.token.OpaqueTokens;

/**
 * Signs devices out from the profile actions (AUTH-05). The access tokens already issued stay valid until they expire
 * (15 min at most), as after a logout.
 */
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class SessionRevocation {

    private final RefreshTokenRepository refreshTokens;
    private final Clock clock;

    SessionRevocation(RefreshTokenRepository refreshTokens, Clock clock) {
        this.refreshTokens = refreshTokens;
        this.clock = clock;
    }

    /**
     * Revokes every session family of the account but the one of {@code currentRefreshToken} (the device that asked),
     * or every family when that token is missing or not a live token of this account.
     *
     * @return the tokens revoked
     */
    public int revokeOthers(UUID userId, String currentRefreshToken) {
        Instant now = clock.instant();
        Optional<UUID> current = OpaqueTokens.isPlausible(currentRefreshToken)
                ? refreshTokens.findByTokenHash(OpaqueTokens.sha256Hex(currentRefreshToken))
                        .filter(token -> token.userId().equals(userId) && !token.isRevoked())
                        .map(RefreshToken::familyId)
                : Optional.empty();
        return current.map(family -> refreshTokens.revokeAllOfUserBut(userId, family, now))
                .orElseGet(() -> refreshTokens.revokeAllOfUser(userId, now));
    }

    /** Revokes every session family of the account. */
    public int revokeAll(UUID userId) {
        return refreshTokens.revokeAllOfUser(userId, clock.instant());
    }
}
