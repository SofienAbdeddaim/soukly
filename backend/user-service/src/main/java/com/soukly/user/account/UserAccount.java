package com.soukly.user.account;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Persistable;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

@Entity
@Table(name = "users")
public class UserAccount implements Persistable<UUID> {

    @Id
    private UUID id;

    private String email;

    private String passwordHash;

    private String displayName;

    @Column(name = "phone_e164")
    private String phoneE164;

    @Convert(converter = LanguageConverter.class)
    private Language preferredLanguage;

    @Enumerated(EnumType.STRING)
    private AccountStatus status;

    private Instant termsAcceptedAt;

    /** Version of the terms of use accepted at registration (LEG-01); {@code null} for older accounts. */
    private String termsVersion;

    private Instant emailVerifiedAt;

    /** {@code null} until the first successful login. */
    private Instant lastLoginAt;

    /** New address waiting for its confirmation (AUTH-05); the login still uses {@link #email} until then. */
    private String pendingEmail;

    private Instant deletedAt;

    /** Address of a deleted account, kept until the purge; {@link #email} is then {@code null}. */
    private String deletedEmail;

    private Instant createdAt;

    private Instant updatedAt;

    /** The id is assigned in code: this flag lets {@code save} insert without a select first. */
    @Transient
    private boolean isNew = true;

    protected UserAccount() {
    }

    /**
     * Creates an account waiting for the verification of its email.
     */
    public static UserAccount pending(String email, String passwordHash, String displayName, String phoneE164,
            Language language, String termsVersion, Instant now) {
        UserAccount account = new UserAccount();
        account.id = UUID.randomUUID();
        account.email = email;
        account.status = AccountStatus.PENDING_VERIFICATION;
        account.createdAt = now;
        account.updateRegistration(passwordHash, displayName, phoneE164, language, termsVersion, now);
        return account;
    }

    /**
     * Replaces the registration data of a pending account with a newer registration for the same email. Only the
     * owner of the mailbox can then activate the account, with the data they entered last.
     */
    public void updateRegistration(String passwordHash, String displayName, String phoneE164, Language language,
            String termsVersion, Instant now) {
        if (status != AccountStatus.PENDING_VERIFICATION) {
            throw new IllegalStateException("Only a pending account can be registered again");
        }
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.phoneE164 = phoneE164;
        this.preferredLanguage = language;
        this.termsAcceptedAt = now;
        this.termsVersion = termsVersion;
        this.updatedAt = now;
    }

    public void activate(Instant now) {
        if (status == AccountStatus.PENDING_VERIFICATION) {
            status = AccountStatus.ACTIVE;
            emailVerifiedAt = now;
            updatedAt = now;
        }
    }

    /** Replaces the profile fields of an active account (AUTH-05); the values are validated and normalized. */
    public void updateProfile(String displayName, String phoneE164, Language language, Instant now) {
        this.displayName = displayName;
        this.phoneE164 = phoneE164;
        this.preferredLanguage = language;
        this.updatedAt = now;
    }

    /** Shows {@code newEmail} as waiting for its confirmation; a newer request replaces it. */
    public void requestEmailChange(String newEmail, Instant now) {
        this.pendingEmail = newEmail;
        this.updatedAt = now;
    }

    public void cancelEmailChange(Instant now) {
        this.pendingEmail = null;
        this.updatedAt = now;
    }

    /** The new address was confirmed by its link: the login uses it from now on. */
    public void changeEmail(String newEmail, Instant now) {
        this.email = newEmail;
        this.pendingEmail = null;
        this.updatedAt = now;
    }

    /**
     * Deletes the account (AUTH-05): it can no longer sign in, and its address is free for a new registration at
     * once. The address is kept in {@code deleted_email} until the purge.
     */
    public void delete(Instant now) {
        this.deletedEmail = email;
        this.email = null;
        this.pendingEmail = null;
        this.status = AccountStatus.DELETED;
        this.deletedAt = now;
        this.updatedAt = now;
    }

    /** Replaces the password hash (AUTH-04 reset, AUTH-05 change). */
    public void changePassword(String passwordHash, Instant now) {
        this.passwordHash = passwordHash;
        this.updatedAt = now;
    }

    /**
     * Records a successful login.
     *
     * @return {@code true} for the first login of the account
     */
    public boolean recordLogin(Instant now) {
        boolean first = lastLoginAt == null;
        lastLoginAt = now;
        return first;
    }

    public UUID id() {
        return id;
    }

    public String email() {
        return email;
    }

    public String pendingEmail() {
        return pendingEmail;
    }

    public String passwordHash() {
        return passwordHash;
    }

    public String displayName() {
        return displayName;
    }

    public String phoneE164() {
        return phoneE164;
    }

    public Language preferredLanguage() {
        return preferredLanguage;
    }

    public AccountStatus status() {
        return status;
    }

    public Instant termsAcceptedAt() {
        return termsAcceptedAt;
    }

    public String termsVersion() {
        return termsVersion;
    }

    public Instant emailVerifiedAt() {
        return emailVerifiedAt;
    }

    public Instant createdAt() {
        return createdAt;
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
