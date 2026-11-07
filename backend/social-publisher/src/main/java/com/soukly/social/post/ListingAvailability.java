package com.soukly.social.post;

import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.soukly.social.caption.ListingServiceProperties;

/**
 * Whether listings are still online, from the internal endpoints of listing-service (SOC-06):
 * {@code GET /internal/listings/{id}/status} and {@code POST /internal/listings/statuses} (50 ids at most per call),
 * with the shared {@code X-Internal-Token}. A post is never put back in the queue for a listing that is not online.
 */
@Component
class ListingAvailability {

    private static final Logger log = LoggerFactory.getLogger(ListingAvailability.class);

    static final String HEADER = "X-Internal-Token";

    /** Ids at most per call of {@code POST /internal/listings/statuses}. */
    static final int BATCH = 50;

    private final RestClient client;

    ListingAvailability(ListingServiceProperties properties) {
        HttpClient http = HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build();
        JdkClientHttpRequestFactory requests = new JdkClientHttpRequestFactory(http);
        requests.setReadTimeout(properties.readTimeout());
        this.client = RestClient.builder()
                .requestFactory(requests)
                .baseUrl(properties.baseUrl().toString())
                .defaultHeader(HEADER, properties.internalApiToken() == null ? "" : properties.internalApiToken())
                .build();
    }

    /**
     * @return {@code false} for a listing that is not online or does not exist
     * @throws ListingStatusUnavailableException listing-service did not answer
     */
    boolean isOnline(UUID listingId) {
        try {
            Status status = client.get().uri("/internal/listings/{id}/status", listingId)
                    .accept(MediaType.APPLICATION_JSON).retrieve().body(Status.class);
            return status != null && status.online();
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                return false;
            }
            throw unavailable(e);
        } catch (RestClientException e) {
            throw unavailable(e);
        }
    }

    /**
     * @return the listings among {@code listingIds} that are online
     * @throws ListingStatusUnavailableException listing-service did not answer
     */
    Set<UUID> online(Collection<UUID> listingIds) {
        List<UUID> ids = new ArrayList<>(new HashSet<>(listingIds));
        Set<UUID> online = new HashSet<>();
        for (int from = 0; from < ids.size(); from += BATCH) {
            List<UUID> batch = ids.subList(from, Math.min(ids.size(), from + BATCH));
            try {
                Statuses statuses = client.post().uri("/internal/listings/statuses")
                        .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                        .body(new StatusesRequest(batch)).retrieve().body(Statuses.class);
                if (statuses == null || statuses.statuses() == null) {
                    throw new ListingStatusUnavailableException(null);
                }
                statuses.statuses().stream().filter(Status::online).map(Status::id).forEach(online::add);
            } catch (RestClientException e) {
                throw unavailable(e);
            }
        }
        return online;
    }

    private static ListingStatusUnavailableException unavailable(RestClientException e) {
        log.warn("listing-service did not give the status of listings: {}", e.getClass().getSimpleName());
        return new ListingStatusUnavailableException(e);
    }

    record Status(UUID id, String status, boolean online) {
    }

    record Statuses(List<Status> statuses) {
    }

    record StatusesRequest(List<UUID> ids) {
    }
}
