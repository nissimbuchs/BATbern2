package ch.batbern.gateway.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class SecurityHeadersTest {

    private SecurityHeadersHandler securityHeadersHandler;

    @BeforeEach
    void setUp() {
        securityHeadersHandler = new SecurityHeadersHandler();
    }

    @Test
    @DisplayName("should_setHSTSHeader_when_httpsRequest")
    void should_setHSTSHeader_when_httpsRequest() {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        request.setScheme("https");
        request.setServerName("api.batbern.ch");

        // When
        securityHeadersHandler.addSecurityHeaders(request, response);

        // Then
        String hstsHeader = response.getHeader("Strict-Transport-Security");
        assertThat(hstsHeader).isEqualTo("max-age=31536000; includeSubDomains; preload");
    }

    @Test
    @DisplayName("should_notSetHSTSHeader_when_httpRequest")
    void should_notSetHSTSHeader_when_httpRequest() {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        request.setScheme("http");
        request.setServerName("localhost");

        // When
        securityHeadersHandler.addSecurityHeaders(request, response);

        // Then
        assertThat(response.getHeader("Strict-Transport-Security")).isNull();
    }

    @Test
    @DisplayName("should_setContentSecurityPolicy_when_anyRequest")
    void should_setContentSecurityPolicy_when_anyRequest() {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        request.setServerName("api.batbern.ch"); // Set production server name

        // When
        securityHeadersHandler.addSecurityHeaders(request, response);

        // Then
        String cspHeader = response.getHeader("Content-Security-Policy");
        assertThat(cspHeader).contains("default-src 'self'");
        assertThat(cspHeader).contains("script-src 'self' 'unsafe-inline'");
        assertThat(cspHeader).contains("style-src 'self' 'unsafe-inline'");
        assertThat(cspHeader).contains("img-src 'self' data: https:");
        assertThat(cspHeader).contains("connect-src 'self' https://api.batbern.ch");
    }

    @Test
    @DisplayName("should_setXFrameOptions_when_anyRequest")
    void should_setXFrameOptions_when_anyRequest() {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        // When
        securityHeadersHandler.addSecurityHeaders(request, response);

        // Then
        assertThat(response.getHeader("X-Frame-Options")).isEqualTo("DENY");
    }

    @Test
    @DisplayName("should_setXContentTypeOptions_when_anyRequest")
    void should_setXContentTypeOptions_when_anyRequest() {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        // When
        securityHeadersHandler.addSecurityHeaders(request, response);

        // Then
        assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
    }

    @Test
    @DisplayName("should_setXXSSProtection_when_anyRequest")
    void should_setXXSSProtection_when_anyRequest() {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        // When
        securityHeadersHandler.addSecurityHeaders(request, response);

        // Then
        assertThat(response.getHeader("X-XSS-Protection")).isEqualTo("1; mode=block");
    }

    @Test
    @DisplayName("should_setReferrerPolicy_when_anyRequest")
    void should_setReferrerPolicy_when_anyRequest() {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        // When
        securityHeadersHandler.addSecurityHeaders(request, response);

        // Then
        assertThat(response.getHeader("Referrer-Policy")).isEqualTo("strict-origin-when-cross-origin");
    }

    @Test
    @DisplayName("should_setPermissionsPolicy_when_anyRequest")
    void should_setPermissionsPolicy_when_anyRequest() {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        // When
        securityHeadersHandler.addSecurityHeaders(request, response);

        // Then
        String permissionsPolicy = response.getHeader("Permissions-Policy");
        assertThat(permissionsPolicy).contains("geolocation=()");
        assertThat(permissionsPolicy).contains("microphone=()");
        assertThat(permissionsPolicy).contains("camera=()");
    }

    @Test
    @DisplayName("should_removeServerHeader_when_anyRequest")
    void should_removeServerHeader_when_anyRequest() {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Pre-set a server header
        response.setHeader("Server", "Apache/2.4.41");

        // When
        securityHeadersHandler.addSecurityHeaders(request, response);

        // Then
        assertThat(response.getHeader("Server")).isNull();
    }

    @Test
    @DisplayName("should_setCacheControlHeaders_when_sensitiveEndpoint")
    void should_setCacheControlHeaders_when_sensitiveEndpoint() {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        request.setRequestURI("/api/events/admin");

        // When
        securityHeadersHandler.addSecurityHeaders(request, response);

        // Then
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-cache, no-store, must-revalidate");
        assertThat(response.getHeader("Pragma")).isEqualTo("no-cache");
        assertThat(response.getHeader("Expires")).isEqualTo("0");
    }

    @Test
    @DisplayName("should_allowCachingForPublicContent_when_contentEndpoint")
    void should_allowCachingForPublicContent_when_contentEndpoint() {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        request.setRequestURI("/api/content/search");

        // When
        securityHeadersHandler.addSecurityHeaders(request, response);

        // Then
        String cacheControl = response.getHeader("Cache-Control");
        assertThat(cacheControl).contains("public");
        assertThat(cacheControl).contains("max-age=300"); // 5 minutes
    }

    @Test
    @DisplayName("should_setSecurityHeadersInCorrectOrder_when_anyRequest")
    void should_setSecurityHeadersInCorrectOrder_when_anyRequest() {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        request.setScheme("https");

        // When
        securityHeadersHandler.addSecurityHeaders(request, response);

        // Then - Verify all security headers are present
        assertThat(response.getHeader("Strict-Transport-Security")).isNotNull();
        assertThat(response.getHeader("Content-Security-Policy")).isNotNull();
        assertThat(response.getHeader("X-Frame-Options")).isNotNull();
        assertThat(response.getHeader("X-Content-Type-Options")).isNotNull();
        assertThat(response.getHeader("X-XSS-Protection")).isNotNull();
        assertThat(response.getHeader("Referrer-Policy")).isNotNull();
        assertThat(response.getHeader("Permissions-Policy")).isNotNull();
    }

    @Test
    @DisplayName("should_customizeCSPForEnvironment_when_developmentMode")
    void should_customizeCSPForEnvironment_when_developmentMode() {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        request.setServerName("localhost");

        // When
        securityHeadersHandler.addSecurityHeaders(request, response);

        // Then
        String cspHeader = response.getHeader("Content-Security-Policy");
        assertThat(cspHeader).contains("connect-src 'self' http://localhost:*");
    }
}