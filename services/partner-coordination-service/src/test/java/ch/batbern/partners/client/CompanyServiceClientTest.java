package ch.batbern.partners.client;

import ch.batbern.partners.client.company.dto.CompanyResponse;
import ch.batbern.partners.exception.CompanyNotFoundException;
import ch.batbern.partners.exception.CompanyServiceException;
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
import org.springframework.web.client.ResourceAccessException;

import java.util.Objects;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/**
 * RED Phase tests for CompanyServiceClient.
 *
 * Tests verify:
 * - HTTP calls to Company Service API
 * - 404 error handling (company not found)
 * - 5xx error handling (service unavailable)
 * - Network error handling (ResourceAccessException)
 * - JWT token propagation
 * - Response caching
 *
 * CRITICAL: These tests are written BEFORE implementation (TDD RED Phase).
 */
@RestClientTest(components = {
    ch.batbern.partners.client.impl.CompanyServiceClientImpl.class,
    ch.batbern.partners.config.RestClientConfig.class,
    ch.batbern.partners.config.CacheConfig.class
})
@TestPropertySource(properties = {
    "company-service.base-url=http://company-user-management:8080"
})
class CompanyServiceClientTest {

    @Autowired
    private CompanyServiceClient companyServiceClient;

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
            Objects.requireNonNull(cacheManager.getCache("companyApiCache")).clear();
        }
    }

    @Test
    void should_callCompanyServiceAPI_when_validatingCompanyName() {
        // Given
        String companyName = "GoogleZH";
        String expectedUrl = "http://company-user-management:8080/api/v1/companies/" + companyName;

        mockServer.expect(requestTo(expectedUrl))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                            {
                                "name": "GoogleZH",
                                "displayName": "Google Schweiz GmbH",
                                "industry": "Technology",
                                "swissUID": "CHE-123.456.789",
                                "isVerified": true
                            }
                            """));

        // When
        CompanyResponse company = companyServiceClient.getCompanyByName(companyName);

        // Then
        assertThat(company)
                .as("Should return company data from API")
                .isNotNull();
        assertThat(company.getName())  // Generated DTO uses getName()
                .isEqualTo("GoogleZH");
        assertThat(company.getDisplayName())  // Generated DTO uses getDisplayName()
                .isEqualTo("Google Schweiz GmbH");

        mockServer.verify();
    }

    @Test
    void should_return404_when_companyNotFound() {
        // Given
        String companyName = "NonExistent";
        String expectedUrl = "http://company-user-management:8080/api/v1/companies/" + companyName;

        mockServer.expect(requestTo(expectedUrl))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withResourceNotFound());

        // When / Then
        assertThatThrownBy(() -> companyServiceClient.getCompanyByName(companyName))
                .as("Should throw CompanyNotFoundException when API returns 404")
                .isInstanceOf(CompanyNotFoundException.class)
                .hasMessageContaining(companyName);

        mockServer.verify();
    }

    @Test
    void should_cacheResponse_when_httpCallSucceeds() {
        // Given
        String companyName = "MicrosoftZH";
        String expectedUrl = "http://company-user-management:8080/api/v1/companies/" + companyName;

        mockServer.expect(requestTo(expectedUrl))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                            {
                                "companyName": "MicrosoftZH",
                                "legalName": "Microsoft Schweiz GmbH",
                                "industry": "Technology",
                                "size": "LARGE",
                                "city": "Zurich"
                            }
                            """));

        // When - First call hits API
        CompanyResponse firstCall = companyServiceClient.getCompanyByName(companyName);

        // Then - Second call should use cache (no HTTP request expected)
        CompanyResponse secondCall = companyServiceClient.getCompanyByName(companyName);

        assertThat(firstCall)
                .as("First call should return company data")
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
    void should_propagateJwtToken_when_callingCompanyService() {
        // Given
        String companyName = "TestCompany";
        String expectedUrl = "http://company-user-management:8080/api/v1/companies/" + companyName;
        String jwtToken = "test-jwt-token-abc123";

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
                                "companyName": "TestCompany",
                                "legalName": "Test Company AG",
                                "industry": "Technology",
                                "size": "MEDIUM",
                                "city": "Bern"
                            }
                            """));

        // When
        CompanyResponse company = companyServiceClient.getCompanyByName(companyName);

        // Then
        assertThat(company)
                .as("Should successfully call API with JWT token")
                .isNotNull();

        mockServer.verify();
    }

    @Test
    void should_handle5xxErrors_when_serviceUnavailable() {
        // Given
        String companyName = "GoogleZH";
        String expectedUrl = "http://company-user-management:8080/api/v1/companies/" + companyName;

        mockServer.expect(requestTo(expectedUrl))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withServerError());

        // When / Then
        assertThatThrownBy(() -> companyServiceClient.getCompanyByName(companyName))
                .as("Should throw CompanyServiceException when API returns 5xx")
                .isInstanceOf(CompanyServiceException.class)
                .hasMessageContaining("Company Service error");

        mockServer.verify();
    }

    @Test
    void should_handleNetworkErrors_when_connectionFails() {
        // Given
        String companyName = "GoogleZH";
        String expectedUrl = "http://company-user-management:8080/api/v1/companies/" + companyName;

        mockServer.expect(requestTo(expectedUrl))
                .andExpect(method(HttpMethod.GET))
                .andRespond(request -> {
                    throw new ResourceAccessException("Connection refused");
                });

        // When / Then
        assertThatThrownBy(() -> companyServiceClient.getCompanyByName(companyName))
                .as("Should throw CompanyServiceException when network error occurs")
                .isInstanceOf(CompanyServiceException.class)
                .hasMessageContaining("Failed to connect");

        mockServer.verify();
    }
}
