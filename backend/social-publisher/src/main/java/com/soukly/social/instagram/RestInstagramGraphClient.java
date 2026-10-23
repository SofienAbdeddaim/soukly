package com.soukly.social.instagram;

import java.net.URI;
import java.net.http.HttpClient;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
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
import com.soukly.social.graph.GraphError;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@link InstagramGraphClient} over HTTP with {@link RestClient}, from the documentation of Instagram API with
 * Instagram Login: the code is exchanged on {@code api.instagram.com}, the rest goes to {@code graph.instagram.com},
 * the content publishing included (SOC-05). The tokens of the publishing calls go in the form body.
 * Tokens and the app secret never reach a log; {@link #call} drops the exceptions that quote a URL.
 */
@Component
class RestInstagramGraphClient implements InstagramGraphClient {

    private static final JsonMapper ERRORS = JsonMapper.builder().build();

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

    @Override
    public String createImageContainer(String accountId, String token, URI imageUrl, String caption) {
        MultiValueMap<String, String> form = form(token);
        form.add("image_url", imageUrl.toString());
        form.add("caption", caption);
        return id("image container", post("image container", "/{account}/media", accountId, form));
    }

    @Override
    public String createCarouselItem(String accountId, String token, URI imageUrl) {
        MultiValueMap<String, String> form = form(token);
        form.add("image_url", imageUrl.toString());
        form.add("is_carousel_item", "true");
        return id("carousel item container", post("carousel item container", "/{account}/media", accountId, form));
    }

    @Override
    public String createCarousel(String accountId, String token, List<String> children, String caption) {
        MultiValueMap<String, String> form = form(token);
        form.add("media_type", "CAROUSEL");
        form.add("children", String.join(",", children));
        form.add("caption", caption);
        return id("carousel container", post("carousel container", "/{account}/media", accountId, form));
    }

    @Override
    public String containerStatus(String containerId, String token) {
        StatusResponse status = call("container status", () -> graph.get()
                .uri(uri -> uri.path("/" + properties.graphVersion() + "/{container}")
                        .queryParam("fields", "status_code")
                        .queryParam("access_token", "{token}")
                        .build(containerId, token))
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(StatusResponse.class));
        if (status == null || status.statusCode() == null) {
            throw new GraphApiException("Instagram container status: no status_code in the answer", 200);
        }
        return status.statusCode();
    }

    @Override
    public String publish(String accountId, String token, String containerId) {
        MultiValueMap<String, String> form = form(token);
        form.add("creation_id", containerId);
        return id("media publish", post("media publish", "/{account}/media_publish", accountId, form));
    }

    @Override
    public String permalink(String mediaId, String token) {
        PermalinkResponse media = call("permalink", () -> graph.get()
                .uri(uri -> uri.path("/" + properties.graphVersion() + "/{media}")
                        .queryParam("fields", "permalink")
                        .queryParam("access_token", "{token}")
                        .build(mediaId, token))
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(PermalinkResponse.class));
        return media == null ? null : media.permalink();
    }

    @Override
    public PublishingLimit publishingLimit(String accountId, String token) {
        JsonNode body = call("publishing limit", () -> graph.get()
                .uri(uri -> uri.path("/" + properties.graphVersion() + "/{account}/content_publishing_limit")
                        .queryParam("fields", "quota_usage,config")
                        .queryParam("access_token", "{token}")
                        .build(accountId, token))
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(JsonNode.class));
        JsonNode limit = body == null ? null : body.path("data").path(0);
        if (limit == null || !limit.path("quota_usage").isNumber() || !limit.path("config").path("quota_total")
                .isNumber()) {
            throw new GraphApiException("Instagram publishing limit: no quota_usage or quota_total in the answer", 200);
        }
        return new PublishingLimit(limit.path("quota_usage").asInt(), limit.path("config").path("quota_total").asInt());
    }

    /** The token goes in the form, never in the URL. */
    private static MultiValueMap<String, String> form(String token) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("access_token", token);
        return form;
    }

    private IdResponse post(String operation, String path, String accountId, MultiValueMap<String, String> form) {
        return call(operation, () -> graph.post()
                .uri(uri -> uri.path("/" + properties.graphVersion() + path).build(accountId))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .accept(MediaType.APPLICATION_JSON)
                .body(form)
                .retrieve()
                .body(IdResponse.class));
    }

    private static String id(String operation, IdResponse response) {
        if (response == null || response.id() == null) {
            throw new GraphApiException("Instagram " + operation + ": no id in the answer", 200);
        }
        return response.id();
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

    /**
     * Runs a call; an error keeps the operation, the status and the {@code error} object of Meta, cleaned of tokens
     * ({@link GraphError}), never the URL that RestClient messages quote.
     */
    private static <T> T call(String operation, Supplier<T> request) {
        try {
            return request.get();
        } catch (RestClientResponseException e) {
            GraphError error = error(e);
            throw new GraphApiException("Instagram " + operation + " answered " + e.getStatusCode().value()
                    + (error == null ? "" : " with error " + error.codes()), e.getStatusCode().value(), error);
        } catch (RestClientException e) {
            throw new GraphApiException("Instagram " + operation + " failed: " + e.getClass().getSimpleName(), 0);
        }
    }

    /** The {@code error} object of the answer; {@code null} when the body is not the JSON of Meta. */
    private static GraphError error(RestClientResponseException e) {
        try {
            byte[] body = e.getResponseBodyAsByteArray();
            ErrorResponse answer = body.length == 0 ? null : ERRORS.readValue(body, ErrorResponse.class);
            return answer == null ? null : answer.error();
        } catch (JacksonException notJson) {
            return null;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ErrorResponse(GraphError error) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record IdResponse(String id) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record StatusResponse(@JsonProperty("status_code") String statusCode) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PermalinkResponse(String permalink) {
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
