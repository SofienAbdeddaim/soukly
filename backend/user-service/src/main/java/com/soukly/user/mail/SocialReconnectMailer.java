package com.soukly.user.mail;

import java.util.Locale;

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
 * Email "connect your Facebook Page / Instagram account again" (SOC-06), sent synchronously by the consumer of the
 * social events: a failure is thrown, so that the event is retried and finally sent to the DLQ.
 */
@Component
public class SocialReconnectMailer {

    private static final Logger log = LoggerFactory.getLogger(SocialReconnectMailer.class);

    static final String TEMPLATE = "social-reconnect";

    private final JavaMailSender sender;
    private final SpringTemplateEngine templates;
    private final ResourceBundleMessageSource messages;
    private final UserProperties properties;

    SocialReconnectMailer(JavaMailSender sender, SpringTemplateEngine mailTemplateEngine,
            ResourceBundleMessageSource mailMessages, UserProperties properties) {
        this.sender = sender;
        this.templates = mailTemplateEngine;
        this.messages = mailMessages;
        this.properties = properties;
    }

    /**
     * @param network     {@code FACEBOOK} or {@code INSTAGRAM}
     * @param accountName name of the Page or {@code @username}; {@code null} when unknown
     * @throws ReconnectNotSentException the SMTP server refused or could not be reached; its message never contains
     *                                   the address
     */
    public void sendReconnect(UserAccount account, String network, String accountName) {
        Language language = account.preferredLanguage();
        String lang = language.code();
        String networkKey = network.toLowerCase(Locale.ROOT);
        Context context = new Context(language.locale());
        context.setVariable("lang", lang);
        context.setVariable("dir", language.isRightToLeft() ? "rtl" : "ltr");
        context.setVariable("displayName", account.displayName());
        String subject = messages.getMessage(TEMPLATE + ".subject." + networkKey, null, language.locale());
        context.setVariable("subject", subject);
        context.setVariable("intro", messages.getMessage(TEMPLATE + ".intro." + networkKey, null, language.locale()));
        context.setVariable("why", messages.getMessage(TEMPLATE + ".why." + networkKey, null, language.locale()));
        context.setVariable("accountName", accountName == null || accountName.isBlank() ? null : accountName);
        // Page "My networks" of SOC-01, in the language of the member
        context.setVariable("socialUrl", properties.frontUrl("/" + lang + "/account/social"));
        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(properties.mailFrom());
            helper.setTo(account.email());
            helper.setSubject(subject);
            helper.setText(templates.process(TEMPLATE + ".txt", context), templates.process(TEMPLATE + ".html",
                    context));
            sender.send(message);
            log.info("Sent {} email ({}) to user {}", TEMPLATE, networkKey, account.id());
        } catch (MessagingException | MailException e) {
            // The exception message may contain the address: only types are kept, and the cause is not attached.
            throw new ReconnectNotSentException("Could not send the " + TEMPLATE + " email to user " + account.id()
                    + ": " + e.getClass().getName() + " (root cause: " + rootCause(e).getClass().getName() + ")");
        }
    }

    private static Throwable rootCause(Throwable e) {
        Throwable cause = e;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause;
    }

    /** The email was not sent; retried by the consumer. */
    public static class ReconnectNotSentException extends RuntimeException {

        ReconnectNotSentException(String message) {
            super(message);
        }
    }
}
