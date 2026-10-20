package com.soukly.user.registration;

import java.util.List;

/** Answered with {@code 400} and the list of invalid fields. */
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
