package com.soukly.user.verification;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, UUID> {

    Optional<EmailVerificationToken> findByTokenHash(String tokenHash);

    long countByUserIdAndReasonAndCreatedAtAfter(UUID userId, TokenReason reason, Instant since);

    /** Makes the usable tokens of an account expire now (an older link stops working). */
    @Modifying
    @Query("""
            update EmailVerificationToken t set t.expiresAt = :now
            where t.userId = :userId and t.usedAt is null and t.expiresAt > :now""")
    int expireUsableTokens(UUID userId, Instant now);

    /** Marks a token as used if it still is usable; returns 0 when another request used it first. */
    @Modifying
    @Query("""
            update EmailVerificationToken t set t.usedAt = :now
            where t.id = :id and t.usedAt is null and t.expiresAt > :now""")
    int markUsed(UUID id, Instant now);
}
