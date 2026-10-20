package com.soukly.user.verification;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.soukly.user.config.UserProperties;
import com.soukly.user.token.OpaqueTokens;

/**
 * Issues and consumes the tokens of the verification links: 32 random bytes in Base64 URL, valid 24 h, single
 * use. Only their SHA-256 reaches the database.
 */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class EmailVerificationTokens {

    private final EmailVerificationTokenRepository tokens;
    private final UserProperties properties;
    private final Clock clock;

    EmailVerificationTokens(EmailVerificationTokenRepository tokens, UserProperties properties, Clock clock) {
        this.tokens = tokens;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Invalidates the previous tokens of the account and issues a new one.
     *
     * @return the token to put in the link; it is never stored
     */
    public String issue(UUID userId, TokenReason reason) {
        Instant now = clock.instant();
        tokens.expireUsableTokens(userId, now);
        String token = OpaqueTokens.generate();
        tokens.save(new EmailVerificationToken(userId, hash(token), reason, now,
                now.plus(properties.verificationTokenTtl())));
        return token;
    }

    /** Whether the account may receive another verification email within the hourly resend limit. */
    public boolean canResend(UUID userId) {
        Instant oneHourAgo = clock.instant().minusSeconds(3600);
        return tokens.countByUserIdAndReasonAndCreatedAtAfter(userId, TokenReason.RESEND, oneHourAgo)
                < properties.verificationResendsPerHour();
    }

    /**
     * Uses a token once.
     *
     * @return the account of the token, empty when the token is unknown, expired or already used
     */
    public Optional<UUID> consume(String token) {
        if (!OpaqueTokens.isPlausible(token)) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        return tokens.findByTokenHash(hash(token))
                .filter(found -> found.isUsable(now))
                .filter(found -> tokens.markUsed(found.id(), now) == 1)
                .map(EmailVerificationToken::userId);
    }

    private static String hash(String token) {
        return OpaqueTokens.sha256Hex(token);
    }
}
