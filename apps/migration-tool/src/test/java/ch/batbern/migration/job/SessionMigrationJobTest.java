package ch.batbern.migration.job;

import ch.batbern.migration.AbstractIntegrationTest;
import ch.batbern.migration.model.target.EntityIdMapping;
import ch.batbern.migration.repository.EntityIdMappingRepository;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.*;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.annotation.DirtiesContext;

import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD Tests for Session Migration Job (AC 6, 12)
 *
 * Test Coverage:
 * - AC6: Migrate 302 event sessions with speaker assignments
 * - AC12: Establish SessionUser relationships (speaker-event-session assignments)
 *
 * RED Phase: All tests should FAIL initially
 */
@Tag("integration")
@DirtiesContext
public class SessionMigrationJobTest extends AbstractIntegrationTest {

    @Autowired(required = false)
    private JobLauncher jobLauncher;

    @Autowired(required = false)
    @Qualifier("sessionMigrationJob")
    private Job sessionMigrationJob;

    @Autowired
    private EntityIdMappingRepository idMappingRepository;

    private WireMockServer wireMockServer;

    @BeforeEach
    public void setUp() {
        // Start WireMock server
        wireMockServer = new WireMockServer(8080);
        wireMockServer.start();
        WireMock.configureFor("localhost", 8080);

        // Clean up any existing test data to avoid constraint violations
        idMappingRepository.deleteAll();

        // Pre-populate entity_id_mapping with test data (prerequisite: Events and Users migrated)
        // Event mapping: BAT 1 → UUID
        EntityIdMapping eventMapping = new EntityIdMapping();
        eventMapping.setEntityType("Event");
        eventMapping.setLegacyId("1");
        eventMapping.setNewId(UUID.fromString("550e8400-e29b-41d4-a716-446655440001"));
        idMappingRepository.save(eventMapping);

        // User mapping: thomas.goetz → UUID (from UserSpeakerMigrationJob)
        EntityIdMapping userMapping = new EntityIdMapping();
        userMapping.setEntityType("User");
        userMapping.setLegacyId("thomas.goetz");
        userMapping.setNewId(UUID.fromString("650e8400-e29b-41d4-a716-446655440002"));
        idMappingRepository.save(userMapping);

        // Additional user mappings for test speakers
        EntityIdMapping userMapping2 = new EntityIdMapping();
        userMapping2.setEntityType("User");
        userMapping2.setLegacyId("volkert.barr");
        userMapping2.setNewId(UUID.fromString("650e8400-e29b-41d4-a716-446655440003"));
        idMappingRepository.save(userMapping2);

        EntityIdMapping userMapping3 = new EntityIdMapping();
        userMapping3.setEntityType("User");
        userMapping3.setLegacyId("peter.kummer");
        userMapping3.setNewId(UUID.fromString("650e8400-e29b-41d4-a716-446655440004"));
        idMappingRepository.save(userMapping3);

        EntityIdMapping userMapping4 = new EntityIdMapping();
        userMapping4.setEntityType("User");
        userMapping4.setLegacyId("beat.stoeckli");
        userMapping4.setNewId(UUID.fromString("650e8400-e29b-41d4-a716-446655440005"));
        idMappingRepository.save(userMapping4);

        EntityIdMapping userMapping5 = new EntityIdMapping();
        userMapping5.setEntityType("User");
        userMapping5.setLegacyId("alain.jacot-guillarmod");
        userMapping5.setNewId(UUID.fromString("650e8400-e29b-41d4-a716-446655440006"));
        idMappingRepository.save(userMapping5);

        // Event mappings for BAT 2 and BAT 3
        EntityIdMapping eventMapping2 = new EntityIdMapping();
        eventMapping2.setEntityType("Event");
        eventMapping2.setLegacyId("2");
        eventMapping2.setNewId(UUID.fromString("550e8400-e29b-41d4-a716-446655440002"));
        idMappingRepository.save(eventMapping2);

        EntityIdMapping eventMapping3 = new EntityIdMapping();
        eventMapping3.setEntityType("Event");
        eventMapping3.setLegacyId("3");
        eventMapping3.setNewId(UUID.fromString("550e8400-e29b-41d4-a716-446655440003"));
        idMappingRepository.save(eventMapping3);
    }

    @AfterEach
    public void tearDown() {
        if (wireMockServer != null && wireMockServer.isRunning()) {
            wireMockServer.stop();
        }
    }

    /**
     * Test 5.1: should_migrateSessions_when_sessionsJsonProvided
     *
     * AC6: Migrate 302 event sessions with speaker assignments
     *
     * Expected: RED - SessionMigrationJob not implemented yet
     */
    @Test
    public void should_migrateSessions_when_sessionsJsonProvided() throws Exception {
        // Given: Job is not null (will fail if not configured)
        assertNotNull(sessionMigrationJob, "Session migration job should be configured");

        // Mock Event Management API for session creation
        stubFor(post(urlEqualTo("/api/sessions"))
            .willReturn(aResponse()
                .withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                        "id": "750e8400-e29b-41d4-a716-446655440003",
                        "eventId": "550e8400-e29b-41d4-a716-446655440001",
                        "title": "Test Session Title",
                        "orderInProgram": 1
                    }
                    """)));

        // When: Run session migration job
        JobParameters jobParameters = new JobParametersBuilder()
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();
        JobExecution execution = jobLauncher.run(sessionMigrationJob, jobParameters);

        // Then: Job completes successfully
        assertEquals(BatchStatus.COMPLETED, execution.getStatus());

        // Verify sessions.json has 5 test sessions
        verify(5, postRequestedFor(urlEqualTo("/api/sessions")));

        // Verify Session entity ID mapping stored (for SessionUser creation)
        assertEquals(5, idMappingRepository.countByEntityType("Session"));
    }

    /**
     * Test 5.2: should_lookupEventId_when_sessionProcessed
     *
     * AC6: Verify eventId lookup from entity_id_mapping
     *
     * Expected: RED - EventId lookup logic not implemented yet
     */
    @Test
    public void should_lookupEventId_when_sessionProcessed() throws Exception {
        // Given: Job configured
        assertNotNull(sessionMigrationJob);

        // Mock Event Management API
        stubFor(post(urlEqualTo("/api/sessions"))
            .willReturn(aResponse()
                .withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                        "id": "750e8400-e29b-41d4-a716-446655440003",
                        "eventId": "550e8400-e29b-41d4-a716-446655440001",
                        "title": "Test Session Title",
                        "orderInProgram": 1
                    }
                    """)));

        // When: Run session migration job
        JobParameters jobParameters = new JobParametersBuilder()
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();
        jobLauncher.run(sessionMigrationJob, jobParameters);

        // Then: Verify eventId lookup applied (BAT 1 → UUID)
        verify(postRequestedFor(urlEqualTo("/api/sessions"))
            .withRequestBody(matchingJsonPath("$.eventId", equalTo("550e8400-e29b-41d4-a716-446655440001"))));
    }

    /**
     * Test 5.3: should_createSessionUsers_when_speakerAssigned
     *
     * AC12: Establish SessionUser relationships (speaker-event-session assignments)
     *
     * Expected: RED - SessionUser junction creation not implemented yet
     */
    @Test
    public void should_createSessionUsers_when_speakerAssigned() throws Exception {
        // Given: Job configured
        assertNotNull(sessionMigrationJob);

        // Mock Event Management API for session creation
        stubFor(post(urlEqualTo("/api/sessions"))
            .willReturn(aResponse()
                .withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                        "id": "750e8400-e29b-41d4-a716-446655440003",
                        "eventId": "550e8400-e29b-41d4-a716-446655440001",
                        "title": "Test Session Title",
                        "orderInProgram": 1
                    }
                    """)));

        // Mock SessionUser junction API
        stubFor(post(urlEqualTo("/api/session-users"))
            .willReturn(aResponse()
                .withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                        "id": "850e8400-e29b-41d4-a716-446655440004",
                        "sessionId": "750e8400-e29b-41d4-a716-446655440003",
                        "userId": "650e8400-e29b-41d4-a716-446655440002",
                        "role": "SPEAKER"
                    }
                    """)));

        // When: Run session migration job
        JobParameters jobParameters = new JobParametersBuilder()
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();
        jobLauncher.run(sessionMigrationJob, jobParameters);

        // Then: Verify SessionUser junctions created for speakers
        // sessions.json has 7 speaker assignments across 5 sessions (2 sessions have multiple speakers)
        // We expect at least 5 calls (one per session minimum)
        verify(moreThanOrExactly(5), postRequestedFor(urlEqualTo("/api/session-users")));
    }

    /**
     * Test 5.4: should_lookupUserId_when_sessionUserCreated
     *
     * AC12: Verify userId lookup from entity_id_mapping (from UserSpeakerMigrationJob)
     *
     * Expected: RED - UserId lookup logic not implemented yet
     */
    @Test
    public void should_lookupUserId_when_sessionUserCreated() throws Exception {
        // Given: Job configured
        assertNotNull(sessionMigrationJob);

        // Mock APIs
        stubFor(post(urlEqualTo("/api/sessions"))
            .willReturn(aResponse()
                .withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                        "id": "750e8400-e29b-41d4-a716-446655440003",
                        "eventId": "550e8400-e29b-41d4-a716-446655440001",
                        "title": "Test Session Title",
                        "orderInProgram": 1
                    }
                    """)));

        stubFor(post(urlEqualTo("/api/session-users"))
            .willReturn(aResponse()
                .withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                        "id": "850e8400-e29b-41d4-a716-446655440004",
                        "sessionId": "750e8400-e29b-41d4-a716-446655440003",
                        "userId": "650e8400-e29b-41d4-a716-446655440002",
                        "role": "SPEAKER"
                    }
                    """)));

        // When: Run session migration job
        JobParameters jobParameters = new JobParametersBuilder()
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();
        jobLauncher.run(sessionMigrationJob, jobParameters);

        // Then: Verify userId lookup applied (thomas.goetz → UUID)
        verify(postRequestedFor(urlEqualTo("/api/session-users"))
            .withRequestBody(matchingJsonPath("$.userId", equalTo("650e8400-e29b-41d4-a716-446655440002"))));
    }
}
