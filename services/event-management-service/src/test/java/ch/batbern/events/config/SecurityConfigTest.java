package ch.batbern.events.config;

import ch.batbern.events.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security Configuration Tests
 *
 * Tests verify that:
 * - Public endpoints are accessible without authentication
 * - Authentication is enforced for protected endpoints
 * - JWT validation happens at API Gateway (services trust gateway)
 */
@DisplayName("Security Configuration Tests")
class SecurityConfigTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    // ========================================
    // Public Endpoint Tests
    // ========================================

    @Test
    @DisplayName("should_allowAccess_when_healthEndpointAccessed")
    void should_allowAccess_when_healthEndpointAccessed() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("should_allowAccess_when_swaggerUiAccessed")
    void should_allowAccess_when_swaggerUiAccessed() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("should_allowAnonymousAccess_when_creatingRegistration")
    void should_allowAnonymousAccess_when_creatingRegistration() throws Exception {
        // Given: Anonymous user (no Authorization header)
        String requestBody = """
            {
                "email": "test@example.com",
                "firstName": "Test",
                "lastName": "User",
                "termsAccepted": true
            }
            """;

        // When: POST to registration endpoint without auth
        // Then: Should not require authentication (404 is OK, means security passed)
        mockMvc.perform(post("/api/v1/events/NON_EXISTENT_EVENT/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isNotFound()); // Event doesn't exist, but security passed
    }

    @Test
    @DisplayName("should_allowAnonymousAccess_when_viewingEventDetails")
    void should_allowAnonymousAccess_when_viewingEventDetails() throws Exception {
        // When: GET event details without authentication
        // Then: Should not require authentication (404 is OK, means security passed)
        mockMvc.perform(get("/api/v1/events/NON_EXISTENT_EVENT"))
                .andExpect(status().isNotFound()); // Event doesn't exist, but security passed
    }

    @Test
    @DisplayName("should_allowAnonymousAccess_when_viewingCurrentEvent")
    void should_allowAnonymousAccess_when_viewingCurrentEvent() throws Exception {
        // When: GET current event without authentication
        // Then: Should not require authentication (404 is OK, means security passed)
        mockMvc.perform(get("/api/v1/events/current"))
                .andExpect(status().isNotFound()); // No current event, but security passed
    }

    @Test
    @DisplayName("should_allowAnonymousAccess_when_viewingSessions")
    void should_allowAnonymousAccess_when_viewingSessions() throws Exception {
        // When: GET event sessions without authentication
        // Then: Should not require authentication (404 is OK, means security passed)
        mockMvc.perform(get("/api/v1/events/NON_EXISTENT_EVENT/sessions"))
                .andExpect(status().isNotFound()); // Event doesn't exist, but security passed
    }

    @Test
    @DisplayName("should_allowAnonymousAccess_when_viewingSpeakers")
    void should_allowAnonymousAccess_when_viewingSpeakers() throws Exception {
        // When: GET session speakers without authentication
        // Then: Should not require authentication (404 is OK, means security passed)
        mockMvc.perform(get("/api/v1/events/NON_EXISTENT_EVENT/sessions/NON_EXISTENT_SESSION/speakers"))
                .andExpect(status().isNotFound()); // Event doesn't exist, but security passed
    }

    @Test
    @DisplayName("should_allowAnonymousAccess_when_confirmingRegistration")
    void should_allowAnonymousAccess_when_confirmingRegistration() throws Exception {
        // When: POST to confirmation endpoint without authentication
        // Then: Should not require authentication (400 is OK, means security passed, token validation failed)
        mockMvc.perform(post("/api/v1/events/NON_EXISTENT_EVENT/registrations/confirm")
                        .param("token", "invalid-token"))
                .andExpect(status().isBadRequest()); // Invalid token, but security passed
    }

    // ========================================
    // Architecture Verification Tests
    // ========================================

    @Test
    @DisplayName("should_trustApiGateway_when_noJwtValidationInService")
    void should_trustApiGateway_when_noJwtValidationInService() throws Exception {
        // Given: Request without Authorization header (simulating API Gateway proxy)

        // When: Accessing public endpoint
        // Then: Should succeed (service trusts API Gateway, no JWT validation)
        mockMvc.perform(get("/api/v1/events/current"))
                .andExpect(status().isNotFound()); // No current event, but security passed
    }

    @Test
    @DisplayName("should_notRejectRequests_when_invalidJwtProvided")
    void should_notRejectRequests_when_invalidJwtProvided() throws Exception {
        // Given: Request with invalid JWT (API Gateway should have rejected it)
        // Services should trust API Gateway and not validate JWTs themselves

        // When: Accessing endpoint with invalid token
        // Then: Should not reject based on token (service trusts gateway)
        mockMvc.perform(get("/api/v1/events/current")
                        .header("Authorization", "Bearer invalid-jwt"))
                .andExpect(status().isNotFound()); // No current event, but security passed
    }
}
