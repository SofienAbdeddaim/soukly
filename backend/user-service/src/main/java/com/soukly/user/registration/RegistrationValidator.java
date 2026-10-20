package com.soukly.user.registration;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.soukly.user.account.Language;

/**
 * Checks and normalizes the registration fields. Each invalid field gets exactly one stable code, the first rule
 * it breaks.
 */
@Component
class RegistrationValidator {

    static final int PASSWORD_MIN = 8;
    static final int PASSWORD_MAX = 128;
    static final int DISPLAY_NAME_MIN = 2;
    static final int DISPLAY_NAME_MAX = 50;

    private final CommonPasswords commonPasswords;

    RegistrationValidator(CommonPasswords commonPasswords) {
        this.commonPasswords = commonPasswords;
    }

    /**
     * @throws InvalidRequestException with every invalid field
     */
    Registration validate(RegisterRequest request, Language language) {
        List<FieldError> errors = new ArrayList<>();

        String email = EmailAddresses.normalize(request.email());
        if (email == null || email.isEmpty()) {
            errors.add(new FieldError("email", "email.required"));
        } else if (!EmailAddresses.isValid(email)) {
            errors.add(new FieldError("email", "email.invalid"));
        }

        String password = request.password();
        if (password == null || password.isEmpty()) {
            errors.add(new FieldError("password", "password.required"));
        } else if (outside(password.codePointCount(0, password.length()), PASSWORD_MIN, PASSWORD_MAX)) {
            errors.add(new FieldError("password", "password.length"));
        } else if (commonPasswords.contains(password)) {
            errors.add(new FieldError("password", "password.too-common"));
        } else if (email != null && password.strip().equalsIgnoreCase(email)) {
            errors.add(new FieldError("password", "password.same-as-email"));
        }

        String displayName = request.displayName() == null ? null : request.displayName().strip();
        if (displayName == null || displayName.isEmpty()) {
            errors.add(new FieldError("displayName", "displayName.required"));
        } else if (outside(displayName.codePointCount(0, displayName.length()), DISPLAY_NAME_MIN,
                DISPLAY_NAME_MAX)) {
            errors.add(new FieldError("displayName", "displayName.length"));
        }

        Optional<String> phone = Optional.empty();
        if (request.phone() == null || request.phone().isBlank()) {
            errors.add(new FieldError("phone", "phone.required"));
        } else {
            phone = PhoneNumbers.toE164(request.phone());
            if (phone.isEmpty()) {
                errors.add(new FieldError("phone", "phone.invalid"));
            }
        }

        if (!Boolean.TRUE.equals(request.acceptTerms())) {
            errors.add(new FieldError("acceptTerms", "terms.required"));
        }

        if (!errors.isEmpty()) {
            throw new InvalidRequestException(errors);
        }
        return new Registration(email, password, displayName, phone.orElseThrow(), language);
    }

    /**
     * @return the normalized email
     * @throws InvalidRequestException when the email is missing or malformed
     */
    String validateEmail(String rawEmail) {
        String email = EmailAddresses.normalize(rawEmail);
        if (email == null || email.isEmpty()) {
            throw new InvalidRequestException(List.of(new FieldError("email", "email.required")));
        }
        if (!EmailAddresses.isValid(email)) {
            throw new InvalidRequestException(List.of(new FieldError("email", "email.invalid")));
        }
        return email;
    }

    private static boolean outside(int length, int min, int max) {
        return length < min || length > max;
    }
}
