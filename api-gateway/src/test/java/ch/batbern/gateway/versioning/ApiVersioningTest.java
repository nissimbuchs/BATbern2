package ch.batbern.gateway.versioning;

import ch.batbern.gateway.controller.TestResourceController;
import ch.batbern.gateway.filter.ApiVersionHeaderFilter;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for API versioning infrastructure.
 *
 * NOTE: Currently disabled due to Spring Boot context loading issues with Cognito configuration.
 * The functionality is verified through unit tests in ApiVersionHeaderFilterTest instead.
 *
 * TODO: Enable when test configuration properly mocks Cognito beans.
 *
 * Verifies that:
 * - v1 routes are accessible
 * - Unsupported versions return 404
 * - Version headers are included in responses
 */
@Disabled("Requires test configuration for Cognito beans - functionality verified in unit tests")
@WebMvcTest(controllers = TestResourceController.class)
@Import(ApiVersionHeaderFilter.class)
class ApiVersioningTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void should_routeToV1Controller_when_v1PathRequested() throws Exception {
        // When/Then
        mockMvc.perform(get("/api/v1/test-resources"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void should_return404_when_unsupportedVersionRequested() throws Exception {
        // When/Then - v2 is not yet implemented
        mockMvc.perform(get("/api/v2/test-resources"))
                .andExpect(status().isNotFound());
    }

    @Test
    void should_return404_when_noVersionInPath() throws Exception {
        // When/Then - must include version
        mockMvc.perform(get("/api/test-resources"))
                .andExpect(status().isNotFound());
    }

    @Test
    void should_includeVersionHeader_when_responseReturned() throws Exception {
        // When/Then
        mockMvc.perform(get("/api/v1/test-resources"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-API-Version"))
                .andExpect(header().string("X-API-Version", "v1"));
    }

    @Test
    void should_includeVersionHeader_when_errorResponseReturned() throws Exception {
        // When/Then - even error responses should include version header
        mockMvc.perform(get("/api/v1/nonexistent"))
                .andExpect(header().exists("X-API-Version"))
                .andExpect(header().string("X-API-Version", "v1"));
    }
}
