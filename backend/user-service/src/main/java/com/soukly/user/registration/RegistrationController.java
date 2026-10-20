package com.soukly.user.registration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.soukly.user.account.Language;

/**
 * Public registration endpoints, exposed by the gateway under {@code /api/users/**}.
 */
@RestController
@RequestMapping("/users")
class RegistrationController {

    private static final Logger log = LoggerFactory.getLogger(RegistrationController.class);

    private final RegistrationService registrations;
    private final RegistrationValidator validator;

    RegistrationController(RegistrationService registrations, RegistrationValidator validator) {
        this.registrations = registrations;
        this.validator = validator;
    }

    /** {@code 202} with an empty body, whether the email is new or already used. */
    @PostMapping("/register")
    ResponseEntity<Void> register(@RequestBody RegisterRequest request,
            @RequestHeader(name = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        Registration registration = validator.validate(request, Language.fromAcceptLanguage(acceptLanguage));
        try {
            registrations.register(registration);
        } catch (DataIntegrityViolationException e) {
            // Two registrations of the same new email at the same time: the second one is answered like any
            // registration of a known email.
            log.info("Concurrent registration of the same email ignored");
        }
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/verify-email")
    ResponseEntity<Void> verifyEmail(@RequestBody VerifyEmailRequest request) {
        registrations.verifyEmail(request.token());
        return ResponseEntity.noContent().build();
    }

    /** {@code 202} with an empty body, whatever the state of the account. */
    @PostMapping("/verify-email/resend")
    ResponseEntity<Void> resendVerification(@RequestBody ResendVerificationRequest request) {
        registrations.resendVerification(validator.validateEmail(request.email()));
        return ResponseEntity.accepted().build();
    }
}
