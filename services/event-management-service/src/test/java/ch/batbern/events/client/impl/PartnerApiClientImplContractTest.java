package ch.batbern.events.client.impl;

import ch.batbern.events.client.PartnerApiClient;
import ch.batbern.events.client.PartnerApiClient.PartnerTopicGroup;
import ch.batbern.events.config.RestClientConfig;
import ch.batbern.shared.test.contract.ContractServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.http.converter.autoconfigure.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.jackson2.autoconfigure.Jackson2AutoConfiguration;
import org.springframework.boot.restclient.autoconfigure.RestTemplateAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.GET;

/**
 * Contract test of {@link PartnerApiClientImpl} against {@code partners-api} (Story 17.0b1): both
 * calls of the topic blob ({@code GET /partners/topics}, {@code GET /partners?include=company})
 * validated against the PCS spec, answered with its examples.
 */
@SpringBootTest(classes = {PartnerApiClientImpl.class, RestClientConfig.class},
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "partner-service.base-url=http://pcs.test")
@ImportAutoConfiguration({
    RestTemplateAutoConfiguration.class,
    Jackson2AutoConfiguration.class,
    HttpMessageConvertersAutoConfiguration.class
})
@DisplayName("PartnerApiClientImpl honours the PCS contract")
class PartnerApiClientImplContractTest {

    private static final String PARTNERS = "docs/api/partners-api.openapi.yml";

    @Autowired
    private PartnerApiClient client;

    @Autowired
    private RestTemplate restTemplate;

    private ContractServer server;

    @BeforeEach
    void setUp() {
        Jwt jwt = Jwt.withTokenValue("organizer-token").header("alg", "none").claim("sub", "organizer").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        server = ContractServer.bindTo(restTemplate, PARTNERS);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        server.close();
    }

    @Test
    void should_groupTopicsByCompany_withLogosFromPartnerList() {
        server.expect(GET, "/api/v1/partners/topics")
                .andExpect(r -> assertThat(r.getHeaders().getFirst(HttpHeaders.AUTHORIZATION))
                        .isEqualTo("Bearer organizer-token"))
                .respondWithExample();
        server.expect(GET, "/api/v1/partners").withQueryParam("include", "company").respondWithExample();

        List<PartnerTopicGroup> groups = client.getPartnerTopics();

        assertThat(groups).extracting(PartnerTopicGroup::companyName)
                .containsExactlyInAnyOrder("mobiliar", "Unknown");
        PartnerTopicGroup mobiliar = groups.stream().filter(g -> g.companyName().equals("mobiliar")).findFirst()
                .orElseThrow();
        assertThat(mobiliar.logoUrl()).isEqualTo("https://cdn.batbern.ch/logos/mobiliar/logo.png");
        assertThat(mobiliar.topics()).singleElement().satisfies(t -> {
            assertThat(t.title()).isEqualTo("Platform Engineering in regulated industries");
            assertThat(t.voteCount()).isEqualTo(4);
        });
        server.verify();
    }

    @Test
    void should_renderWithoutLogos_when_partnerListFails() {
        server.expect(GET, "/api/v1/partners/topics").respondWithExample();
        server.expect(GET, "/api/v1/partners").respondWithStatus(HttpStatus.BAD_REQUEST);

        List<PartnerTopicGroup> groups = client.getPartnerTopics();

        assertThat(groups).hasSize(2).allSatisfy(g -> assertThat(g.logoUrl()).isNull());
        server.verify();
    }

    @Test
    void should_returnEmpty_when_noTopics() {
        server.expect(GET, "/api/v1/partners/topics").respondWith(HttpStatus.OK, "[]");

        assertThat(client.getPartnerTopics()).isEmpty();
        server.verify();
    }
}
