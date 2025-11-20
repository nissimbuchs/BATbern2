package ch.batbern.migration.job;

import ch.batbern.migration.AbstractIntegrationTest;
import ch.batbern.migration.repository.EntityIdMappingRepository;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD Tests for Event Migration Job (AC 5-8)
 * RED Phase: Write failing tests first
 */
@SpringBootTest
@SpringBatchTest
@TestPropertySource(properties = {
    "migration.target-api.event-management.base-url=http://localhost:8082",
    "migration.source-data-path=src/test/resources/test-data",
    "spring.batch.job.enabled=false"
})
public class EventMigrationJobTest extends AbstractIntegrationTest {

    private WireMockServer wireMockServer;

    @Autowired
    private JobLauncher jobLauncher;

    @Autowired
    @Qualifier("eventMigrationJob")
    private Job eventMigrationJob;

    @Autowired
    private EntityIdMappingRepository idMappingRepository;

    @BeforeEach
    public void setUp() {
        wireMockServer = new WireMockServer(WireMockConfiguration.options().port(8082));
        wireMockServer.start();
        WireMock.configureFor("localhost", 8082);
        idMappingRepository.deleteAll();
    }

    @AfterEach
    public void tearDown() {
        if (wireMockServer != null && wireMockServer.isRunning()) {
            wireMockServer.stop();
        }
    }

    /**
     * Test 5.1: should_migrateEvents_when_topicsJsonProvided
     * AC5: Migrate 60 historical events to Event Management Service
     */
    @Test
    public void should_migrateEvents_when_topicsJsonProvided() throws Exception {
        // Given: Mock Event Management API
        stubFor(post(urlEqualTo("/api/events"))
            .willReturn(aResponse()
                .withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"id\":\"" + UUID.randomUUID() + "\",\"eventCode\":\"BATbern1\",\"title\":\"GUI Frameworks\",\"eventNumber\":1}")));

        // When: Run event migration job
        JobParameters params = new JobParametersBuilder()
            .addLong("timestamp", System.currentTimeMillis())
            .toJobParameters();

        JobExecution execution = jobLauncher.run(eventMigrationJob, params);

        // Then: Job completed successfully
        assertEquals("COMPLETED", execution.getExitStatus().getExitCode());

        // Then: API called 5 times (5 events in test data)
        verify(5, postRequestedFor(urlEqualTo("/api/events")));

        // Then: Event code generated correctly (e.g., "BATbern1")
        verify(postRequestedFor(urlEqualTo("/api/events"))
            .withRequestBody(matchingJsonPath("$.eventCode", equalTo("BATbern1"))));

        verify(postRequestedFor(urlEqualTo("/api/events"))
            .withRequestBody(matchingJsonPath("$.eventCode", equalTo("BATbern2"))));

        // Then: Entity ID mappings created
        assertEquals(5, idMappingRepository.countByEntityType("Event"));
    }

    /**
     * Test 5.2: should_parseGermanDateFormat1_when_eventProcessed
     * AC8: German date parsing - Format 1: "24. Juni 05, 16:00h - 18:30h"
     */
    @Test
    public void should_parseGermanDateFormat1_when_eventProcessed() throws Exception {
        // Given: Mock Event Management API
        stubFor(post(urlEqualTo("/api/events"))
            .willReturn(aResponse()
                .withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"id\":\"" + UUID.randomUUID() + "\",\"eventCode\":\"BATbern1\",\"title\":\"GUI Frameworks\",\"eventNumber\":1}")));

        // When: Run event migration job
        JobParameters params = new JobParametersBuilder()
            .addLong("timestamp", System.currentTimeMillis())
            .toJobParameters();

        JobExecution execution = jobLauncher.run(eventMigrationJob, params);

        // Then: Date parsed correctly - "24. Juni 05" → 2005-06-24
        verify(postRequestedFor(urlEqualTo("/api/events"))
            .withRequestBody(matchingJsonPath("$.date", matching("2005-06-24T16:00:00.*"))));
    }

    /**
     * Test 5.3: should_parseGermanDateFormat2_when_eventProcessed
     * AC8: German date parsing - Format 2: "Freitag, 15. Juni 2018, 16.00 bis 20.15 Uhr"
     */
    @Test
    public void should_parseGermanDateFormat2_when_eventProcessed() throws Exception {
        // Given: Mock Event Management API
        stubFor(post(urlEqualTo("/api/events"))
            .willReturn(aResponse()
                .withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"id\":\"" + UUID.randomUUID() + "\",\"eventCode\":\"BATbern40\",\"title\":\"Microservices\",\"eventNumber\":40}")));

        // When: Run event migration job
        JobParameters params = new JobParametersBuilder()
            .addLong("timestamp", System.currentTimeMillis())
            .toJobParameters();

        JobExecution execution = jobLauncher.run(eventMigrationJob, params);

        // Then: Date parsed correctly - "15. Juni 2018, 16.00" → 2018-06-15T16:00:00
        verify(postRequestedFor(urlEqualTo("/api/events"))
            .withRequestBody(matchingJsonPath("$.date", matching("2018-06-15T16:00:00.*"))));
    }

    /**
     * Test 5.4: should_parseIsoDateFormat_when_eventProcessed
     * AC8: German date parsing - Format 3: ISO 8601 "2021-11-19T16:00:00Z"
     */
    @Test
    public void should_parseIsoDateFormat_when_eventProcessed() throws Exception {
        // Given: Mock Event Management API
        stubFor(post(urlEqualTo("/api/events"))
            .willReturn(aResponse()
                .withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"id\":\"" + UUID.randomUUID() + "\",\"eventCode\":\"BATbern50\",\"title\":\"Cloud Native\",\"eventNumber\":50}")));

        // When: Run event migration job
        JobParameters params = new JobParametersBuilder()
            .addLong("timestamp", System.currentTimeMillis())
            .toJobParameters();

        JobExecution execution = jobLauncher.run(eventMigrationJob, params);

        // Then: ISO date parsed correctly
        verify(postRequestedFor(urlEqualTo("/api/events"))
            .withRequestBody(matchingJsonPath("$.date", equalTo("2021-11-19T16:00:00Z"))));
    }

    /**
     * Test 5.5: should_setArchivedStatus_when_historicalEventMigrated
     * AC7: Timeline migration - default "archived" status
     */
    @Test
    public void should_setArchivedStatus_when_historicalEventMigrated() throws Exception {
        // Given: Mock Event Management API
        stubFor(post(urlEqualTo("/api/events"))
            .willReturn(aResponse()
                .withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"id\":\"" + UUID.randomUUID() + "\",\"eventCode\":\"BATbern1\",\"title\":\"GUI Frameworks\",\"eventNumber\":1}")));

        // When: Run event migration job
        JobParameters params = new JobParametersBuilder()
            .addLong("timestamp", System.currentTimeMillis())
            .toJobParameters();

        JobExecution execution = jobLauncher.run(eventMigrationJob, params);

        // Then: Status set to "ARCHIVED" for historical events
        verify(postRequestedFor(urlEqualTo("/api/events"))
            .withRequestBody(matchingJsonPath("$.status", equalTo("ARCHIVED"))));
    }

    /**
     * Test 5.6: should_setDefaultVenue_when_eventMigrated
     * AC8: Metadata migration - default venue for historical events
     */
    @Test
    public void should_setDefaultVenue_when_eventMigrated() throws Exception {
        // Given: Mock Event Management API
        stubFor(post(urlEqualTo("/api/events"))
            .willReturn(aResponse()
                .withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"id\":\"" + UUID.randomUUID() + "\",\"eventCode\":\"BATbern1\",\"title\":\"GUI Frameworks\",\"eventNumber\":1}")));

        // When: Run event migration job
        JobParameters params = new JobParametersBuilder()
            .addLong("timestamp", System.currentTimeMillis())
            .toJobParameters();

        JobExecution execution = jobLauncher.run(eventMigrationJob, params);

        // Then: Default venue set to "Kornhausforum"
        verify(postRequestedFor(urlEqualTo("/api/events"))
            .withRequestBody(matchingJsonPath("$.venueName", equalTo("Kornhausforum")))
            .withRequestBody(matchingJsonPath("$.venueAddress", equalTo("Kornhausplatz 18, 3011 Bern")))
            .withRequestBody(matchingJsonPath("$.venueCapacity", equalTo("200"))));
    }
}
