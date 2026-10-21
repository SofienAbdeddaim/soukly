package com.soukly.listing.listing;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Drafts, bound from {@code soukly.listing.drafts.*}.
 *
 * @param maxPerOwner drafts an account may keep at the same time; one more is answered with {@code 409}
 */
@ConfigurationProperties("soukly.listing.drafts")
public record DraftProperties(@DefaultValue("20") int maxPerOwner) {
}
