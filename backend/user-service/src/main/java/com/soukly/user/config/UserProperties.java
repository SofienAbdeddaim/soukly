package com.soukly.user.config;

import java.net.URI;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings of user-service, bound from {@code soukly.user.*} in {@code application.yml}.
 *
 * @param frontBaseUrl               origin of the front end, used in the links of the emails ({@code FRONT_BASE_URL})
 * @param mailFrom                   sender of the emails ({@code MAIL_FROM})
 * @param verificationTokenTtl       validity of a verification link
 * @param verificationResendsPerHour verification emails an account may receive per hour after the first one
 * @param passwordResetTokenTtl      validity of a password reset link (AUTH-04)
 * @param passwordResetsPerHour      password reset links an account may receive per hour
 * @param emailChangeTokenTtl        validity of the confirmation link of a new address (AUTH-05)
 * @param emailChangesPerHour        changes of address an account may ask per hour
 * @param accountPurgeAfter          a deleted account is erased for good after this (AUTH-05)
 * @param termsVersion               version of the terms of use accepted at registration ({@code LEGAL_TERMS_VERSION},
 *                                   LEG-01), the {@code version} of {@code frontend/legal/<lang>/terms.md}
 */
@ConfigurationProperties("soukly.user")
public record UserProperties(URI frontBaseUrl, String mailFrom,
        @DefaultValue("24h") Duration verificationTokenTtl,
        @DefaultValue("3") int verificationResendsPerHour,
        @DefaultValue("1h") Duration passwordResetTokenTtl,
        @DefaultValue("3") int passwordResetsPerHour,
        @DefaultValue("24h") Duration emailChangeTokenTtl,
        @DefaultValue("3") int emailChangesPerHour,
        @DefaultValue("30d") Duration accountPurgeAfter,
        String termsVersion) {

    public UserProperties {
        if (termsVersion == null || !termsVersion.matches("\\d{4}-\\d{2}-\\d{2}")) {
            throw new IllegalArgumentException(
                    "soukly.user.terms-version (LEGAL_TERMS_VERSION) must be a YYYY-MM-DD date");
        }
    }

    /** Returns the front-end URL of a path such as {@code /fr/account/login}, without a trailing slash issue. */
    public String frontUrl(String path) {
        return frontBaseUrl.toString().replaceAll("/+$", "") + path;
    }
}
