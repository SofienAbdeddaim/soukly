package com.soukly.user.profile;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.soukly.user.account.AccountStatus;
import com.soukly.user.account.Language;
import com.soukly.user.account.UserAccount;
import com.soukly.user.account.UserAccountRepository;
import com.soukly.user.internal.UserNotFoundException;
import com.soukly.user.mail.AccountMail;
import com.soukly.user.registration.FieldError;
import com.soukly.user.registration.InvalidRequestException;
import com.soukly.user.registration.PasswordPolicy;
import com.soukly.user.registration.RegistrationValidator;
import com.soukly.user.session.CurrentPasswordCheck;
import com.soukly.user.session.SessionRevocation;

import tools.jackson.databind.JsonNode;

/**
 * Profile of the signed-in account (AUTH-05): reading, update of the display name, phone number and email language,
 * and change of password. The new phone number is the one every listing shows from now on: listing-service reads it
 * from user-service at each reveal (CATA-03).
 */
@Service
class ProfileService {

    private static final Logger log = LoggerFactory.getLogger(ProfileService.class);

    static final String DISPLAY_NAME = "displayName";
    static final String PHONE = "phone";
    static final String PREFERRED_LANGUAGE = "preferredLanguage";
    static final String NEW_PASSWORD = "newPassword";

    private final UserAccountRepository users;
    private final RegistrationValidator validator;
    private final PasswordPolicy passwordPolicy;
    private final PasswordEncoder passwordEncoder;
    private final CurrentPasswordCheck currentPassword;
    private final SessionRevocation sessions;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    ProfileService(UserAccountRepository users, RegistrationValidator validator, PasswordPolicy passwordPolicy,
            PasswordEncoder passwordEncoder, CurrentPasswordCheck currentPassword, SessionRevocation sessions,
            ApplicationEventPublisher events, Clock clock) {
        this.users = users;
        this.validator = validator;
        this.passwordPolicy = passwordPolicy;
        this.passwordEncoder = passwordEncoder;
        this.currentPassword = currentPassword;
        this.sessions = sessions;
        this.events = events;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ProfileResponse get(UUID userId) {
        return ProfileResponse.of(active(users, userId));
    }

    /**
     * Applies a JSON Merge Patch (RFC 7396) of {@code displayName}, {@code phone} and {@code preferredLanguage}, with
     * the rules of the registration. A member left out keeps its value; {@code null} is refused, every field being
     * required. Other members are ignored: the email has its own confirmed change.
     *
     * @throws InvalidRequestException with every invalid field
     */
    @Transactional
    public ProfileResponse update(UUID userId, JsonNode patch) {
        if (patch == null || !patch.isObject()) {
            throw new InvalidRequestException(List.of(new FieldError("body", "body.invalid")));
        }
        UserAccount account = active(users, userId);
        List<FieldError> errors = new ArrayList<>();
        String displayName = account.displayName();
        JsonNode name = patch.get(DISPLAY_NAME);
        if (name != null) {
            if (isStringOrNull(name)) {
                displayName = validator.displayName(stringValue(name), errors);
            } else {
                errors.add(new FieldError(DISPLAY_NAME, "displayName.invalid"));
            }
        }
        String phone = account.phoneE164();
        JsonNode number = patch.get(PHONE);
        if (number != null) {
            if (isStringOrNull(number)) {
                phone = validator.phone(stringValue(number), errors).orElse(phone);
            } else {
                errors.add(new FieldError(PHONE, "phone.invalid"));
            }
        }
        Language language = account.preferredLanguage();
        if (patch.has(PREFERRED_LANGUAGE)) {
            language = language(patch.get(PREFERRED_LANGUAGE), errors).orElse(language);
        }
        if (!errors.isEmpty()) {
            throw new InvalidRequestException(errors);
        }
        account.updateProfile(displayName, phone, language, clock.instant());
        log.info("Profile of user {} updated", userId);
        return ProfileResponse.of(account);
    }

    /**
     * Changes the password: checks the current one (with the login throttling), applies the rules of a new password,
     * signs out every other device and sends the confirmation email of AUTH-04. The device that asked keeps its
     * session.
     *
     * @param currentRefreshToken value of the refresh cookie of the device that asked, {@code null} when missing
     */
    @Transactional
    public void changePassword(UUID userId, String current, String newPassword, String currentRefreshToken) {
        UserAccount account = active(users, userId);
        currentPassword.verify(account, current);
        passwordPolicy.violation(newPassword, account.email()).ifPresent(code -> {
            throw new InvalidRequestException(List.of(new FieldError(NEW_PASSWORD, code)));
        });
        Instant now = clock.instant();
        account.changePassword(passwordEncoder.encode(newPassword), now);
        int revoked = sessions.revokeOthers(userId, currentRefreshToken);
        events.publishEvent(new AccountMail.PasswordChanged(account.id(), account.email(), account.displayName(),
                account.preferredLanguage(), now));
        log.info("Password of user {} changed, {} session token(s) of other devices revoked", userId, revoked);
    }

    /** The account of an access token, if it is still active. */
    static UserAccount active(UserAccountRepository users, UUID userId) {
        return users.findById(userId).filter(account -> account.status() == AccountStatus.ACTIVE)
                .orElseThrow(UserNotFoundException::new);
    }

    private static boolean isStringOrNull(JsonNode value) {
        return value.isNull() || value.isString();
    }

    private static String stringValue(JsonNode value) {
        return value.isNull() ? null : value.asString();
    }

    private static Optional<Language> language(JsonNode value, List<FieldError> errors) {
        if (value == null || value.isNull()) {
            errors.add(new FieldError(PREFERRED_LANGUAGE, PREFERRED_LANGUAGE + ".required"));
            return Optional.empty();
        }
        try {
            return Optional.of(Language.fromCode(value.isString() ? value.asString() : ""));
        } catch (IllegalArgumentException unsupported) {
            errors.add(new FieldError(PREFERRED_LANGUAGE, PREFERRED_LANGUAGE + ".invalid"));
            return Optional.empty();
        }
    }
}
