package com.soukly.user.session;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    /** Locks the row: two refreshes with the same token are handled one after the other. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /** Revokes every token of a family that is not revoked yet. */
    @Modifying
    @Query("update RefreshToken t set t.revokedAt = :now where t.familyId = :familyId and t.revokedAt is null")
    int revokeFamily(UUID familyId, Instant now);

    /** Revokes every session of an account (password reset: every device is signed out). */
    @Modifying
    @Query("update RefreshToken t set t.revokedAt = :now where t.userId = :userId and t.revokedAt is null")
    int revokeAllOfUser(UUID userId, Instant now);

    /** Revokes every session of an account but one family (change of password: the device that asked stays in). */
    @Modifying
    @Query("""
            update RefreshToken t set t.revokedAt = :now
            where t.userId = :userId and t.familyId <> :familyId and t.revokedAt is null""")
    int revokeAllOfUserBut(UUID userId, UUID familyId, Instant now);

    /** Deletes the tokens expired or revoked before {@code before}. */
    @Modifying
    @Query("delete from RefreshToken t where t.expiresAt < :before or t.revokedAt < :before")
    int deleteExpiredOrRevokedBefore(Instant before);
}
