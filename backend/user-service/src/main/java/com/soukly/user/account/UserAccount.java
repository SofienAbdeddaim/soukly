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

    private Instant emailVerifiedAt;

    /** {@code null} until the first successful login. */
    private Instant lastLoginAt;

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
            Language language, Instant now) {
        UserAccount account = new UserAccount();
        account.id = UUID.randomUUID();
        account.email = email;
        account.status = AccountStatus.PENDING_VERIFICATION;
        account.createdAt = now;
        account.updateRegistration(passwordHash, displayName, phoneE164, language, now);
        return account;
    }

    /**
     * Replaces the registration data of a pending account with a newer registration for the same email. Only the
     * owner of the mailbox can then activate the account, with the data they entered last.
     */
    public void updateRegistration(String passwordHash, String displayName, String phoneE164, Language language,
            Instant now) {
        if (status != AccountStatus.PENDING_VERIFICATION) {
            throw new IllegalStateException("Only a pending account can be registered again");
        }
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.phoneE164 = phoneE164;
        this.preferredLanguage = language;
        this.termsAcceptedAt = now;
        this.updatedAt = now;
    }

    public void activate(Instant now) {
        if (status == AccountStatus.PENDING_VERIFICATION) {
            status = AccountStatus.ACTIVE;
            emailVerifiedAt = now;
            updatedAt = now;
        }
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
