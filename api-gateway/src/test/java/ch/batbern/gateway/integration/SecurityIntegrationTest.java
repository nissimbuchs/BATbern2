package ch.batbern.gateway.integration;

import ch.batbern.gateway.config.TestRateLimitConfig;
import ch.batbern.gateway.config.TestSecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * E2E Integration Tests for Security Features
 *
 * TDD RED Phase: These tests are written first and should FAIL initially.
 * Tests cover: Security Headers (AC1), Rate Limiting (AC6); the GDPR stubs are gone (#1215)
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestRateLimitConfig.class, TestSecurityConfig.class})
@DisplayName("Security Features E2E Integration Tests")
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    // ========================================
    // AC1: Security Headers Tests (E2E)
    // ========================================

    @Test
    @DisplayName("should_includeAllSecurityHeaders_when_apiGatewayResponds")
    void should_includeAllSecurityHeaders_when_apiGatewayResponds() throws Exception {
        // Given: A request to any API endpoint

        // When: Request is made
        MvcResult result = mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andReturn();

        // Then: All security headers should be present
        assertThat(result.getResponse().getHeader("Content-Security-Policy"))
                .as("CSP header must be present")
                .isNotNull()
                .contains("default-src 'self'");

        assertThat(result.getResponse().getHeader("Strict-Transport-Security"))
                .as("HSTS header must be present")
                .isNotNull()
                .contains("max-age=31536000");

        assertThat(result.getResponse().getHeader("X-Frame-Options"))
                .as("X-Frame-Options header must be present")
                .isEqualTo("DENY");

        assertThat(result.getResponse().getHeader("X-Content-Type-Options"))
                .as("X-Content-Type-Options header must be present")
                .isEqualTo("nosniff");

        assertThat(result.getResponse().getHeader("X-XSS-Protection"))
                .as("X-XSS-Protection header must be present")
                .isNotNull()
                .contains("1; mode=block");

        assertThat(result.getResponse().getHeader("Referrer-Policy"))
                .as("Referrer-Policy header must be present")
                .isNotNull()
                .contains("strict-origin");
    }

    @Test
    @DisplayName("should_includeCSPHeader_when_responseReturned")
    void should_includeCSPHeader_when_responseReturned() throws Exception {
        // When: Making a request
        MvcResult result = mockMvc.perform(get("/api/v1/health"))
                .andReturn();

        // Then: CSP header should be present and properly configured
        String cspHeader = result.getResponse().getHeader("Content-Security-Policy");
        assertThat(cspHeader).isNotNull();
        assertThat(cspHeader).contains("default-src 'self'");
        assertThat(cspHeader).contains("script-src");
        assertThat(cspHeader).contains("style-src");
    }

    @Test
    @DisplayName("should_includeHSTSHeader_when_responseReturned")
    void should_includeHSTSHeader_when_responseReturned() throws Exception {
        // When: Making a request
        MvcResult result = mockMvc.perform(get("/api/v1/health"))
                .andReturn();

        // Then: HSTS header should enforce HTTPS
        String hstsHeader = result.getResponse().getHeader("Strict-Transport-Security");
        assertThat(hstsHeader).isNotNull();
        assertThat(hstsHeader).contains("max-age=31536000");
        assertThat(hstsHeader).contains("includeSubDomains");
    }

    @Test
    @DisplayName("should_includeXFrameOptionsHeader_when_responseReturned")
    void should_includeXFrameOptionsHeader_when_responseReturned() throws Exception {
        // When: Making a request
        MvcResult result = mockMvc.perform(get("/api/v1/health"))
                .andReturn();

        // Then: X-Frame-Options should prevent clickjacking
        assertThat(result.getResponse().getHeader("X-Frame-Options"))
                .isEqualTo("DENY");
    }

    @Test
    @DisplayName("should_includeXContentTypeOptionsHeader_when_responseReturned")
    void should_includeXContentTypeOptionsHeader_when_responseReturned() throws Exception {
        // When: Making a request
        MvcResult result = mockMvc.perform(get("/api/v1/health"))
                .andReturn();

        // Then: X-Content-Type-Options should prevent MIME sniffing
        assertThat(result.getResponse().getHeader("X-Content-Type-Options"))
                .isEqualTo("nosniff");
    }

    // ========================================
    // AC6: Rate Limiting Tests (E2E)
    // ========================================

    @Test
    @DisplayName("should_return429_when_rateLimitExceeded")
    void should_return429_when_rateLimitExceeded() throws Exception {
        // Given: Rate limit is configured (default 60 req/min)
        String endpoint = "/api/v1/test-rate-limit";

        // When: Making requests beyond the limit (simulate 61 requests)
        for (int i = 0; i < 61; i++) {
            mockMvc.perform(get(endpoint))
                    .andReturn();
        }

        // Then: 61st request should return 429 Too Many Requests
        mockMvc.perform(get(endpoint))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("should_allowRequests_when_withinRateLimit")
    void should_allowRequests_when_withinRateLimit() throws Exception {
        // Given: Rate limit allows 60 requests per minute
        String endpoint = "/api/v1/test-rate-limit-allowed";

        // When: Making requests within the limit (10 requests)
        for (int i = 0; i < 10; i++) {
            // Then: All requests should succeed
            mockMvc.perform(get(endpoint))
                    .andExpect(status().isOk());
        }
    }

    @Test
    @DisplayName("should_includeRateLimitHeaders_when_requestProcessed")
    void should_includeRateLimitHeaders_when_requestProcessed() throws Exception {
        // When: Making a request
        MvcResult result = mockMvc.perform(get("/api/v1/test-rate-limit-headers"))
                .andReturn();

        // Then: Rate limit headers should be present
        assertThat(result.getResponse().getHeader("X-RateLimit-Limit"))
                .as("Rate limit header must indicate maximum allowed requests")
                .isNotNull();

        assertThat(result.getResponse().getHeader("X-RateLimit-Remaining"))
                .as("Remaining requests header must be present")
                .isNotNull();

        assertThat(result.getResponse().getHeader("X-RateLimit-Reset"))
                .as("Reset timestamp header must be present")
                .isNotNull();
    }

    // ========================================
    // #1215: no GDPR stub endpoints
    // ========================================

    @Test
    @DisplayName("should_notServeGdprStubs_when_exportOrDeleteRequested")
    void should_notServeGdprStubs_when_exportOrDeleteRequested() throws Exception {
        // The former /gdpr/export returned sample data and /gdpr/delete reported a deletion that never
        // happened (#1215). Nothing answers these paths any more; erasure is handled by hand (#1120).
        mockMvc.perform(get("/api/v1/gdpr/export").header("Authorization", "Bearer mock-jwt-token"))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/gdpr/delete").header("Authorization", "Bearer mock-jwt-token"))
                .andExpect(status().isNotFound());
    }

    // ========================================
    // CORP header (ZAP fix P5)
    // ========================================

    @Test
    @DisplayName("should_includeCORPHeader_when_responseReturned")
    void should_includeCORPHeader_when_responseReturned() throws Exception {
        // When: Making a request
        MvcResult result = mockMvc.perform(get("/api/v1/health"))
                .andReturn();

        // Then: Cross-Origin-Resource-Policy header must be set to same-site
        assertThat(result.getResponse().getHeader("Cross-Origin-Resource-Policy"))
                .as("CORP header must prevent cross-origin reads of API responses")
                .isEqualTo("same-site");
    }

    // ========================================
    // Actuator access control (ZAP fix P1)
    // ========================================

    @Test
    @DisplayName("should_allowPublicAccess_when_actuatorHealthCalled")
    void should_allowPublicAccess_when_actuatorHealthCalled() throws Exception {
        // /actuator/health must remain public for load balancer health checks
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }
}
