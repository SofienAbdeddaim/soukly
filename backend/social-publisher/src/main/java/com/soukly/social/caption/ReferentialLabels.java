package com.soukly.social.caption;

import java.net.http.HttpClient;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * Labels of the subcategories, governorates and delegations of CAT-01 in the three languages, read from the public
 * referential endpoints of listing-service ({@code GET /categories}, {@code /locations/governorates},
 * {@code /locations/governorates/{code}/delegations}) and kept in memory for
 * {@link ListingServiceProperties#labelsCacheTtl()}. listing-service stays the single source of the places and
 * categories: nothing is copied in this service.
 */
@Component
@EnableConfigurationProperties(ListingServiceProperties.class)
public class ReferentialLabels {

    private static final Logger log = LoggerFactory.getLogger(ReferentialLabels.class);
    private static final String CATEGORIES = "categories";
    private static final String GOVERNORATES = "governorates";

    private final RestClient client;
    private final Cache<String, Map<String, Labels>> cache;

    ReferentialLabels(ListingServiceProperties properties) {
        HttpClient http = HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build();
        JdkClientHttpRequestFactory requests = new JdkClientHttpRequestFactory(http);
        requests.setReadTimeout(properties.readTimeout());
        this.client = RestClient.builder()
                .requestFactory(requests)
                .baseUrl(properties.baseUrl().toString())
                .build();
        this.cache = Caffeine.newBuilder().expireAfterWrite(properties.labelsCacheTtl()).maximumSize(64).build();
    }

    /**
     * @throws CaptionUnavailableException listing-service did not answer
     */
    public Optional<Labels> subcategory(String code) {
        return find(CATEGORIES, code, () -> {
            Map<String, Labels> labels = new HashMap<>();
            for (Family family : get("/categories", Family[].class)) {
                for (Element subcategory : family.subcategories()) {
                    labels.put(subcategory.code(), subcategory.label());
                }
            }
            return labels;
        });
    }

    /**
     * @throws CaptionUnavailableException listing-service did not answer
     */
    public Optional<Labels> governorate(String code) {
        return find(GOVERNORATES, code, () -> index(get("/locations/governorates", Element[].class)));
    }

    /**
     * A delegation of this governorate; empty when the governorate does not have it.
     *
     * @throws CaptionUnavailableException listing-service did not answer
     */
    public Optional<Labels> delegation(String governorateCode, String code) {
        if (governorate(governorateCode).isEmpty()) {
            return Optional.empty();
        }
        return find("delegations:" + governorateCode, code, () -> index(
                get("/locations/governorates/{code}/delegations", Element[].class, governorateCode)));
    }

    /** Forgets every label: the next text reads them again (tests). */
    void clear() {
        cache.invalidateAll();
    }

    private Optional<Labels> find(String key, String code, Supplier<Map<String, Labels>> load) {
        if (code == null) {
            return Optional.empty();
        }
        Map<String, Labels> labels = cache.getIfPresent(key);
        if (labels == null) {
            labels = load.get();
            cache.put(key, labels);
        }
        return Optional.ofNullable(labels.get(code));
    }

    private <T> T get(String path, Class<T> type, Object... variables) {
        try {
            T body = client.get().uri(path, variables).accept(MediaType.APPLICATION_JSON).retrieve().body(type);
            if (body == null) {
                throw new CaptionUnavailableException(null);
            }
            return body;
        } catch (RestClientException e) {
            log.warn("listing-service did not give the labels of {}: {}", path, e.getClass().getSimpleName());
            throw new CaptionUnavailableException(e);
        }
    }

    private static Map<String, Labels> index(Element[] elements) {
        Map<String, Labels> labels = new HashMap<>();
        for (Element element : elements) {
            labels.put(element.code(), element.label());
        }
        return labels;
    }

    /** The three labels of an element of a referential. */
    public record Labels(String fr, String ar, String en) {

        public String in(CaptionLanguage language) {
            return switch (language) {
                case FR -> fr;
                case AR -> ar;
                case EN -> en;
            };
        }
    }

    record Element(String code, Labels label) {
    }

    record Family(String code, Labels label, List<Element> subcategories) {
    }
}
