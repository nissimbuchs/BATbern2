package ch.batbern.gateway.client;

import ch.batbern.gateway.config.WebClientConfig;
import ch.batbern.shared.test.contract.ContractServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.http.converter.autoconfigure.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.jackson2.autoconfigure.Jackson2AutoConfiguration;
import org.springframework.boot.restclient.autoconfigure.RestTemplateAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.net.ConnectException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

/**
 * Contract test of {@link GatewayUserStatusClient} (Story 12.2) against
 * {@code docs/api/internal/cums-internal.openapi.yml} (Story 17.0b2, replaces the
 * {@code @Mock RestTemplate} test): the real client on the real {@code cumsStatusRestTemplate} bean
 * of {@code WebClientConfig}, the request validated against the spec, answers from it.
 * Fail-open contract: 404 is unknown (empty; {@code active} is required by the spec), anything else
 * transient throws
 * {@link GatewayUserStatusException}.
 */
@SpringBootTest(classes = {WebClientConfig.class, GatewayUserStatusClient.class},
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"services.company-user-management.url=http://cums:8085",
            "spring.http.converters.preferred-json-mapper=jackson2"})
@ImportAutoConfiguration({
    RestTemplateAutoConfiguration.class,
    Jackson2AutoConfiguration.class,
    HttpMessageConvertersAutoConfiguration.class
})
@DisplayName("GatewayUserStatusClient honours the CUMS internal contract")
class GatewayUserStatusClientContractTest {

    private static final String CUMS_INTERNAL = "docs/api/internal/cums-internal.openapi.yml";
    private static final String USER = "/internal/users/{username}";
    private static final String USER_BODY = """
            {"id": "anna.meier", "email": "anna.meier@example.com", "firstName": "Anna", "lastName": "Meier",
             "roles": ["SPEAKER"], "active": %s, "additionalEmails": [], "bio": null, "companyId": null,
             "company": null, "hasCognitoAccount": true, "lastLoginAt": null, "profilePictureUrl": null,
             "settings": null, "termsAcceptedAt": null,
             "createdAt": "2025-01-15T10:00:00Z", "updatedAt": "2025-01-15T10:00:00Z"}""";

    @Autowired
    @Qualifier("cumsStatusRestTemplate")
    private RestTemplate restTemplate;

    @Autowired
    private GatewayUserStatusClient client;

    private ContractServer cums;

    @BeforeEach
    void setUp() {
        cums = ContractServer.bindTo(restTemplate, CUMS_INTERNAL);
    }

    @AfterEach
    void tearDown() {
        cums.close();
    }

    @Test
    void should_returnActiveTrue_withoutJwt() {
        cums.expect(GET, USER)
                .andExpect(r -> assertThat(r.getHeaders().containsHeader(HttpHeaders.AUTHORIZATION)).isFalse())
                .andExpect(r -> assertThat(r.getURI().toString()).isEqualTo("http://cums:8085/internal/users/anna.meier"))
                .respondWithExample("user");

        assertThat(client.getActiveStatus("anna.meier")).contains(true);
        cums.verify();
    }

    @Test
    void should_returnActiveFalse_when_deactivated() {
        cums.expect(GET, USER).respondWith(HttpStatus.OK, USER_BODY.formatted("false"));

        assertThat(client.getActiveStatus("anna.meier")).contains(false);
        cums.verify();
    }

    @Test
    void should_returnEmpty_when_userNotProvisioned() {
        cums.expect(GET, USER).respondWithExample(HttpStatus.NOT_FOUND);

        assertThat(client.getActiveStatus("ghost.user")).isEmpty();
        cums.verify();
    }

    @Test
    @DisplayName("17.0b2: the username is one strictly encoded path segment")
    void should_encodeUsername() {
        cums.expect(GET, USER)
                .andExpect(r -> assertThat(r.getURI().getRawPath()).isEqualTo("/internal/users/a%2Fb%3Fc"))
                .respondWithExample("user");

        client.getActiveStatus("a/b?c");

        cums.verify();
    }

    @Test
    void should_throw_when_cumsAnswers5xx() {
        // 503 is not an answer the spec declares (ALB/Service Connect); the request is still validated.
        cums.expect(GET, USER).respondWith(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> client.getActiveStatus("anna.meier")).isInstanceOf(GatewayUserStatusException.class);
        cums.verify();
    }

    @Test
    void should_throw_when_connectionFails() {
        cums.expect(GET, USER).respondWith(withException(new ConnectException("connection refused")));

        assertThatThrownBy(() -> client.getActiveStatus("anna.meier"))
                .isInstanceOf(GatewayUserStatusException.class)
                .hasCauseInstanceOf(ResourceAccessException.class);
        cums.verify();
    }
}
