package com.soukly.user.password;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.soukly.user.registration.RegistrationValidator;

/**
 * Request of a reset link (AUTH-04), exposed by the gateway as {@code POST /api/users/password/forgot}. The reset
 * itself opens a session, so it lives with the session endpoints.
 */
@RestController
@RequestMapping("/users/password")
class ForgotPasswordController {

    private final PasswordResets resets;
    private final RegistrationValidator validator;

    ForgotPasswordController(PasswordResets resets, RegistrationValidator validator) {
        this.resets = resets;
        this.validator = validator;
    }

    /** {@code 202} with an empty body, whether the email has an account or not. */
    @PostMapping("/forgot")
    ResponseEntity<Void> forgot(@RequestBody ForgotPasswordRequest request) {
        resets.requestReset(validator.validateEmail(request.email()));
        return ResponseEntity.accepted().build();
    }
}
