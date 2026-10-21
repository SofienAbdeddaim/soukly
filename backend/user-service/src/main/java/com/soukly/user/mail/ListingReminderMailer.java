package com.soukly.user.mail;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import com.soukly.user.account.Language;
import com.soukly.user.account.UserAccount;
import com.soukly.user.config.UserProperties;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

/**
 * Reminder email of a listing that expires soon (ANN-03), sent synchronously by the consumer of the listing events:
 * a failure is thrown, so that the event is retried and finally sent to the DLQ.
 */
@Component
public class ListingReminderMailer {

    private static final Logger log = LoggerFactory.getLogger(ListingReminderMailer.class);

    static final String TEMPLATE = "listing-expiring";

    /** Dates are shown in the time zone of Tunisia. */
    private static final ZoneId TUNIS = ZoneId.of("Africa/Tunis");

    private final JavaMailSender sender;
    private final SpringTemplateEngine templates;
    private final ResourceBundleMessageSource messages;
    private final UserProperties properties;

    ListingReminderMailer(JavaMailSender sender, SpringTemplateEngine mailTemplateEngine,
            ResourceBundleMessageSource mailMessages, UserProperties properties) {
        this.sender = sender;
        this.templates = mailTemplateEngine;
        this.messages = mailMessages;
        this.properties = properties;
    }

    /**
     * @throws ReminderNotSentException the SMTP server refused or could not be reached; its message never contains
     *                                  the address
     */
    public void sendExpiryReminder(UserAccount account, UUID listingId, String title, Instant expiresAt) {
        Language language = account.preferredLanguage();
        String lang = language.code();
        Context context = new Context(language.locale());
        context.setVariable("lang", lang);
        context.setVariable("dir", language.isRightToLeft() ? "rtl" : "ltr");
        context.setVariable("displayName", account.displayName());
        // Not "title": the layout fragment has a parameter of that name (the subject)
        context.setVariable("listingTitle", title);
        context.setVariable("expiryDate", date(expiresAt, language));
        // Page "My listings" of ANN-05, tab of the published listings
        context.setVariable("listingsUrl", properties.frontUrl("/" + lang + "/account/listings"));
        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(properties.mailFrom());
            helper.setTo(account.email());
            helper.setSubject(messages.getMessage(TEMPLATE + ".subject", new Object[] { title }, language.locale()));
            helper.setText(templates.process(TEMPLATE + ".txt", context), templates.process(TEMPLATE + ".html",
                    context));
            sender.send(message);
            log.info("Sent {} email to user {} for listing {}", TEMPLATE, account.id(), listingId);
        } catch (MessagingException | MailException e) {
            // The exception message may contain the address: only types are kept, and the cause is not attached.
            throw new ReminderNotSentException("Could not send the " + TEMPLATE + " email to user " + account.id()
                    + ": " + e.getClass().getName() + " (root cause: " + rootCause(e).getClass().getName() + ")");
        }
    }

    /** Long date of the language, with Western digits in Arabic as written in Tunisia (locale {@code ar-TN}). */
    static String date(Instant instant, Language language) {
        Locale locale = Locale.forLanguageTag(language.code() + "-TN");
        return DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale).format(instant.atZone(TUNIS));
    }

    private static Throwable rootCause(Throwable e) {
        Throwable cause = e;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause;
    }

    /** The reminder was not sent; retried by the consumer. */
    public static class ReminderNotSentException extends RuntimeException {

        ReminderNotSentException(String message) {
            super(message);
        }
    }
}
