package ch.batbern.partners.client;

import ch.batbern.partners.client.user.dto.UserResponse;
import ch.batbern.partners.exception.UserServiceException;
import ch.batbern.shared.test.contract.ContractServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restclient.test.autoconfigure.RestClientTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.GET;

/**
 * Contract test of the one public call of {@link UserServiceClient}, {@code GET /users/me}, against
 * {@code users-api} (Story 17.0b1). The {@code /internal/users} calls stay in UserServiceClientTest
 * until 0b2 gives them a spec.
 */
@RestClientTest(components = {
    ch.batbern.partners.client.impl.UserServiceClientImpl.class,
    ch.batbern.partners.config.RestClientConfig.class,
    ch.batbern.partners.config.CacheConfig.class
})
@TestPropertySource(properties = "user-service.base-url=http://company-user-management:8080")
class UserServiceClientContractTest {

    private static final String USERS = "docs/api/users-api.openapi.yml";

    @Autowired
    private UserServiceClient userServiceClient;

    @Autowired
    private RestTemplate restTemplate;

    private ContractServer server;

    @BeforeEach
    void setUp() {
        Jwt jwt = Jwt.withTokenValue("partner-token").header("alg", "RS256").claim("sub", "john.doe").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        server = ContractServer.bindTo(restTemplate, USERS);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        server.close();
    }

    @Test
    void should_returnOwnProfile_withCallersJwt() {
        server.expect(GET, "/api/v1/users/me")
                .andExpect(r -> assertThat(r.getHeaders().getFirst(HttpHeaders.AUTHORIZATION))
                        .isEqualTo("Bearer partner-token"))
                .respondWithExample("basicUser");

        UserResponse me = userServiceClient.getCurrentUserProfile();

        assertThat(me.getId()).isEqualTo("john.doe");
        assertThat(me.getEmail()).isEqualTo("john.doe@example.com");
        server.verify();
    }

    @Test
    void should_throwServiceException_when_unauthorized() {
        server.expect(GET, "/api/v1/users/me").respondWithStatus(HttpStatus.UNAUTHORIZED);

        assertThatThrownBy(() -> userServiceClient.getCurrentUserProfile())
                .isInstanceOf(UserServiceException.class);
        server.verify();
    }
}
