package com.soukly.social.facebook;

import java.net.URI;
import java.net.http.HttpClient;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriBuilder;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import com.soukly.social.graph.GraphApiException;
import com.soukly.social.graph.GraphError;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@link FacebookGraphClient} over HTTP with {@link RestClient}: origin and version from {@link FacebookProperties}.
 * Tokens and the app secret go in the query string, as the Graph API documents it, except for the publishing calls
 * whose parameters, Page token included, go in a form body; the client never logs a URL, and {@link #call} drops the
 * exceptions that quote one, keeping the {@code error} object of Meta ({@link GraphError}).
 */
@Component
class RestFacebookGraphClient implements FacebookGraphClient {

    /** Pages read at most: a member rarely manages more, and only one is connected. */
    static final int MAX_PAGES = 100;

    /** Tasks that allow posting on a Page; a Page without any of them is not offered. */
    private static final Set<String> POSTING_TASKS = Set.of("CREATE_CONTENT", "MANAGE");

    /** Reads the error objects of Meta; the answers of the calls are read by the converters of RestClient. */
    private static final JsonMapper ERRORS = JsonMapper.builder().build();

    private static final String ACCOUNT_FIELDS = "id,name,category,access_token,tasks,picture{url}";

    private final FacebookProperties properties;
    private final RestClient client;

    RestFacebookGraphClient(FacebookProperties properties) {
        this.properties = properties;
        HttpClient http = HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build();
        JdkClientHttpRequestFactory requests = new JdkClientHttpRequestFactory(http);
        requests.setReadTimeout(properties.readTimeout());
        this.client = RestClient.builder()
                .requestFactory(requests)
                .baseUrl(properties.graphBaseUrl().toString())
                .build();
    }

    @Override
    public String exchangeCode(String code, String redirectUri) {
        return token(call("code exchange", () -> client.get()
                .uri(uri -> versioned(uri, "/oauth/access_token")
                        .queryParam("client_id", properties.appId())
                        .queryParam("client_secret", "{secret}")
                        .queryParam("redirect_uri", "{redirect}")
                        .queryParam("code", "{code}")
                        .build(properties.appSecret(), redirectUri, code))
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(TokenResponse.class)));
    }

    @Override
    public String exchangeForLongLived(String userAccessToken) {
        return token(call("long-lived token exchange", () -> client.get()
                .uri(uri -> versioned(uri, "/oauth/access_token")
                        .queryParam("grant_type", "fb_exchange_token")
                        .queryParam("client_id", properties.appId())
                        .queryParam("client_secret", "{secret}")
                        .queryParam("fb_exchange_token", "{token}")
                        .build(properties.appSecret(), userAccessToken))
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(TokenResponse.class)));
    }

    @Override
    public String userId(String userAccessToken) {
        IdResponse me = call("user id", () -> client.get()
                .uri(uri -> versioned(uri, "/me").queryParam("fields", "id")
                        .queryParam("access_token", "{token}").build(userAccessToken))
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(IdResponse.class));
        if (me == null || me.id() == null) {
            throw new GraphApiException("Graph API user id: no id in the answer", 200);
        }
        return me.id();
    }

    @Override
    public Set<String> grantedPermissions(String userAccessToken) {
        PermissionsResponse permissions = call("permissions", () -> client.get()
                .uri(uri -> versioned(uri, "/me/permissions")
                        .queryParam("access_token", "{token}").build(userAccessToken))
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(PermissionsResponse.class));
        if (permissions == null || permissions.data() == null) {
            return Set.of();
        }
        return permissions.data().stream()
                .filter(permission -> "granted".equals(permission.status()))
                .map(Permission::permission)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public List<ManagedPage> managedPages(String userAccessToken) {
        AccountsResponse accounts = call("managed pages", () -> client.get()
                .uri(uri -> versioned(uri, "/me/accounts")
                        // A variable: the braces of picture{url} are not a template
                        .queryParam("fields", "{fields}")
                        .queryParam("limit", MAX_PAGES)
                        .queryParam("access_token", "{token}").build(ACCOUNT_FIELDS, userAccessToken))
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(AccountsResponse.class));
        if (accounts == null || accounts.data() == null) {
            return List.of();
        }
        return accounts.data().stream()
                .filter(page -> page.id() != null && page.accessToken() != null)
                .filter(page -> page.tasks() == null || page.tasks().stream().anyMatch(POSTING_TASKS::contains))
                .map(page -> new ManagedPage(page.id(), page.name() == null ? page.id() : page.name(),
                        page.category(), page.pictureUrl(), page.accessToken()))
                .toList();
    }

    @Override
    public void revokePermissions(String userId) {
        call("permission revocation", () -> client.delete()
                .uri(uri -> versioned(uri, "/{user}/permissions")
                        .queryParam("access_token", "{appToken}")
                        .build(userId, properties.appId() + "|" + properties.appSecret()))
                .retrieve()
                .toBodilessEntity());
    }

    @Override
    public String uploadUnpublishedPhoto(String pageId, String pageToken, URI photoUrl) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("url", photoUrl.toString());
        form.add("published", "false");
        form.add("access_token", pageToken);
        return id("photo upload", call("photo upload", () -> client.post()
                .uri(uri -> versioned(uri, "/{page}/photos").build(pageId))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(IdResponse.class)));
    }

    @Override
    public String createPost(String pageId, String pageToken, String message, List<String> photoIds) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("message", message);
        for (int i = 0; i < photoIds.size(); i++) {
            form.add("attached_media[" + i + "]", "{\"media_fbid\":\"" + photoIds.get(i) + "\"}");
        }
        form.add("access_token", pageToken);
        return id("post creation", call("post creation", () -> client.post()
                .uri(uri -> versioned(uri, "/{page}/feed").build(pageId))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(IdResponse.class)));
    }

    @Override
    public String permalink(String postId, String pageToken) {
        PermalinkResponse post = call("permalink", () -> client.get()
                .uri(uri -> versioned(uri, "/{post}").queryParam("fields", "permalink_url")
                        .queryParam("access_token", "{token}").build(postId, pageToken))
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(PermalinkResponse.class));
        return post == null ? null : post.permalinkUrl();
    }

    @Override
    public void deletePost(String postId, String pageToken) {
        call("post deletion", () -> client.delete()
                .uri(uri -> versioned(uri, "/{post}").queryParam("access_token", "{token}").build(postId, pageToken))
                .retrieve()
                .toBodilessEntity());
    }

    private static String id(String operation, IdResponse response) {
        if (response == null || response.id() == null) {
            throw new GraphApiException("Graph API " + operation + ": no id in the answer", 200);
        }
        return response.id();
    }

    private UriBuilder versioned(UriBuilder uri, String path) {
        return uri.path("/" + properties.graphVersion() + path);
    }

    private static String token(TokenResponse response) {
        if (response == null || response.accessToken() == null) {
            throw new GraphApiException("Graph API token exchange: no access_token in the answer", 200);
        }
        return response.accessToken();
    }

    /** Runs a call; an error keeps the operation and the status only (RestClient messages quote the URL). */
    private static <T> T call(String operation, Supplier<T> request) {
        try {
            return request.get();
        } catch (RestClientResponseException e) {
            GraphError error = error(e);
            throw new GraphApiException("Graph API " + operation + " answered " + e.getStatusCode().value()
                    + (error == null ? "" : " with error " + error.codes()), e.getStatusCode().value(), error);
        } catch (RestClientException e) {
            throw new GraphApiException("Graph API " + operation + " failed: " + e.getClass().getSimpleName(), 0);
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
    record PermalinkResponse(@JsonProperty("permalink_url") String permalinkUrl) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TokenResponse(@JsonProperty("access_token") String accessToken) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record IdResponse(String id) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PermissionsResponse(List<Permission> data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Permission(String permission, String status) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AccountsResponse(List<Account> data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Account(String id, String name, String category, @JsonProperty("access_token") String accessToken,
            List<String> tasks, Picture picture) {

        String pictureUrl() {
            return picture == null || picture.data() == null ? null : picture.data().url();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Picture(PictureData data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PictureData(String url) {
    }
}
