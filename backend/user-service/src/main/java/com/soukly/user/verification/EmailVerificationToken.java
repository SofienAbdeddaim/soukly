package com.soukly.user.verification;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Persistable;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

/**
 * Single-use token of a verification link. Only the SHA-256 of the token is stored.
 */
@Entity
@Table(name = "email_verification_tokens")
public class EmailVerificationToken implements Persistable<UUID> {

    @Id
    private UUID id;

    private UUID userId;

    private String tokenHash;

    @Enumerated(EnumType.STRING)
    private TokenReason reason;

    private Instant expiresAt;

    private Instant usedAt;

    private Instant createdAt;

    /** The id is assigned in code: this flag lets {@code save} insert without a select first. */
    @Transient
    private boolean isNew = true;

    protected EmailVerificationToken() {
    }

    EmailVerificationToken(UUID userId, String tokenHash, TokenReason reason, Instant now, Instant expiresAt) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.reason = reason;
        this.createdAt = now;
        this.expiresAt = expiresAt;
    }

    public UUID id() {
        return id;
    }

    public UUID userId() {
        return userId;
    }

    public boolean isUsable(Instant now) {
        return usedAt == null && now.isBefore(expiresAt);
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
