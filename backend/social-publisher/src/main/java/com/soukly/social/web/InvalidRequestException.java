package com.soukly.social.web;

import java.util.List;

/** Answered with {@code 400 validation-failed} and the list of invalid fields, like listing-service. */
public class InvalidRequestException extends RuntimeException {

    private final transient List<FieldError> errors;

    public InvalidRequestException(List<FieldError> errors) {
        super("Invalid fields: " + errors.stream().map(FieldError::field).toList(), null, false, false);
        this.errors = List.copyOf(errors);
    }

    public List<FieldError> errors() {
        return errors;
    }
}
