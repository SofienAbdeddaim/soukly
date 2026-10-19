package com.soukly.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(properties = "APP_VERSION=0123456789abcdef0123456789abcdef01234567")
@AutoConfigureWebTestClient
class InfoEndpointTests {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void infoShowsTheDeployedVersionWithoutToken() {
        webTestClient.get().uri("/api/actuator/info")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.app.version").isEqualTo("0123456789abcdef0123456789abcdef01234567");
    }

    @Test
    void infoIsNotServedOutsideApi() {
        webTestClient.get().uri("/actuator/info")
                .exchange()
                .expectStatus().value(status -> assertThat(status).isNotEqualTo(200));
    }
}
