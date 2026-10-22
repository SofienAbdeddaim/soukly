package com.soukly.user.profile;

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
 * Single-use token of the link that confirms a new address (AUTH-05). Only the SHA-256 of the token is stored.
 */
@Entity
@Table(name = "email_change_tokens")
class EmailChangeToken implements Persistable<UUID> {

    @Id
    private UUID id;

    private UUID userId;

    private String tokenHash;

    /** Normalized address that the link confirms. */
    private String newEmail;

    private Instant expiresAt;

    private Instant usedAt;

    /** Set when a newer request replaces this one, when the request is cancelled or the account deleted. */
    private Instant invalidatedAt;

    private Instant createdAt;

    /** The id is assigned in code: this flag lets {@code save} insert without a select first. */
    @Transient
    private boolean isNew = true;

    protected EmailChangeToken() {
    }

    EmailChangeToken(UUID userId, String tokenHash, String newEmail, Instant now, Instant expiresAt) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.newEmail = newEmail;
        this.createdAt = now;
        this.expiresAt = expiresAt;
    }

    UUID id() {
        return id;
    }

    UUID userId() {
        return userId;
    }

    String newEmail() {
        return newEmail;
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
