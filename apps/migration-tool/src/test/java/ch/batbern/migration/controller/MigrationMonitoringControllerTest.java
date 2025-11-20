package ch.batbern.migration.controller;

import ch.batbern.migration.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Migration Monitoring Controller Test
 *
 * Tests REST endpoints for monitoring migration progress.
 *
 * Story: 3.2.1 - Task 9: Progress Monitoring Dashboard
 */
@AutoConfigureMockMvc
class MigrationMonitoringControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    /**
     * Test health check endpoint
     */
    @Test
    void should_returnHealthStatus_when_healthEndpointCalled() throws Exception {
        mockMvc.perform(get("/migration/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"))
            .andExpect(jsonPath("$.service").value("migration-tool"));
    }

    /**
     * Test migration status endpoint
     */
    @Test
    void should_returnMigrationStatus_when_statusEndpointCalled() throws Exception {
        mockMvc.perform(get("/migration/status"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.entityCounts").exists())
            .andExpect(jsonPath("$.entityCounts.companies").isNumber())
            .andExpect(jsonPath("$.entityCounts.events").isNumber())
            .andExpect(jsonPath("$.entityCounts.users").isNumber())
            .andExpect(jsonPath("$.entityCounts.speakers").isNumber())
            .andExpect(jsonPath("$.entityCounts.sessions").isNumber())
            .andExpect(jsonPath("$.errors").exists())
            .andExpect(jsonPath("$.errors.total").isNumber())
            .andExpect(jsonPath("$.errors.unresolved").isNumber())
            .andExpect(jsonPath("$.errors.resolved").isNumber());
    }

    /**
     * Test errors endpoint
     */
    @Test
    void should_returnErrors_when_errorsEndpointCalled() throws Exception {
        mockMvc.perform(get("/migration/errors"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.count").isNumber())
            .andExpect(jsonPath("$.errors").isArray());
    }

    /**
     * Test errors export endpoint
     */
    @Test
    void should_exportErrors_when_exportEndpointCalled() throws Exception {
        mockMvc.perform(get("/migration/errors/export")
                .param("outputPath", "/tmp/test-migration-errors.csv"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.errorCount").isNumber())
            .andExpect(jsonPath("$.outputPath").value("/tmp/test-migration-errors.csv"));
    }
}
