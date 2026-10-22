package com.soukly.social.web;

/**
 * Invalid field of a request, for example {@code language}. {@code code} is a stable key the front end may translate,
 * never a sentence.
 */
public record FieldError(String field, String code) {
}
