package ch.batbern.gateway.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class CorsConfigurationTest {

    private CorsHandler corsHandler;

    @BeforeEach
    void setUp() {
        corsHandler = new CorsHandler();
    }

    @Test
    @DisplayName("should_allowProductionOrigin_when_requestFromBatbernDomain")
    void should_allowProductionOrigin_when_requestFromBatbernDomain() {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        request.addHeader("Origin", "https://www.batbern.ch");

        // When
        corsHandler.handleCorsRequest(request, response);

        // Then
        assertThat(response.getHeader("Access-Control-Allow-Origin")).isEqualTo("https://www.batbern.ch");
        assertThat(response.getHeader("Access-Control-Allow-Credentials")).isEqualTo("true");
    }

    @Test
    @DisplayName("should_allowStagingOrigin_when_requestFromStagingDomain")
    void should_allowStagingOrigin_when_requestFromStagingDomain() {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        request.addHeader("Origin", "https://staging.batbern.ch");

        // When
        corsHandler.handleCorsRequest(request, response);

        // Then
        assertThat(response.getHeader("Access-Control-Allow-Origin")).isEqualTo("https://staging.batbern.ch");
        assertThat(response.getHeader("Access-Control-Allow-Credentials")).isEqualTo("true");
    }

    @Test
    @DisplayName("should_allowLocalhostOrigin_when_requestFromLocalDevelopment")
    void should_allowLocalhostOrigin_when_requestFromLocalDevelopment() {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        request.addHeader("Origin", "http://localhost:3000");

        // When
        corsHandler.handleCorsRequest(request, response);

        // Then
        assertThat(response.getHeader("Access-Control-Allow-Origin")).isEqualTo("http://localhost:3000");
        assertThat(response.getHeader("Access-Control-Allow-Credentials")).isEqualTo("true");
    }

    @Test
    @DisplayName("should_rejectUnauthorizedOrigin_when_requestFromUnknownDomain")
    void should_rejectUnauthorizedOrigin_when_requestFromUnknownDomain() {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        request.addHeader("Origin", "https://malicious-site.com");

        // When
        corsHandler.handleCorsRequest(request, response);

        // Then
        assertThat(response.getHeader("Access-Control-Allow-Origin")).isNull();
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
    }

    @Test
    @DisplayName("should_setCorrectCorsHeaders_when_preflightRequest")
    void should_setCorrectCorsHeaders_when_preflightRequest() {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        request.setMethod("OPTIONS");
        request.addHeader("Origin", "https://www.batbern.ch");
        request.addHeader("Access-Control-Request-Method", "POST");
        request.addHeader("Access-Control-Request-Headers", "Authorization, Content-Type");

        // When
        corsHandler.handlePreflightRequest(request, response);

        // Then
        assertThat(response.getHeader("Access-Control-Allow-Origin")).isEqualTo("https://www.batbern.ch");
        assertThat(response.getHeader("Access-Control-Allow-Methods")).contains("GET", "POST", "PUT", "DELETE", "OPTIONS");
        assertThat(response.getHeader("Access-Control-Allow-Headers")).contains("Authorization", "Content-Type");
        assertThat(response.getHeader("Access-Control-Max-Age")).isEqualTo("3600");
    }

    @Test
    @DisplayName("should_allowSpecificHeaders_when_requestContainsAllowedHeaders")
    void should_allowSpecificHeaders_when_requestContainsAllowedHeaders() {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        request.setMethod("OPTIONS");
        request.addHeader("Origin", "https://www.batbern.ch");
        request.addHeader("Access-Control-Request-Headers", "Authorization, Content-Type, X-Requested-With");

        // When
        corsHandler.handlePreflightRequest(request, response);

        // Then
        String allowedHeaders = response.getHeader("Access-Control-Allow-Headers");
        assertThat(allowedHeaders).contains("Authorization");
        assertThat(allowedHeaders).contains("Content-Type");
        assertThat(allowedHeaders).contains("X-Requested-With");
        assertThat(allowedHeaders).contains("X-Request-Id");
    }

    @Test
    @DisplayName("should_rejectForbiddenHeaders_when_requestContainsUnsafeHeaders")
    void should_rejectForbiddenHeaders_when_requestContainsUnsafeHeaders() {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        request.setMethod("OPTIONS");
        request.addHeader("Origin", "https://www.batbern.ch");
        request.addHeader("Access-Control-Request-Headers", "X-Dangerous-Header, Authorization");

        // When
        boolean isAllowed = corsHandler.areHeadersAllowed(request);

        // Then
        assertThat(isAllowed).isFalse();
    }

    @Test
    @DisplayName("should_exposeResponseHeaders_when_successfulRequest")
    void should_exposeResponseHeaders_when_successfulRequest() {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        request.addHeader("Origin", "https://www.batbern.ch");

        // When
        corsHandler.handleCorsRequest(request, response);

        // Then
        String exposedHeaders = response.getHeader("Access-Control-Expose-Headers");
        assertThat(exposedHeaders).contains("X-Request-Id");
        assertThat(exposedHeaders).contains("X-Rate-Limit-Remaining");
        assertThat(exposedHeaders).contains("X-Rate-Limit-Reset");
    }

    @Test
    @DisplayName("should_handleVaryHeader_when_corsEnabled")
    void should_handleVaryHeader_when_corsEnabled() {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        request.addHeader("Origin", "https://www.batbern.ch");

        // When
        corsHandler.handleCorsRequest(request, response);

        // Then
        assertThat(response.getHeader("Vary")).contains("Origin");
    }

    @Test
    @DisplayName("should_validateOriginPattern_when_dynamicOriginProvided")
    void should_validateOriginPattern_when_dynamicOriginProvided() {
        // Given
        String[] validOrigins = {
            "https://www.batbern.ch",
            "https://staging.batbern.ch",
            "http://localhost:3000",
            "http://localhost:3001"
        };

        String[] invalidOrigins = {
            "https://evil.com",
            "http://batbern.ch.evil.com",
            "https://www.batbern.ch.evil.com"
        };

        // When / Then
        for (String origin : validOrigins) {
            assertThat(corsHandler.isOriginAllowed(origin)).isTrue();
        }

        for (String origin : invalidOrigins) {
            assertThat(corsHandler.isOriginAllowed(origin)).isFalse();
        }
    }

    @Test
    @DisplayName("should_setSecurityHeaders_when_corsRequestProcessed")
    void should_setSecurityHeaders_when_corsRequestProcessed() {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        request.addHeader("Origin", "https://www.batbern.ch");

        // When
        corsHandler.handleCorsRequest(request, response);

        // Then
        assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getHeader("X-Frame-Options")).isEqualTo("DENY");
        assertThat(response.getHeader("X-XSS-Protection")).isEqualTo("1; mode=block");
    }
}