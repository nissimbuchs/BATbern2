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
 * TDD Tests for User/Speaker Migration Job (AC 9-12)
 * RED Phase: Write failing tests first
 *
 * User + Speaker creation per ADR-004:
 * - Bio goes in User entity
 * - Speaker entity only has userId + expertise
 */
@SpringBootTest
@SpringBatchTest
@TestPropertySource(properties = {
    "migration.target-api.company-management.base-url=http://localhost:8081",
    "migration.target-api.speaker-coordination.base-url=http://localhost:8083",
    "migration.source-data-path=src/test/resources/test-data",
    "spring.batch.job.enabled=false"
})
public class UserSpeakerMigrationJobTest extends AbstractIntegrationTest {

    private WireMockServer companyMgmtServer;
    private WireMockServer speakerCoordServer;

    @Autowired
    private JobLauncher jobLauncher;

    @Autowired
    @Qualifier("userSpeakerMigrationJob")
    private Job userSpeakerMigrationJob;

    @Autowired
    private EntityIdMappingRepository idMappingRepository;

    @BeforeEach
    public void setUp() {
        // Start WireMock servers for both APIs
        companyMgmtServer = new WireMockServer(WireMockConfiguration.options().port(8081));
        companyMgmtServer.start();
        WireMock.configureFor("localhost", 8081);

        speakerCoordServer = new WireMockServer(WireMockConfiguration.options().port(8083));
        speakerCoordServer.start();

        idMappingRepository.deleteAll();

        // Setup company ID mappings (companies must exist before users)
        setupCompanyMappings();
    }

    @AfterEach
    public void tearDown() {
        if (companyMgmtServer != null && companyMgmtServer.isRunning()) {
            companyMgmtServer.stop();
        }
        if (speakerCoordServer != null && speakerCoordServer.isRunning()) {
            speakerCoordServer.stop();
        }
    }

    /**
     * Setup company ID mappings for test data
     * Companies: mobiliar, postfinance, postmail
     */
    private void setupCompanyMappings() {
        idMappingRepository.save(createMapping("Company", "mobiliar", UUID.randomUUID()));
        idMappingRepository.save(createMapping("Company", "postfinance", UUID.randomUUID()));
        idMappingRepository.save(createMapping("Company", "postmail", UUID.randomUUID()));
    }

    private ch.batbern.migration.model.target.EntityIdMapping createMapping(String entityType, String legacyId, UUID newId) {
        ch.batbern.migration.model.target.EntityIdMapping mapping = new ch.batbern.migration.model.target.EntityIdMapping();
        mapping.setEntityType(entityType);
        mapping.setLegacyId(legacyId);
        mapping.setNewId(newId);
        return mapping;
    }

    /**
     * Test 9.1: should_createUserAndSpeaker_when_speakerProcessed
     * AC9: Migrate speaker profiles with User + Speaker entities
     * ADR-004: Bio in User, NOT in Speaker
     */
    @Test
    public void should_createUserAndSpeaker_when_speakerProcessed() throws Exception {
        // Given: Mock Company Management API (User creation)
        WireMock.configureFor("localhost", 8081);
        stubFor(post(urlEqualTo("/api/users"))
            .willReturn(aResponse()
                .withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"id\":\"" + UUID.randomUUID() + "\",\"username\":\"thomas.goetz\",\"firstName\":\"Thomas\",\"lastName\":\"Goetz\"}")));

        // Given: Mock Speaker Coordination API (Speaker creation)
        WireMock.configureFor("localhost", 8083);
        stubFor(post(urlEqualTo("/api/speakers"))
            .willReturn(aResponse()
                .withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"id\":\"" + UUID.randomUUID() + "\",\"userId\":\"" + UUID.randomUUID() + "\",\"isActive\":true}")));

        // When: Run user/speaker migration job
        JobParameters params = new JobParametersBuilder()
            .addLong("timestamp", System.currentTimeMillis())
            .toJobParameters();

        JobExecution execution = jobLauncher.run(userSpeakerMigrationJob, params);

        // Then: Job completed successfully
        assertEquals("COMPLETED", execution.getExitStatus().getExitCode());

        // Then: User creation API called (5 unique speakers in test data)
        // Thomas Goetz (2x but only 1 created), Volkert Barr, Peter Kummer, Beat Stöckli, Alain Jacot-Guillarmod
        WireMock.configureFor("localhost", 8081);
        verify(5, postRequestedFor(urlEqualTo("/api/users")));

        // Then: Speaker creation API called
        WireMock.configureFor("localhost", 8083);
        verify(5, postRequestedFor(urlEqualTo("/api/speakers")));

        // Then: Bio is in User entity (ADR-004)
        WireMock.configureFor("localhost", 8081);
        verify(postRequestedFor(urlEqualTo("/api/users"))
            .withRequestBody(matchingJsonPath("$.bio", containing("Thomas Goetz"))));

        // Then: Speaker entity has userId (ADR-004)
        WireMock.configureFor("localhost", 8083);
        verify(postRequestedFor(urlEqualTo("/api/speakers"))
            .withRequestBody(matchingJsonPath("$.userId")));
    }

    /**
     * Test 9.2: should_generateUsername_when_speakerNameParsed
     * AC9: Username generation from "FirstName LastName, Company" format
     */
    @Test
    public void should_generateUsername_when_speakerNameParsed() throws Exception {
        // Given: Mock APIs
        WireMock.configureFor("localhost", 8081);
        stubFor(post(urlEqualTo("/api/users"))
            .willReturn(aResponse()
                .withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"id\":\"" + UUID.randomUUID() + "\",\"username\":\"thomas.goetz\",\"firstName\":\"Thomas\",\"lastName\":\"Goetz\"}")));

        WireMock.configureFor("localhost", 8083);
        stubFor(post(urlEqualTo("/api/speakers"))
            .willReturn(aResponse()
                .withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"id\":\"" + UUID.randomUUID() + "\",\"userId\":\"" + UUID.randomUUID() + "\",\"isActive\":true}")));

        // When: Run migration
        JobParameters params = new JobParametersBuilder()
            .addLong("timestamp", System.currentTimeMillis())
            .toJobParameters();

        jobLauncher.run(userSpeakerMigrationJob, params);

        // Then: Username generated as "firstname.lastname"
        WireMock.configureFor("localhost", 8081);
        verify(postRequestedFor(urlEqualTo("/api/users"))
            .withRequestBody(matchingJsonPath("$.username", equalTo("thomas.goetz"))));

        verify(postRequestedFor(urlEqualTo("/api/users"))
            .withRequestBody(matchingJsonPath("$.username", equalTo("volkert.barr"))));

        verify(postRequestedFor(urlEqualTo("/api/users"))
            .withRequestBody(matchingJsonPath("$.username", equalTo("peter.kummer"))));

        // Then: Special characters handled (Stöckli → stoeckli)
        verify(postRequestedFor(urlEqualTo("/api/users"))
            .withRequestBody(matchingJsonPath("$.username", matching("beat.*stoeckli"))));
    }

    /**
     * Test 9.3: should_validateCompany_when_userCreated
     * AC9: Company validation before user creation
     */
    @Test
    public void should_validateCompany_when_userCreated() throws Exception {
        // Given: Mock APIs
        WireMock.configureFor("localhost", 8081);
        stubFor(post(urlEqualTo("/api/users"))
            .willReturn(aResponse()
                .withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"id\":\"" + UUID.randomUUID() + "\",\"username\":\"thomas.goetz\",\"firstName\":\"Thomas\",\"lastName\":\"Goetz\"}")));

        WireMock.configureFor("localhost", 8083);
        stubFor(post(urlEqualTo("/api/speakers"))
            .willReturn(aResponse()
                .withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"id\":\"" + UUID.randomUUID() + "\",\"userId\":\"" + UUID.randomUUID() + "\",\"isActive\":true}")));

        // When: Run migration
        JobParameters params = new JobParametersBuilder()
            .addLong("timestamp", System.currentTimeMillis())
            .toJobParameters();

        jobLauncher.run(userSpeakerMigrationJob, params);

        // Then: User created with valid companyId (looked up from entity_id_mapping)
        WireMock.configureFor("localhost", 8081);
        verify(postRequestedFor(urlEqualTo("/api/users"))
            .withRequestBody(matchingJsonPath("$.companyId", matching("[0-9a-f-]{36}"))));
    }

    /**
     * Test 9.4: should_handleDuplicateSpeakers_when_appearingMultipleTimes
     * AC12: Duplicate speaker detection (same name appears in multiple sessions)
     */
    @Test
    public void should_handleDuplicateSpeakers_when_appearingMultipleTimes() throws Exception {
        // Given: Mock APIs
        WireMock.configureFor("localhost", 8081);
        stubFor(post(urlEqualTo("/api/users"))
            .willReturn(aResponse()
                .withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"id\":\"" + UUID.randomUUID() + "\",\"username\":\"thomas.goetz\",\"firstName\":\"Thomas\",\"lastName\":\"Goetz\"}")));

        WireMock.configureFor("localhost", 8083);
        stubFor(post(urlEqualTo("/api/speakers"))
            .willReturn(aResponse()
                .withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"id\":\"" + UUID.randomUUID() + "\",\"userId\":\"" + UUID.randomUUID() + "\",\"isActive\":true}")));

        // When: Run migration (Thomas Goetz appears in BAT 1 and BAT 3)
        JobParameters params = new JobParametersBuilder()
            .addLong("timestamp", System.currentTimeMillis())
            .toJobParameters();

        jobLauncher.run(userSpeakerMigrationJob, params);

        // Then: Only 5 unique speakers created (Thomas Goetz deduplicated in reader)
        // Test data: Thomas Goetz (2x → 1x), Volkert Barr, Peter Kummer, Beat Stöckli, Alain Jacot-Guillarmod
        WireMock.configureFor("localhost", 8081);
        verify(5, postRequestedFor(urlEqualTo("/api/users")));

        // Then: Entity ID mapping stored for duplicate detection
        assertEquals(5, idMappingRepository.countByEntityType("User"));
    }

    /**
     * Test 9.5: should_storeUserIdMapping_when_userCreated
     * AC12: Store User ID mapping for SessionUser junction creation
     */
    @Test
    public void should_storeUserIdMapping_when_userCreated() throws Exception {
        // Given: Mock APIs
        WireMock.configureFor("localhost", 8081);
        stubFor(post(urlEqualTo("/api/users"))
            .willReturn(aResponse()
                .withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"id\":\"550e8400-e29b-41d4-a716-446655440000\",\"username\":\"thomas.goetz\",\"firstName\":\"Thomas\",\"lastName\":\"Goetz\"}")));

        WireMock.configureFor("localhost", 8083);
        stubFor(post(urlEqualTo("/api/speakers"))
            .willReturn(aResponse()
                .withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"id\":\"" + UUID.randomUUID() + "\",\"userId\":\"550e8400-e29b-41d4-a716-446655440000\",\"isActive\":true}")));

        // When: Run migration
        JobParameters params = new JobParametersBuilder()
            .addLong("timestamp", System.currentTimeMillis())
            .toJobParameters();

        jobLauncher.run(userSpeakerMigrationJob, params);

        // Then: User ID mappings stored (username → UUID)
        assertEquals(5, idMappingRepository.countByEntityType("User"));

        // Then: Can lookup User UUID by username
        assertNotNull(idMappingRepository.findByEntityTypeAndLegacyId("User", "thomas.goetz"));
    }
}
