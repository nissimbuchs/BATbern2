package ch.batbern.partners.client;

import ch.batbern.partners.client.user.dto.UserResponse;
import ch.batbern.partners.exception.UserNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.client.MockRestServiceServer;

import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * RED Phase tests for UserServiceClient.
 *
 * Tests verify:
 * - HTTP calls to User Service API for enriching contacts
 * - 404 error handling (user not found)
 * - JWT token propagation
 * - Response caching
 *
 * CRITICAL: These tests are written BEFORE implementation (TDD RED Phase).
 */
@RestClientTest(components = {
    ch.batbern.partners.client.impl.UserServiceClientImpl.class,
    ch.batbern.partners.config.RestClientConfig.class,
    ch.batbern.partners.config.CacheConfig.class
})
@TestPropertySource(properties = {
    "user-service.base-url=http://company-user-management:8080"
})
class UserServiceClientTest {

    @Autowired
    private UserServiceClient userServiceClient;

    @Autowired
    private MockRestServiceServer mockServer;

    @MockitoBean
    private SecurityContext securityContext;

    @MockitoBean
    private Authentication authentication;

    @Autowired(required = false)
    private CacheManager cacheManager;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.setContext(securityContext);

        // Clear cache before each test
        if (cacheManager != null) {
            Objects.requireNonNull(cacheManager.getCache("userApiCache")).clear();
            var byCompanyCache = cacheManager.getCache("usersByCompanyRoleCache");
            if (byCompanyCache != null) { byCompanyCache.clear(); }
            var byRoleCache = cacheManager.getCache("usersByRoleCache");
            if (byRoleCache != null) { byRoleCache.clear(); }
        }
    }

    @Test
    void should_callUserServiceAPI_when_enrichingContacts() {
        // Given
        String username = "john.doe@google.com";
        String expectedUrl = "http://company-user-management:8080/api/v1/users/" + username;

        mockServer.expect(requestTo(expectedUrl))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                            {
                                "id": "john.doe@google.com",
                                "email": "john.doe@google.com",
                                "firstName": "John",
                                "lastName": "Doe",
                                "profilePictureUrl": "https://cdn.batbern.ch/profiles/john.doe.jpg"
                            }
                            """));

        // When
        UserResponse user = userServiceClient.getUserByUsername(username);

        // Then
        assertThat(user)
                .as("Should return user profile data from API")
                .isNotNull();
        assertThat(user.getId())  // Generated DTO uses getId() for username
                .isEqualTo("john.doe@google.com");
        assertThat(user.getFirstName())
                .isEqualTo("John");
        assertThat(user.getLastName())
                .isEqualTo("Doe");
        assertThat(user.getEmail())
                .isEqualTo("john.doe@google.com");
        assertThat(user.getProfilePictureUrl())
                .isEqualTo(java.net.URI.create("https://cdn.batbern.ch/profiles/john.doe.jpg"));

        mockServer.verify();
    }

    @Test
    void should_return404_when_userNotFound() {
        // Given
        String username = "nonexistent@example.com";
        String expectedUrl = "http://company-user-management:8080/api/v1/users/" + username;

        mockServer.expect(requestTo(expectedUrl))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withResourceNotFound());

        // When / Then
        assertThatThrownBy(() -> userServiceClient.getUserByUsername(username))
                .as("Should throw UserNotFoundException when API returns 404")
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining(username);

        mockServer.verify();
    }

    @Test
    void should_cacheResponse_when_httpCallSucceeds() {
        // Given
        String username = "jane.smith@microsoft.com";
        String expectedUrl = "http://company-user-management:8080/api/v1/users/" + username;

        mockServer.expect(requestTo(expectedUrl))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                            {
                                "username": "jane.smith@microsoft.com",
                                "email": "jane.smith@microsoft.com",
                                "firstName": "Jane",
                                "lastName": "Smith",
                                "profilePictureUrl": "https://cdn.batbern.ch/profiles/jane.smith.jpg"
                            }
                            """));

        // When - First call hits API
        UserResponse firstCall = userServiceClient.getUserByUsername(username);

        // Then - Second call should use cache (no HTTP request expected)
        UserResponse secondCall = userServiceClient.getUserByUsername(username);

        assertThat(firstCall)
                .as("First call should return user data")
                .isNotNull();
        assertThat(secondCall)
                .as("Second call should return cached data")
                .isNotNull()
                .usingRecursiveComparison()
                .isEqualTo(firstCall);

        // Verify only ONE HTTP request was made (second was cached)
        mockServer.verify();
    }

    @Test
    void should_propagateJwtToken_when_callingUserService() {
        // Given
        String username = "test.user@example.com";
        String expectedUrl = "http://company-user-management:8080/api/v1/users/" + username;
        String jwtToken = "test-jwt-token-xyz789";

        // Mock JWT in SecurityContext
        Jwt mockJwt = Jwt.withTokenValue(jwtToken)
                .header("alg", "RS256")
                .claim("sub", "organizer@batbern.ch")
                .build();

        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(mockJwt);

        mockServer.expect(requestTo(expectedUrl))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken))
                .andRespond(withSuccess()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                            {
                                "username": "test.user@example.com",
                                "email": "test.user@example.com",
                                "firstName": "Test",
                                "lastName": "User",
                                "profilePictureUrl": null
                            }
                            """));

        // When
        UserResponse user = userServiceClient.getUserByUsername(username);

        // Then
        assertThat(user)
                .as("Should successfully call API with JWT token")
                .isNotNull();

        mockServer.verify();
    }

    @Test
    void should_callByCompanyEndpoint_withoutAuthHeader_when_getUsersByCompanyAndRole() {
        // Given
        String expectedUrl = "http://company-user-management:8080/api/v1/users/by-company?company=sbb&role=PARTNER&limit=100";

        mockServer.expect(requestTo(expectedUrl))
                .andExpect(method(HttpMethod.GET))
                // No JWT header expected — this is a VPC-internal service-to-service call
                .andExpect(request -> assertThat(request.getHeaders().containsKey(HttpHeaders.AUTHORIZATION))
                        .as("getUsersByCompanyAndRole must NOT forward a JWT")
                        .isFalse())
                .andRespond(withSuccess()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                            {
                                "data": [
                                    {
                                        "id": "partner.user",
                                        "email": "partner@sbb.ch",
                                        "firstName": "Partner",
                                        "lastName": "User"
                                    }
                                ],
                                "pagination": {
                                    "page": 1, "limit": 100,
                                    "totalItems": 1, "totalPages": 1,
                                    "hasNext": false, "hasPrev": false
                                }
                            }
                            """));

        // When
        var users = userServiceClient.getUsersByCompanyAndRole("sbb", "PARTNER");

        // Then
        assertThat(users).hasSize(1);
        assertThat(users.get(0).getEmail()).isEqualTo("partner@sbb.ch");
        mockServer.verify();
    }

    @Test
    void should_returnEmptyList_when_byCompanyEndpointReturnsEmptyData() {
        // Given
        String expectedUrl = "http://company-user-management:8080/api/v1/users/by-company?company=empty-co&role=PARTNER&limit=100";

        mockServer.expect(requestTo(expectedUrl))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                            {
                                "data": [],
                                "pagination": {
                                    "page": 1, "limit": 100,
                                    "totalItems": 0, "totalPages": 0,
                                    "hasNext": false, "hasPrev": false
                                }
                            }
                            """));

        // When
        var users = userServiceClient.getUsersByCompanyAndRole("empty-co", "PARTNER");

        // Then
        assertThat(users).isEmpty();
        mockServer.verify();
    }
}
