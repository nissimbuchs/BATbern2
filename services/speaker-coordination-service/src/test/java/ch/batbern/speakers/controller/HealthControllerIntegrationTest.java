package ch.batbern.speakers.controller;

import ch.batbern.speakers.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for HealthController.
 *
 * Tests the health and info endpoints to ensure the service is operational.
 */
class HealthControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void should_returnHealthStatus_when_healthEndpointCalled() throws Exception {
        // Spring Boot Actuator's default /actuator/health endpoint
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void should_returnServiceInfo_when_infoEndpointCalled() throws Exception {
        // Spring Boot Actuator's default /actuator/info endpoint
        // Returns build info from git.properties and build-info.properties
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isOk());
    }
}
