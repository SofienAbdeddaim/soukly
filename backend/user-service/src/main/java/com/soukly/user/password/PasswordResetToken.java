package com.soukly.user.password;

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
 * Single-use token of a password reset link. Only the SHA-256 of the token is stored.
 */
@Entity
@Table(name = "password_reset_tokens")
class PasswordResetToken implements Persistable<UUID> {

    @Id
    private UUID id;

    private UUID userId;

    private String tokenHash;

    private Instant expiresAt;

    private Instant usedAt;

    /** Set when a newer token of the same account replaces this one, or when another token is used. */
    private Instant invalidatedAt;

    private Instant createdAt;

    /** The id is assigned in code: this flag lets {@code save} insert without a select first. */
    @Transient
    private boolean isNew = true;

    protected PasswordResetToken() {
    }

    PasswordResetToken(UUID userId, String tokenHash, Instant now, Instant expiresAt) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.createdAt = now;
        this.expiresAt = expiresAt;
    }

    UUID id() {
        return id;
    }

    UUID userId() {
        return userId;
    }

    boolean isUsable(Instant now) {
        return usedAt == null && invalidatedAt == null && now.isBefore(expiresAt);
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
}
