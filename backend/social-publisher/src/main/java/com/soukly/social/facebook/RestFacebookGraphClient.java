package com.soukly.social.facebook;

import java.net.http.HttpClient;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriBuilder;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import com.soukly.social.graph.GraphApiException;

/**
 * {@link FacebookGraphClient} over HTTP with {@link RestClient}: origin and version from {@link FacebookProperties}.
 * Tokens and the app secret go in the query string, as the Graph API documents it; the client never logs a URL, and
 * {@link #call} drops the exceptions that quote one.
 */
@Component
class RestFacebookGraphClient implements FacebookGraphClient {

    /** Pages read at most: a member rarely manages more, and only one is connected. */
    static final int MAX_PAGES = 100;

    /** Tasks that allow posting on a Page; a Page without any of them is not offered. */
    private static final Set<String> POSTING_TASKS = Set.of("CREATE_CONTENT", "MANAGE");

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
            throw new GraphApiException("Graph API " + operation + " answered " + e.getStatusCode().value(),
                    e.getStatusCode().value());
        } catch (RestClientException e) {
            throw new GraphApiException("Graph API " + operation + " failed: " + e.getClass().getSimpleName(), 0);
        }
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
