package ch.batbern.events.mailcenter;

import ch.batbern.events.config.RestClientConfig;
import ch.batbern.shared.test.contract.ContractServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.GET;

/**
 * Contract test of {@link MailCenterRequestFeedClient} against
 * {@code docs/api/internal/cums-internal.openapi.yml} (Epic 17 entry 7): the real client on the real
 * {@code RestTemplate} bean, each request validated against the spec, answers from its examples.
 * ADR-016 decision 3: the job runs without a user and sends no JWT; a signed-in caller's JWT would
 * be forwarded, and the call works either way.
 */
@SpringBootTest(classes = {MailCenterRequestFeedClient.class, RestClientConfig.class},
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "user-service.base-url=http://cums.test")
@ImportAutoConfiguration({
    RestTemplateAutoConfiguration.class,
    Jackson2AutoConfiguration.class,
    HttpMessageConvertersAutoConfiguration.class
})
@DisplayName("MailCenterRequestFeedClient honours the CUMS internal contract")
class MailCenterRequestFeedClientContractTest {

    private static final String CUMS_INTERNAL = "docs/api/internal/cums-internal.openapi.yml";
    private static final String PATH = "/internal/mail-center-requests";
    private static final String TOKEN = "user-token";
    private static final String CURSOR =
            "MjAyNi0xMS0xMVQxMDowMDowNS42NTQzMjFafDdhMWIyYzNkLTRlNWYtNGE2Yi04YzdkLTllMGYxYTJiM2M0ZA";

    @Autowired
    private MailCenterRequestFeedClient client;
    @Autowired
    private RestTemplate restTemplate;

    private ContractServer cums;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        cums = ContractServer.bindTo(restTemplate, CUMS_INTERNAL);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        cums.close();
    }

    @ParameterizedTest(name = "signed in: {0}")
    @CsvSource({"true", "false"})
    @DisplayName("reads a page, with the user's JWT when there is one and without any otherwise")
    void should_readPage_withOrWithoutJwt(boolean user) {
        if (user) {
            Jwt jwt = Jwt.withTokenValue(TOKEN).header("alg", "none").claim("sub", "organizer")
                    .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(3600)).build();
            SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        }
        cums.expect(GET, PATH).withQueryParam("limit", "100").andExpect(request -> {
            if (user) {
                assertThat(request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer " + TOKEN);
            } else {
                assertThat(request.getHeaders().containsHeader(HttpHeaders.AUTHORIZATION)).isFalse();
            }
            assertThat(request.getURI().getQuery()).doesNotContain("since");
        }).respondWithExample("openAndDecided");

        MailCenterRequestFeedClient.Page page = client.fetch(null, 100);

        assertThat(page.items()).hasSize(2);
        MailCenterRequestFeedClient.Item merge = page.items().get(0);
        assertThat(merge.kind()).isEqualTo(MailCenterRequestFeedClient.Kind.MERGE);
        assertThat(merge.requestId()).isEqualTo(UUID.fromString("2f6d8c1a-5b7e-4c3d-9a0f-1e2d3c4b5a69"));
        assertThat(merge.open()).isTrue();
        assertThat(merge.requesterEmail()).isEqualTo("anna.meier@example.com");
        assertThat(merge.subject()).isEqualTo("Profil zusammenführen: Anna Meier");
        assertThat(merge.decidedBy()).isNull();
        MailCenterRequestFeedClient.Item logo = page.items().get(1);
        assertThat(logo.kind()).isEqualTo(MailCenterRequestFeedClient.Kind.LOGO);
        assertThat(logo.open()).isFalse();
        assertThat(logo.decidedBy()).isEqualTo("organizer.alice");
        assertThat(page.nextCursor()).isEqualTo(CURSOR);
        cums.verify();
    }

    @Test
    @DisplayName("sends the stored cursor back unchanged as since")
    void should_sendCursor_asSince() {
        cums.expect(GET, PATH).withQueryParam("since", CURSOR).withQueryParam("limit", "50")
                .respondWithExample("empty");

        MailCenterRequestFeedClient.Page page = client.fetch(CURSOR, 50);

        assertThat(page.items()).isEmpty();
        assertThat(page.nextCursor()).isEqualTo(CURSOR);
        cums.verify();
    }

    @Test
    @DisplayName("a 400 (unknown cursor) is thrown, so the job keeps its cursor")
    void should_throw_whenCumsRejectsTheCursor() {
        cums.expect(GET, PATH).respondWithExample(HttpStatus.BAD_REQUEST);

        assertThatThrownBy(() -> client.fetch("bm9wZQ", 100)).isInstanceOf(HttpClientErrorException.class);
        cums.verify();
    }
}
