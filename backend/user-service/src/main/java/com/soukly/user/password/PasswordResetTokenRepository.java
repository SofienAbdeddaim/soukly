package com.soukly.user.password;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    /** Requests of the account since {@code since}, for the hourly limit. */
    long countByUserIdAndCreatedAtAfter(UUID userId, Instant since);

    /** Invalidates the unused tokens of an account, except {@code keptId} (may be {@code null}). */
    @Modifying
    @Query("""
            update PasswordResetToken t set t.invalidatedAt = :now
            where t.userId = :userId and t.usedAt is null and t.invalidatedAt is null
                and (:keptId is null or t.id <> :keptId)""")
    int invalidateUnused(UUID userId, UUID keptId, Instant now);

    /** Marks a token as used if it still is usable; returns 0 when another request used it first. */
    @Modifying
    @Query("""
            update PasswordResetToken t set t.usedAt = :now
            where t.id = :id and t.usedAt is null and t.invalidatedAt is null and t.expiresAt > :now""")
    int markUsed(UUID id, Instant now);
}
