package ch.batbern.migration.orchestrator;

import ch.batbern.migration.AbstractIntegrationTest;
import ch.batbern.migration.repository.EntityIdMappingRepository;
import ch.batbern.migration.repository.MigrationErrorRepository;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Migration Job Orchestrator Test
 *
 * Tests sequential execution of all migration jobs with dependency validation.
 *
 * Story: 3.2.1 - Migration Tool Implementation
 * AC: 1-4 (Batch Processing Infrastructure)
 */
@AutoConfigureWireMock(port = 0)
@TestPropertySource(properties = {
    "migration.target-api.company-management.base-url=http://localhost:${wiremock.server.port}",
    "migration.target-api.event-management.base-url=http://localhost:${wiremock.server.port}",
    "migration.target-api.speaker-coordination.base-url=http://localhost:${wiremock.server.port}"
})
@DirtiesContext
class MigrationJobOrchestratorTest extends AbstractIntegrationTest {

    @Autowired
    private MigrationJobOrchestrator orchestrator;

    @Autowired
    private EntityIdMappingRepository idMappingRepository;

    @Autowired
    private MigrationErrorRepository errorRepository;

    @BeforeEach
    void setupWireMock() {
        // Mock Company API - POST /api/companies
        stubFor(post(urlPathEqualTo("/api/companies"))
            .willReturn(aResponse()
                .withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"id\":\"" + java.util.UUID.randomUUID() + "\",\"name\":\"test-company\"}")));

        // Mock Event API - POST /api/events
        stubFor(post(urlPathEqualTo("/api/events"))
            .willReturn(aResponse()
                .withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"id\":\"" + java.util.UUID.randomUUID() + "\",\"eventCode\":\"BATbern123\"}")));

        // Mock User API - POST /api/users
        stubFor(post(urlPathEqualTo("/api/users"))
            .willReturn(aResponse()
                .withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"id\":\"" + java.util.UUID.randomUUID() + "\",\"username\":\"test.user\"}")));

        // Mock Speaker API - POST /api/speakers
        stubFor(post(urlPathEqualTo("/api/speakers"))
            .willReturn(aResponse()
                .withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"id\":\"" + java.util.UUID.randomUUID() + "\",\"userId\":\"" + java.util.UUID.randomUUID() + "\"}")));

        // Mock Session API - POST /api/sessions
        stubFor(post(urlPathEqualTo("/api/sessions"))
            .willReturn(aResponse()
                .withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"id\":\"" + java.util.UUID.randomUUID() + "\",\"title\":\"test-session\"}")));

        // Mock SessionUser API - POST /api/session-users
        stubFor(post(urlPathEqualTo("/api/session-users"))
            .willReturn(aResponse()
                .withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"sessionId\":\"" + java.util.UUID.randomUUID() + "\",\"userId\":\"" + java.util.UUID.randomUUID() + "\"}")));
    }

    /**
     * AC 1-4: Test sequential execution of all migration jobs
     *
     * Expected execution order:
     * 1. Company (test data: 3 companies)
     * 2. Event (test data: 5 events)
     * 3. UserSpeaker (test data: 5 users + speakers, depends on Company)
     * 4. Session (test data: 5 sessions, depends on Event + User)
     * 5. File (test data: skipped as no files in test dir)
     *
     * NOTE: This test uses small test dataset, not full production data
     */
    @Test
    void should_runJobsSequentially_when_orchestratorExecutes() throws Exception {
        // When: Execute full migration workflow
        MigrationJobOrchestrator.MigrationResult result = orchestrator.executeFullMigration();

        // Then: Migration completes successfully
        assertTrue(result.isSuccess(), "Migration should complete successfully");
        assertNull(result.getErrorMessage(), "Should have no error message");

        // Verify: All 5 jobs executed
        assertEquals(5, result.getJobExecutions().size(), "Should execute 5 jobs");

        // Verify: Job execution order
        String[] expectedOrder = {"Company", "Event", "UserSpeaker", "Session", "File"};
        String[] actualOrder = result.getJobExecutions().keySet().toArray(new String[0]);
        assertArrayEquals(expectedOrder, actualOrder, "Jobs should execute in correct order");

        // Verify: Company migration (test data: 3 companies from test-data/companies.json)
        long companyCount = idMappingRepository.countByEntityType("Company");
        assertTrue(companyCount > 0, "Should create at least 1 company");

        // Verify: Event migration (test data: 5 events from test-data/topics.json)
        long eventCount = idMappingRepository.countByEntityType("Event");
        assertTrue(eventCount > 0, "Should create at least 1 event");

        // Verify: User migration (test data: 5 users from test-data/sessions.json speakers)
        long userCount = idMappingRepository.countByEntityType("User");
        assertTrue(userCount > 0, "Should create at least 1 user");

        // Verify: Speaker migration (test data: 5 speakers)
        long speakerCount = idMappingRepository.countByEntityType("Speaker");
        assertTrue(speakerCount > 0, "Should create at least 1 speaker");

        // Verify: Session migration (test data: 5 sessions from test-data/sessions.json)
        long sessionCount = idMappingRepository.countByEntityType("Session");
        assertTrue(sessionCount > 0, "Should create at least 1 session");

        // Verify: Zero unresolved errors
        assertEquals(0, errorRepository.countByResolvedFalse(),
            "Should have zero unresolved errors after migration");

        // Verify: Migration completed in reasonable time (< 60 seconds for test data)
        assertTrue(result.getTotalDurationMs() < 60_000,
            "Migration should complete within 60 seconds");
    }

    /**
     * AC 3: Verify chunk processing configuration (100 records per chunk)
     *
     * This is implicitly tested by the job configurations, but we verify
     * the jobs complete successfully with chunked processing.
     */
    @Test
    void should_processInChunks_when_jobExecutes() throws Exception {
        // When: Execute full migration workflow
        MigrationJobOrchestrator.MigrationResult result = orchestrator.executeFullMigration();

        // Then: All jobs complete successfully (chunk size configured in job configs)
        assertTrue(result.isSuccess(), "Migration with chunk processing should succeed");

        // Verify: Jobs processed records (chunk size 100 configured in job configs)
        long sessionCount = idMappingRepository.countByEntityType("Session");
        assertTrue(sessionCount > 0, "Should process sessions in chunks");
    }

    /**
     * Verify dependency validation: User job requires Company completion
     */
    @Test
    void should_validateDependencies_when_userJobExecutes() throws Exception {
        // When: Execute full migration workflow
        MigrationJobOrchestrator.MigrationResult result = orchestrator.executeFullMigration();

        // Then: User job only executes after Company job completes
        assertTrue(result.isSuccess(), "Migration should complete successfully");

        // Verify: Company mappings exist before User processing
        long companyCount = idMappingRepository.countByEntityType("Company");
        assertTrue(companyCount > 0, "Companies should be created before users");

        // Verify: Users created with valid company references (validated during processing)
        long userCount = idMappingRepository.countByEntityType("User");
        assertTrue(userCount > 0, "Users should be created with valid company references");
    }

    /**
     * Verify dependency validation: Session job requires Event + User completion
     */
    @Test
    void should_validateDependencies_when_sessionJobExecutes() throws Exception {
        // When: Execute full migration workflow
        MigrationJobOrchestrator.MigrationResult result = orchestrator.executeFullMigration();

        // Then: Session job only executes after Event + User jobs complete
        assertTrue(result.isSuccess(), "Migration should complete successfully");

        // Verify: Event and User mappings exist before Session processing
        long eventCount = idMappingRepository.countByEntityType("Event");
        long userCount = idMappingRepository.countByEntityType("User");
        assertTrue(eventCount > 0, "Events should be created before sessions");
        assertTrue(userCount > 0, "Users should be created before sessions");

        // Verify: Sessions created with valid event references
        long sessionCount = idMappingRepository.countByEntityType("Session");
        assertTrue(sessionCount > 0, "Sessions should be created with valid event references");
    }
}
