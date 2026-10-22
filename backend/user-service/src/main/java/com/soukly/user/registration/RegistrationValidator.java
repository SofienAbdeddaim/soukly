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
public class RegistrationValidator {

    static final int DISPLAY_NAME_MIN = 2;
    static final int DISPLAY_NAME_MAX = 50;

    private final PasswordPolicy passwordPolicy;

    RegistrationValidator(PasswordPolicy passwordPolicy) {
        this.passwordPolicy = passwordPolicy;
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
        passwordPolicy.violation(password, email).ifPresent(code -> errors.add(new FieldError("password", code)));

        String displayName = displayName(request.displayName(), errors);
        Optional<String> phone = phone(request.phone(), errors);

        if (!Boolean.TRUE.equals(request.acceptTerms())) {
            errors.add(new FieldError("acceptTerms", "terms.required"));
        }

        if (!errors.isEmpty()) {
            throw new InvalidRequestException(errors);
        }
        return new Registration(email, password, displayName, phone.orElseThrow(), language);
    }

    /**
     * Checks a display name (2 to 50 characters once stripped), shared by the registration and the profile (AUTH-05).
     *
     * @return the stripped name; {@code errors} gets its error when it is invalid
     */
    public String displayName(String raw, List<FieldError> errors) {
        String displayName = raw == null ? null : raw.strip();
        if (displayName == null || displayName.isEmpty()) {
            errors.add(new FieldError("displayName", "displayName.required"));
        } else if (outside(displayName.codePointCount(0, displayName.length()), DISPLAY_NAME_MIN,
                DISPLAY_NAME_MAX)) {
            errors.add(new FieldError("displayName", "displayName.length"));
        }
        return displayName;
    }

    /**
     * Checks a Tunisian phone number, shared by the registration and the profile (AUTH-05).
     *
     * @return its E.164 form, empty when it is invalid ({@code errors} then gets its error)
     */
    public Optional<String> phone(String raw, List<FieldError> errors) {
        if (raw == null || raw.isBlank()) {
            errors.add(new FieldError("phone", "phone.required"));
            return Optional.empty();
        }
        Optional<String> phone = PhoneNumbers.toE164(raw);
        if (phone.isEmpty()) {
            errors.add(new FieldError("phone", "phone.invalid"));
        }
        return phone;
    }

    /**
     * @return the normalized email
     * @throws InvalidRequestException when the email is missing or malformed
     */
    public String validateEmail(String rawEmail) {
        return validateEmail(rawEmail, "email");
    }

    /**
     * @param field name of the field in the errors ({@code newEmail} for a change of address)
     * @return the normalized email
     * @throws InvalidRequestException when the email is missing or malformed
     */
    public String validateEmail(String rawEmail, String field) {
        String email = EmailAddresses.normalize(rawEmail);
        if (email == null || email.isEmpty()) {
            throw new InvalidRequestException(List.of(new FieldError(field, "email.required")));
        }
        if (!EmailAddresses.isValid(email)) {
            throw new InvalidRequestException(List.of(new FieldError(field, "email.invalid")));
        }
        return email;
    }

    private static boolean outside(int length, int min, int max) {
        return length < min || length > max;
    }
}
