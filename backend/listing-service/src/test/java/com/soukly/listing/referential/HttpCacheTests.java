package com.soukly.listing.referential;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;

import com.soukly.listing.support.IntegrationTest;

class HttpCacheTests extends IntegrationTest {

    @ParameterizedTest
    @ValueSource(strings = { "/categories", "/categories/vehicles.cars/attributes", "/locations/governorates",
            "/locations/governorates/sfax/delegations" })
    void answersArePublicForOneHourWithAnEtag(String path) throws Exception {
        MockHttpServletResponse response = mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andExpect(header().exists(HttpHeaders.ETAG))
                .andReturn().getResponse();

        assertThat(response.getHeader(HttpHeaders.CACHE_CONTROL)).contains("public").contains("max-age=3600");
    }

    @ParameterizedTest
    @ValueSource(strings = { "/categories", "/categories/vehicles.cars/attributes", "/locations/governorates",
            "/locations/governorates/sfax/delegations" })
    void sameEtagAnswersNotModifiedWithoutBody(String path) throws Exception {
        String etag = mockMvc.perform(get(path)).andReturn().getResponse().getHeader(HttpHeaders.ETAG);

        MockHttpServletResponse response = mockMvc.perform(get(path).header(HttpHeaders.IF_NONE_MATCH, etag))
                .andExpect(status().isNotModified())
                .andExpect(header().string(HttpHeaders.ETAG, etag))
                .andReturn().getResponse();

        assertThat(response.getContentAsByteArray()).isEmpty();
        assertThat(response.getHeader(HttpHeaders.CACHE_CONTROL)).contains("max-age=3600");
    }

    @ParameterizedTest
    @ValueSource(strings = { "/categories", "/locations/governorates" })
    void otherEtagAnswersTheFullBody(String path) throws Exception {
        mockMvc.perform(get(path).header(HttpHeaders.IF_NONE_MATCH, "\"outdated\""))
                .andExpect(status().isOk())
                .andExpect(header().exists(HttpHeaders.ETAG));
    }
}
