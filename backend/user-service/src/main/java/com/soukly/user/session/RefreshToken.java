package com.soukly.user.session;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Persistable;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

/**
 * Refresh token of a session. A family groups the successive tokens of one session on one device; each token is
 * used once. Only the SHA-256 of the token is stored.
 */
@Entity
@Table(name = "refresh_tokens")
class RefreshToken implements Persistable<UUID> {

    static final int USER_AGENT_MAX_LENGTH = 255;

    @Id
    private UUID id;

    private UUID userId;

    private UUID familyId;

    private String tokenHash;

    private Instant expiresAt;

    private Instant usedAt;

    private Instant revokedAt;

    private Instant createdAt;

    private String userAgent;

    /** The id is assigned in code: this flag lets {@code save} insert without a select first. */
    @Transient
    private boolean isNew = true;

    protected RefreshToken() {
    }

    RefreshToken(UUID userId, UUID familyId, String tokenHash, Instant now, Instant expiresAt, String userAgent) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.familyId = familyId;
        this.tokenHash = tokenHash;
        this.createdAt = now;
        this.expiresAt = expiresAt;
        this.userAgent = truncate(userAgent);
    }

    UUID userId() {
        return userId;
    }

    UUID familyId() {
        return familyId;
    }

    boolean isRevoked() {
        return revokedAt != null;
    }

    /** Already replaced by a rotation. */
    boolean isUsed() {
        return usedAt != null;
    }

    boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    void markUsed(Instant now) {
        usedAt = now;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        isNew = false;
    }

    private static String truncate(String userAgent) {
        if (userAgent == null || userAgent.length() <= USER_AGENT_MAX_LENGTH) {
            return userAgent;
        }
        int end = USER_AGENT_MAX_LENGTH;
        // Never cut a surrogate pair in two.
        if (Character.isHighSurrogate(userAgent.charAt(end - 1))) {
            end--;
        }
        return userAgent.substring(0, end);
    }
}
