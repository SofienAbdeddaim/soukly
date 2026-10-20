package com.soukly.user.mail;

import java.util.Set;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/**
 * Thymeleaf engine of the emails: templates in {@code mail/*.html} and {@code mail/*.txt}, texts in
 * {@code mail/messages_{fr,ar,en}.properties}.
 */
@Configuration(proxyBeanMethods = false)
class MailTemplateConfig {

    @Bean
    ResourceBundleMessageSource mailMessages() {
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("mail/messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);
        // Every text goes through MessageFormat, so '' is always an apostrophe (French texts).
        messages.setAlwaysUseMessageFormat(true);
        return messages;
    }

    @Bean
    SpringTemplateEngine mailTemplateEngine(ResourceBundleMessageSource mailMessages) {
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.addTemplateResolver(resolver(TemplateMode.HTML, "*.html", 1));
        engine.addTemplateResolver(resolver(TemplateMode.TEXT, "*.txt", 2));
        engine.setTemplateEngineMessageSource(mailMessages);
        return engine;
    }

    private static ClassLoaderTemplateResolver resolver(TemplateMode mode, String pattern, int order) {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("mail/");
        resolver.setTemplateMode(mode);
        resolver.setResolvablePatterns(Set.of(pattern));
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCheckExistence(true);
        resolver.setOrder(order);
        return resolver;
    }
}
