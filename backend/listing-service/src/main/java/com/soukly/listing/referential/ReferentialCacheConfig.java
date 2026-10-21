package com.soukly.listing.referential;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.ShallowEtagHeaderFilter;

@Configuration(proxyBeanMethods = false)
class ReferentialCacheConfig {

    /**
     * {@code ETag} (hash of the body) on the referential answers, and {@code 304} without a body when
     * {@code If-None-Match} matches. Computing the hash is cheap: the data is in memory and small.
     */
    @Bean
    FilterRegistrationBean<ShallowEtagHeaderFilter> referentialEtagFilter() {
        FilterRegistrationBean<ShallowEtagHeaderFilter> registration =
                new FilterRegistrationBean<>(new ShallowEtagHeaderFilter());
        registration.addUrlPatterns("/categories", "/categories/*", "/locations/*");
        return registration;
    }
}
