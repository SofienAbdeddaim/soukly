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
 */
@ConfigurationProperties("soukly.user")
public record UserProperties(URI frontBaseUrl, String mailFrom,
        @DefaultValue("24h") Duration verificationTokenTtl,
        @DefaultValue("3") int verificationResendsPerHour,
        @DefaultValue("1h") Duration passwordResetTokenTtl,
        @DefaultValue("3") int passwordResetsPerHour) {

    /** Returns the front-end URL of a path such as {@code /fr/account/login}, without a trailing slash issue. */
    public String frontUrl(String path) {
        return frontBaseUrl.toString().replaceAll("/+$", "") + path;
    }
}
