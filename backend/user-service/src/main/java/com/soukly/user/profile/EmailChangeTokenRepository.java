package com.soukly.user.profile;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface EmailChangeTokenRepository extends JpaRepository<EmailChangeToken, UUID> {

    Optional<EmailChangeToken> findByTokenHash(String tokenHash);

    /** Requests of the account since {@code since}, for the hourly limit. */
    long countByUserIdAndCreatedAtAfter(UUID userId, Instant since);

    /** Invalidates the unused tokens of an account. */
    @Modifying
    @Query("""
            update EmailChangeToken t set t.invalidatedAt = :now
            where t.userId = :userId and t.usedAt is null and t.invalidatedAt is null""")
    int invalidateUnused(UUID userId, Instant now);

    /** Marks a token as used if it still is usable; returns 0 when another request used it first. */
    @Modifying
    @Query("""
            update EmailChangeToken t set t.usedAt = :now
            where t.id = :id and t.usedAt is null and t.invalidatedAt is null and t.expiresAt > :now""")
    int markUsed(UUID id, Instant now);
}
