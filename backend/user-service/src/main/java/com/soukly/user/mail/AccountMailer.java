package com.soukly.user.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.util.UriComponentsBuilder;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import com.soukly.user.config.UserProperties;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

/**
 * Sends the account emails after the transaction commits (nothing is sent when it rolls back), on a separate
 * thread. A failure is logged and never fails the request: the user can ask for another email.
 */
@Component
class AccountMailer {

    private static final Logger log = LoggerFactory.getLogger(AccountMailer.class);

    private final JavaMailSender sender;
    private final SpringTemplateEngine templates;
    private final ResourceBundleMessageSource messages;
    private final UserProperties properties;

    AccountMailer(JavaMailSender sender, SpringTemplateEngine mailTemplateEngine,
            ResourceBundleMessageSource mailMessages, UserProperties properties) {
        this.sender = sender;
        this.templates = mailTemplateEngine;
        this.messages = mailMessages;
        this.properties = properties;
    }

    @Async
    @TransactionalEventListener
    void send(AccountMail mail) {
        String template = switch (mail) {
            case AccountMail.Verification v -> "verification";
            case AccountMail.AccountExists e -> "account-exists";
        };
        try {
            Context context = context(mail);
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(properties.mailFrom());
            helper.setTo(mail.to());
            helper.setSubject(messages.getMessage(template + ".subject", null, mail.language().locale()));
            helper.setText(templates.process(template + ".txt", context),
                    templates.process(template + ".html", context));
            sender.send(message);
            log.info("Sent {} email to user {}", template, mail.userId());
        } catch (MessagingException | RuntimeException e) {
            // The exception message may contain the address: log its type and root cause type only.
            log.error("Could not send {} email to user {}: {} (root cause: {})", template, mail.userId(),
                    e.getClass().getName(), rootCause(e).getClass().getName());
        }
    }

    private Context context(AccountMail mail) {
        String lang = mail.language().code();
        Context context = new Context(mail.language().locale());
        context.setVariable("lang", lang);
        context.setVariable("dir", mail.language().isRightToLeft() ? "rtl" : "ltr");
        context.setVariable("displayName", mail.displayName());
        switch (mail) {
            case AccountMail.Verification v -> {
                context.setVariable("verifyUrl", UriComponentsBuilder
                        .fromUriString(properties.frontUrl("/" + lang + "/account/verify-email"))
                        .queryParam("token", v.token()).build().toUriString());
                // A string, so that MessageFormat keeps Western digits in Arabic (as written in Tunisia)
                context.setVariable("validityHours", String.valueOf(properties.verificationTokenTtl().toHours()));
            }
            case AccountMail.AccountExists e -> {
                context.setVariable("loginUrl", properties.frontUrl("/" + lang + "/account/login"));
                context.setVariable("forgotPasswordUrl", properties.frontUrl("/" + lang + "/account/forgot-password"));
            }
        }
        return context;
    }

    private static Throwable rootCause(Throwable e) {
        Throwable cause = e;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause;
    }
}
