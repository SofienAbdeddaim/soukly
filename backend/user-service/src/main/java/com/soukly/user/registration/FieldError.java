package com.soukly.user.registration;

/**
 * Invalid field of a request. {@code code} is a stable key that the front end translates, never a sentence.
 */
public record FieldError(String field, String code) {
}
