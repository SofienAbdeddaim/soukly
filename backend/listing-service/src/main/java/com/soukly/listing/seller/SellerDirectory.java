package com.soukly.listing.seller;

import java.net.http.HttpClient;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * Sellers as user-service knows them, read through its internal endpoints with the shared {@code X-Internal-Token}
 * (CATA-03). Public profiles are kept in memory for {@link UserServiceProperties#profileCacheTtl()}, including the
 * answer "no active account"; phone numbers are never kept. Neither is ever logged.
 */
@Component
@EnableConfigurationProperties(UserServiceProperties.class)
public class SellerDirectory {

    static final String TOKEN_HEADER = "X-Internal-Token";

    private static final Logger log = LoggerFactory.getLogger(SellerDirectory.class);

    private final RestClient client;
    private final Cache<UUID, Optional<SellerProfile>> profiles;

    SellerDirectory(UserServiceProperties properties) {
        HttpClient http = HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build();
        JdkClientHttpRequestFactory requests = new JdkClientHttpRequestFactory(http);
        requests.setReadTimeout(properties.readTimeout());
        this.client = RestClient.builder()
                .requestFactory(requests)
                .baseUrl(properties.baseUrl().toString())
                .defaultHeader(TOKEN_HEADER, properties.internalApiToken() == null ? "" : properties.internalApiToken())
                .build();
        this.profiles = Caffeine.newBuilder()
                .expireAfterWrite(properties.profileCacheTtl())
                .maximumSize(properties.profileCacheSize())
                .build();
    }

    /**
     * @return empty when user-service has no active account with this id (unknown, or closed)
     * @throws SellerUnavailableException user-service did not answer
     */
    public Optional<SellerProfile> profile(UUID sellerId) {
        Optional<SellerProfile> cached = profiles.getIfPresent(sellerId);
        if (cached != null) {
            return cached;
        }
        Optional<SellerProfile> profile = call(() -> client.get()
                .uri("/internal/users/{id}/public-profile", sellerId)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(SellerProfile.class));
        profiles.put(sellerId, profile);
        return profile;
    }

    /**
     * @return the phone number in E.164 ({@code +21620123456}), empty when the account is not active
     * @throws SellerUnavailableException user-service did not answer
     */
    public Optional<String> phone(UUID sellerId) {
        return call(() -> client.get()
                .uri("/internal/users/{id}/contact", sellerId)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(Contact.class))
                .map(Contact::phone);
    }

    private static <T> Optional<T> call(Supplier<T> request) {
        try {
            return Optional.ofNullable(request.get());
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            }
            log.error("user-service refused an internal call with {}", e.getStatusCode().value());
            throw new SellerUnavailableException(e);
        } catch (RestClientException e) {
            log.warn("user-service is unavailable: {}", e.getClass().getSimpleName());
            throw new SellerUnavailableException(e);
        }
    }

    /** Body of {@code GET /internal/users/{id}/contact}. */
    record Contact(String phone) {
    }
}
