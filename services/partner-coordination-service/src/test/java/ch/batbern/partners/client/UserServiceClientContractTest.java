package ch.batbern.partners.client;

import ch.batbern.partners.client.user.dto.UserResponse;
import ch.batbern.partners.exception.UserNotFoundException;
import ch.batbern.partners.exception.UserServiceException;
import ch.batbern.shared.test.contract.ContractServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restclient.test.autoconfigure.RestClientTest;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.GET;

/**
 * Contract test of every call of {@link UserServiceClient}: the {@code /internal/users} calls against
 * {@code docs/api/internal/cums-internal.openapi.yml} (17.0b2, replaces the spec-less
 * {@code UserServiceClientTest}). The real client on the real {@code RestTemplate}; every request
 * validated against the spec. Since Epic 17 6b the client calls no public operation (the former
 * {@code GET /users/me} is gone: the caller's own profile is {@code getUserByUsername}), and every
 * call carries the signed-in user's JWT when there is one and no {@code Authorization} otherwise
 * (ADR-016 decision 3, {@code S2sHeaders}).
 */
@RestClientTest(components = {
    ch.batbern.partners.client.impl.UserServiceClientImpl.class,
    ch.batbern.partners.config.RestClientConfig.class,
    ch.batbern.partners.config.CacheConfig.class
})
@TestPropertySource(properties = "user-service.base-url=http://company-user-management:8080")
class UserServiceClientContractTest {

    private static final String CUMS_INTERNAL = "docs/api/internal/cums-internal.openapi.yml";

    @Autowired
    private UserServiceClient userServiceClient;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private CacheManager cacheManager;

    private ContractServer server;

    @BeforeEach
    void setUp() {
        cacheManager.getCacheNames().forEach(n -> Objects.requireNonNull(cacheManager.getCache(n)).clear());
        Jwt jwt = Jwt.withTokenValue("partner-token").header("alg", "RS256").claim("sub", "john.doe")
                .expiresAt(Instant.now().plusSeconds(3600)).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        server = ContractServer.bindTo(restTemplate, CUMS_INTERNAL);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        server.close();
    }

    /** A signed-in user's JWT is forwarded (attribution); the answer does not depend on it. */
    private static void userJwt(ClientHttpRequest request) {
        assertThat(request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer partner-token");
    }

    /** Without a user (scheduler, async thread, test) no Authorization header goes out. */
    private static void noJwt(ClientHttpRequest request) {
        assertThat(request.getHeaders().containsHeader(HttpHeaders.AUTHORIZATION)).isFalse();
    }

    // ------------------------------------------------------------------ GET /internal/users/{username}

    @Test
    void should_readUser_withUsersJwt_andCacheIt() {
        server.expect(GET, "/internal/users/{username}").andExpect(UserServiceClientContractTest::userJwt)
                .respondWithExample("user");

        UserResponse first = userServiceClient.getUserByUsername("anna.meier");
        UserResponse second = userServiceClient.getUserByUsername("anna.meier");

        assertThat(first.getEmail()).isEqualTo("anna.meier@example.com");
        assertThat(second).usingRecursiveComparison().isEqualTo(first);
        server.verify(); // exactly one request: the second read came from the cache
    }

    @Test
    void should_readUser_withoutJwt_when_noUserSignedIn() {
        SecurityContextHolder.clearContext();
        server.expect(GET, "/internal/users/{username}").andExpect(UserServiceClientContractTest::noJwt)
                .respondWithExample("user");

        assertThat(userServiceClient.getUserByUsername("anna.meier").getEmail()).isEqualTo("anna.meier@example.com");
        server.verify();
    }

    @Test
    @DisplayName("a username is one strictly encoded path segment")
    void should_encodeUsername_asOnePathSegment() {
        server.expect(GET, "/internal/users/{username}")
                .andExpect(r -> assertThat(r.getURI().getRawPath()).isEqualTo("/internal/users/jean-luc.o%27brien%20x"))
                .respondWithExample("user");

        userServiceClient.getUserByUsername("jean-luc.o'brien x");

        server.verify();
    }

    @Test
    void should_throwUserNotFound_when_cumsAnswers404() {
        server.expect(GET, "/internal/users/{username}").respondWithExample(HttpStatus.NOT_FOUND);

        assertThatThrownBy(() -> userServiceClient.getUserByUsername("ghost.user"))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining("ghost.user");
        server.verify();
    }

    // ------------------------------------------------------------------ GET /internal/users (paged)

    /** A page of {@code count} partner users numbered from {@code from}, with hasNext as given. */
    private static String page(int page, int from, int count, boolean hasNext) {
        String users = IntStream.range(from, from + count).mapToObj(i -> """
                {"id": "partner.user", "hasCognitoAccount": true, "email": "partner%d@example.com",
                 "firstName": "P", "lastName": "U", "bio": null, "companyId": null, "roles": ["PARTNER"],
                 "profilePictureUrl": null, "active": true, "createdAt": "2025-01-15T10:00:00Z",
                 "updatedAt": "2025-01-15T10:00:00Z", "lastLoginAt": null, "termsAcceptedAt": null,
                 "company": null, "settings": null, "additionalEmails": []}"""
                .formatted(i)).collect(Collectors.joining(","));
        return """
                {"data": [%s], "pagination": {"page": %d, "limit": 100, "totalItems": 150, "totalPages": 2,
                 "hasNext": %b, "hasPrev": %b}}""".formatted(users, page, hasNext, page > 1);
    }

    @Test
    @DisplayName("17.0b2: more than 100 partners of a role are all read, page by page")
    void should_readEveryPage_when_roleHasMoreThan100Users() {
        server.expect(GET, "/internal/users").withQueryParam("role", "PARTNER").withQueryParam("page", "1")
                .withQueryParam("limit", "100").andExpect(UserServiceClientContractTest::userJwt)
                .respondWith(HttpStatus.OK, page(1, 0, 100, true));
        server.expect(GET, "/internal/users").withQueryParam("role", "PARTNER").withQueryParam("page", "2")
                .andExpect(UserServiceClientContractTest::userJwt)
                .respondWith(HttpStatus.OK, page(2, 100, 50, false));

        List<UserResponse> users = userServiceClient.getUsersByRole("PARTNER");

        assertThat(users).hasSize(150);
        assertThat(users.get(149).getEmail()).isEqualTo("partner149@example.com");
        server.verify();
    }

    @Test
    @DisplayName("17.0b2: a company's contacts are read page by page, the company name strictly encoded")
    void should_readEveryPage_ofCompanyContacts() {
        SecurityContextHolder.clearContext(); // without a user (scheduler): no Authorization on any page
        server.expect(GET, "/internal/users").withQueryParam("company", "Meier+Partner & Co")
                .withQueryParam("role", "PARTNER").withQueryParam("page", "1")
                .andExpect(r -> assertThat(r.getURI().getRawQuery()).contains("company=Meier%2BPartner%20%26%20Co"))
                .andExpect(UserServiceClientContractTest::noJwt)
                .respondWith(HttpStatus.OK, page(1, 0, 100, true));
        server.expect(GET, "/internal/users").withQueryParam("company", "Meier+Partner & Co")
                .withQueryParam("page", "2").andExpect(UserServiceClientContractTest::noJwt).respondWithExample();

        List<UserResponse> users = userServiceClient.getUsersByCompanyAndRole("Meier+Partner & Co", "PARTNER");

        assertThat(users).hasSize(101);
        assertThat(users.get(100).getId()).isEqualTo("anna.meier");
        server.verify();
    }

    @Test
    void should_returnEmptyList_when_noUserMatches() {
        server.expect(GET, "/internal/users").respondWith(HttpStatus.OK, """
                {"data": [], "pagination": {"page": 1, "limit": 100, "totalItems": 0, "totalPages": 0,
                 "hasNext": false, "hasPrev": false}}""");

        assertThat(userServiceClient.getUsersByCompanyAndRole("empty-co", "PARTNER")).isEmpty();
        server.verify();
    }

    @Test
    void should_throwServiceException_when_roleRejected() {
        server.expect(GET, "/internal/users").respondWithStatus(HttpStatus.BAD_REQUEST);

        assertThatThrownBy(() -> userServiceClient.getUsersByRole("ORGANIZER"))
                .isInstanceOf(UserServiceException.class);
        server.verify();
    }
}
