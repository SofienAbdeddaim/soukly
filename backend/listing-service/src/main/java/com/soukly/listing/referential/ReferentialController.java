package com.soukly.listing.referential;

import java.time.Clock;
import java.time.Duration;
import java.time.Year;
import java.util.List;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Public read-only API of the referentials, under {@code /api} on the gateway. Every answer carries the three
 * labels and may be cached for one hour; {@link ReferentialCacheConfig} adds the {@code ETag} and answers
 * {@code 304}.
 */
@RestController
class ReferentialController {

    static final CacheControl CACHE_CONTROL = CacheControl.maxAge(Duration.ofHours(1)).cachePublic();

    private final ReferentialService referentials;
    private final Clock clock;

    ReferentialController(ReferentialService referentials, Clock clock) {
        this.referentials = referentials;
        this.clock = clock;
    }

    @GetMapping("/categories")
    ResponseEntity<List<CategoryFamily>> categories() {
        return cached(referentials.categories());
    }

    @GetMapping("/categories/{code}/attributes")
    ResponseEntity<List<AttributeResponse>> attributes(@PathVariable String code) {
        Year currentYear = Year.now(clock);
        return cached(referentials.attributesOf(code).stream()
                .map(attribute -> AttributeResponse.of(attribute, currentYear))
                .toList());
    }

    @GetMapping("/locations/governorates")
    ResponseEntity<List<Governorate>> governorates() {
        return cached(referentials.governorates());
    }

    @GetMapping("/locations/governorates/{code}/delegations")
    ResponseEntity<List<DelegationResponse>> delegations(@PathVariable String code) {
        return cached(referentials.delegationsOf(code).stream().map(DelegationResponse::of).toList());
    }

    private static <T> ResponseEntity<T> cached(T body) {
        return ResponseEntity.ok().cacheControl(CACHE_CONTROL).body(body);
    }

    /**
     * An attribute as published: bounds that do not apply to its type are left out, and a maximum that depends on
     * the current year is resolved (year: current year + 1).
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record AttributeResponse(String code, AttributeType type, boolean required, Integer minValue, Integer maxValue,
            Integer minLength, Integer maxLength, boolean filterable, Labels label, List<EnumValue> values) {

        static AttributeResponse of(Attribute attribute, Year currentYear) {
            return new AttributeResponse(attribute.code(), attribute.type(), attribute.required(),
                    attribute.minValue(), attribute.effectiveMaxValue(currentYear), attribute.minLength(),
                    attribute.maxLength(), attribute.filterable(), attribute.label(),
                    attribute.type() == AttributeType.ENUM ? attribute.values() : null);
        }
    }

    record DelegationResponse(String code, Labels label) {

        static DelegationResponse of(Delegation delegation) {
            return new DelegationResponse(delegation.code(), delegation.label());
        }
    }
}
