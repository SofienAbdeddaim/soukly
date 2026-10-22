package com.soukly.social.instagram;

import java.net.http.HttpClient;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.soukly.social.graph.GraphApiException;

import tools.jackson.databind.JsonNode;

/**
 * {@link InstagramGraphClient} over HTTP with {@link RestClient}, from the documentation of Instagram API with
 * Instagram Login: the code is exchanged on {@code api.instagram.com}, the rest goes to {@code graph.instagram.com}.
 * Tokens and the app secret never reach a log; {@link #call} drops the exceptions that quote a URL.
 */
@Component
class RestInstagramGraphClient implements InstagramGraphClient {

    private static final String PROFILE_FIELDS = "id,user_id,username,account_type,profile_picture_url";

    private final InstagramProperties properties;
    private final RestClient api;
    private final RestClient graph;

    RestInstagramGraphClient(InstagramProperties properties) {
        this.properties = properties;
        HttpClient http = HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build();
        JdkClientHttpRequestFactory requests = new JdkClientHttpRequestFactory(http);
        requests.setReadTimeout(properties.readTimeout());
        this.api = RestClient.builder().requestFactory(requests).baseUrl(properties.apiBaseUrl().toString()).build();
        this.graph = RestClient.builder().requestFactory(requests).baseUrl(properties.graphBaseUrl().toString())
                .build();
    }

    @Override
    public ShortLivedToken exchangeCode(String code, String redirectUri) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", properties.appId());
        form.add("client_secret", properties.appSecret());
        form.add("grant_type", "authorization_code");
        form.add("redirect_uri", redirectUri);
        form.add("code", code);
        JsonNode body = call("code exchange", () -> api.post()
                .uri("/oauth/access_token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .accept(MediaType.APPLICATION_JSON)
                .body(form)
                .retrieve()
                .body(JsonNode.class));
        // The documentation shows the token in a "data" array; some answers have it at the top level
        JsonNode token = body != null && body.path("data").isArray() && !body.path("data").isEmpty()
                ? body.path("data").get(0)
                : body;
        String accessToken = text(token, "access_token");
        if (accessToken == null) {
            throw new GraphApiException("Instagram code exchange: no access_token in the answer", 200);
        }
        return new ShortLivedToken(accessToken, text(token, "user_id"), permissions(token.path("permissions")));
    }

    @Override
    public LongLivedToken exchangeForLongLived(String shortLivedToken) {
        return longLived(call("long-lived token exchange", () -> graph.get()
                .uri(uri -> uri.path("/access_token")
                        .queryParam("grant_type", "ig_exchange_token")
                        .queryParam("client_secret", "{secret}")
                        .queryParam("access_token", "{token}")
                        .build(properties.appSecret(), shortLivedToken))
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(TokenResponse.class)));
    }

    @Override
    public LongLivedToken refresh(String longLivedToken) {
        return longLived(call("token refresh", () -> graph.get()
                .uri(uri -> uri.path("/refresh_access_token")
                        .queryParam("grant_type", "ig_refresh_token")
                        .queryParam("access_token", "{token}")
                        .build(longLivedToken))
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(TokenResponse.class)));
    }

    @Override
    public InstagramProfile profile(String accessToken) {
        ProfileResponse me = call("profile", () -> graph.get()
                .uri(uri -> uri.path("/" + properties.graphVersion() + "/me")
                        .queryParam("fields", PROFILE_FIELDS)
                        .queryParam("access_token", "{token}")
                        .build(accessToken))
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(ProfileResponse.class));
        if (me == null || me.id() == null || me.username() == null) {
            throw new GraphApiException("Instagram profile: no id or username in the answer", 200);
        }
        return new InstagramProfile(me.id(), me.userId(), me.username(), me.accountType(), me.pictureUrl());
    }

    private static LongLivedToken longLived(TokenResponse response) {
        if (response == null || response.accessToken() == null || response.expiresIn() == null) {
            throw new GraphApiException("Instagram token exchange: no access_token or expires_in in the answer", 200);
        }
        return new LongLivedToken(response.accessToken(), response.expiresIn());
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? null : value.asString();
    }

    /** The permissions as a comma-separated string or an array; {@code null} when the answer has none. */
    private static Set<String> permissions(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        Set<String> permissions = new HashSet<>();
        if (node.isArray()) {
            node.forEach(permission -> permissions.add(permission.asString().trim()));
        } else {
            permissions.addAll(Arrays.stream(node.asString().split(",")).map(String::trim).toList());
        }
        return Set.copyOf(permissions);
    }

    /** Runs a call; an error keeps the operation and the status only (RestClient messages quote the URL). */
    private static <T> T call(String operation, Supplier<T> request) {
        try {
            return request.get();
        } catch (RestClientResponseException e) {
            throw new GraphApiException("Instagram " + operation + " answered " + e.getStatusCode().value(),
                    e.getStatusCode().value());
        } catch (RestClientException e) {
            throw new GraphApiException("Instagram " + operation + " failed: " + e.getClass().getSimpleName(), 0);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TokenResponse(@JsonProperty("access_token") String accessToken,
            @JsonProperty("expires_in") Long expiresIn) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ProfileResponse(String id, @JsonProperty("user_id") String userId, String username,
            @JsonProperty("account_type") String accountType,
            @JsonProperty("profile_picture_url") String pictureUrl) {
    }
}
