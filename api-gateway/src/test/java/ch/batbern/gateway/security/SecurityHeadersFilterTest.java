package ch.batbern.gateway.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit Tests for SecurityHeadersFilter
 *
 * TDD RED Phase: These tests are written BEFORE the filter implementation.
 * Tests AC1: Security Headers
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SecurityHeadersFilter Unit Tests")
class SecurityHeadersFilterTest {

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    private SecurityHeadersFilter securityHeadersFilter;

    @BeforeEach
    void setUp() {
        securityHeadersFilter = new SecurityHeadersFilter();
    }

    // ========================================
    // AC1.1: CSP Header Tests
    // ========================================

    @Test
    @DisplayName("should_setCSPHeader_when_filterExecuted")
    void should_setCSPHeader_when_filterExecuted() throws ServletException, IOException {
        // When: Filter is executed
        securityHeadersFilter.doFilter(request, response, filterChain);

        // Then: CSP header should be set
        verify(response).setHeader(eq("Content-Security-Policy"), anyString());
    }

    @Test
    @DisplayName("should_includeDefaultSrcSelf_when_cspHeaderSet")
    void should_includeDefaultSrcSelf_when_cspHeaderSet() throws ServletException, IOException {
        // When: Filter is executed
        securityHeadersFilter.doFilter(request, response, filterChain);

        // Then: CSP should include default-src 'self'
        verify(response).setHeader(eq("Content-Security-Policy"), contains("default-src 'self'"));
    }

    @Test
    @DisplayName("should_restrictScriptSources_when_cspHeaderSet")
    void should_restrictScriptSources_when_cspHeaderSet() throws ServletException, IOException {
        // When: Filter is executed
        securityHeadersFilter.doFilter(request, response, filterChain);

        // Then: CSP should restrict script sources
        verify(response).setHeader(eq("Content-Security-Policy"), contains("script-src"));
    }

    // ========================================
    // AC1.2: HSTS Header Tests
    // ========================================

    @Test
    @DisplayName("should_setHSTSHeader_when_filterExecuted")
    void should_setHSTSHeader_when_filterExecuted() throws ServletException, IOException {
        // When: Filter is executed
        securityHeadersFilter.doFilter(request, response, filterChain);

        // Then: HSTS header should be set
        verify(response).setHeader(eq("Strict-Transport-Security"), anyString());
    }

    @Test
    @DisplayName("should_includeMaxAge_when_hstsHeaderSet")
    void should_includeMaxAge_when_hstsHeaderSet() throws ServletException, IOException {
        // When: Filter is executed
        securityHeadersFilter.doFilter(request, response, filterChain);

        // Then: HSTS should include max-age
        verify(response).setHeader(eq("Strict-Transport-Security"), contains("max-age=31536000"));
    }

    @Test
    @DisplayName("should_includeSubDomains_when_hstsHeaderSet")
    void should_includeSubDomains_when_hstsHeaderSet() throws ServletException, IOException {
        // When: Filter is executed
        securityHeadersFilter.doFilter(request, response, filterChain);

        // Then: HSTS should include subdomains
        verify(response).setHeader(eq("Strict-Transport-Security"), contains("includeSubDomains"));
    }

    // ========================================
    // AC1.3: X-Frame-Options Header Tests
    // ========================================

    @Test
    @DisplayName("should_setXFrameOptionsHeader_when_filterExecuted")
    void should_setXFrameOptionsHeader_when_filterExecuted() throws ServletException, IOException {
        // When: Filter is executed
        securityHeadersFilter.doFilter(request, response, filterChain);

        // Then: X-Frame-Options header should be set to DENY
        verify(response).setHeader("X-Frame-Options", "DENY");
    }

    @Test
    @DisplayName("should_preventClickjacking_when_xFrameOptionsSet")
    void should_preventClickjacking_when_xFrameOptionsSet() throws ServletException, IOException {
        // When: Filter is executed
        securityHeadersFilter.doFilter(request, response, filterChain);

        // Then: X-Frame-Options should prevent page from being framed
        verify(response).setHeader("X-Frame-Options", "DENY");
    }

    // ========================================
    // AC1.4: X-Content-Type-Options Header Tests
    // ========================================

    @Test
    @DisplayName("should_setXContentTypeOptionsHeader_when_filterExecuted")
    void should_setXContentTypeOptionsHeader_when_filterExecuted() throws ServletException, IOException {
        // When: Filter is executed
        securityHeadersFilter.doFilter(request, response, filterChain);

        // Then: X-Content-Type-Options header should be set
        verify(response).setHeader("X-Content-Type-Options", "nosniff");
    }

    @Test
    @DisplayName("should_preventMimeSniffing_when_xContentTypeOptionsSet")
    void should_preventMimeSniffing_when_xContentTypeOptionsSet() throws ServletException, IOException {
        // When: Filter is executed
        securityHeadersFilter.doFilter(request, response, filterChain);

        // Then: nosniff directive should prevent MIME type sniffing
        verify(response).setHeader("X-Content-Type-Options", "nosniff");
    }

    // ========================================
    // AC1.5: Additional Security Headers
    // ========================================

    @Test
    @DisplayName("should_setXXSSProtectionHeader_when_filterExecuted")
    void should_setXXSSProtectionHeader_when_filterExecuted() throws ServletException, IOException {
        // When: Filter is executed
        securityHeadersFilter.doFilter(request, response, filterChain);

        // Then: X-XSS-Protection header should be set
        verify(response).setHeader(eq("X-XSS-Protection"), anyString());
    }

    @Test
    @DisplayName("should_setReferrerPolicyHeader_when_filterExecuted")
    void should_setReferrerPolicyHeader_when_filterExecuted() throws ServletException, IOException {
        // When: Filter is executed
        securityHeadersFilter.doFilter(request, response, filterChain);

        // Then: Referrer-Policy header should be set
        verify(response).setHeader(eq("Referrer-Policy"), anyString());
    }

    @Test
    @DisplayName("should_setPermissionsPolicyHeader_when_filterExecuted")
    void should_setPermissionsPolicyHeader_when_filterExecuted() throws ServletException, IOException {
        // When: Filter is executed
        securityHeadersFilter.doFilter(request, response, filterChain);

        // Then: Permissions-Policy header should be set
        verify(response).setHeader(eq("Permissions-Policy"), anyString());
    }

    // ========================================
    // Filter Chain Tests
    // ========================================

    @Test
    @DisplayName("should_continueFilterChain_when_headersSet")
    void should_continueFilterChain_when_headersSet() throws ServletException, IOException {
        // When: Filter is executed
        securityHeadersFilter.doFilter(request, response, filterChain);

        // Then: Filter chain should continue
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("should_setHeadersBeforeChainContinues_when_filterExecuted")
    void should_setHeadersBeforeChainContinues_when_filterExecuted() throws ServletException, IOException {
        // Given: InOrder verification to ensure headers are set before chain continues
        var inOrder = inOrder(response, filterChain);

        // When: Filter is executed
        securityHeadersFilter.doFilter(request, response, filterChain);

        // Then: Headers should be set before filter chain continues
        inOrder.verify(response, atLeastOnce()).setHeader(anyString(), anyString());
        inOrder.verify(filterChain).doFilter(request, response);
    }

    // ========================================
    // Helper Method
    // ========================================

    /**
     * Custom matcher to verify string contains substring
     */
    private static String contains(String substring) {
        return argThat(str -> str != null && str.contains(substring));
    }
}
