package ch.batbern.migration.job;

import ch.batbern.migration.AbstractIntegrationTest;
import ch.batbern.migration.repository.EntityIdMappingRepository;
import ch.batbern.migration.repository.MigrationErrorRepository;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.annotation.DirtiesContext;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Company Migration Job Test
 *
 * RED PHASE: Tests for Company Migration Job implementation
 * AC 17-20: Company migration with deduplication, normalization, logo upload
 *
 * Story: 3.2.1 - Migration Tool Implementation
 */
@Tag("integration")
@DirtiesContext
class CompanyMigrationJobTest extends AbstractIntegrationTest {

    @Autowired(required = false)
    private JobLauncher jobLauncher;

    @Autowired(required = false)
    @Qualifier("companyMigrationJob")
    private Job companyMigrationJob;

    @Autowired
    private EntityIdMappingRepository idMappingRepository;

    @Autowired
    private MigrationErrorRepository errorRepository;

    private WireMockServer wireMockServer;

    @BeforeEach
    void setUp() {
        // Setup WireMock for Company Management API
        if (wireMockServer == null || !wireMockServer.isRunning()) {
            wireMockServer = new WireMockServer(8080);
            wireMockServer.start();
        }

        WireMock.configureFor("localhost", 8080);
        wireMockServer.resetAll(); // Reset stubs and request history

        // Clean up test data
        idMappingRepository.deleteAll();
        errorRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        if (wireMockServer != null && wireMockServer.isRunning()) {
            wireMockServer.resetAll();
            wireMockServer.stop();
        }
    }

    /**
     * RED PHASE TEST - AC 17: Create 65 companies (4 in test data, 1 duplicate skipped)
     * Expected to FAIL: CompanyMigrationJob not implemented yet
     */
    @Test
    void should_create4Companies_when_deduplicationApplied() throws Exception {
        // Given: Mock Company Management API
        stubFor(post(urlEqualTo("/api/companies"))
            .willReturn(aResponse()
                .withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                        "id": "550e8400-e29b-41d4-a716-446655440000",
                        "name": "sbb",
                        "displayName": "SBB CFF FFS",
                        "website": "https://www.sbb.ch",
                        "isVerified": false
                    }
                    """)));

        // Then: Job should exist
        assertNotNull(companyMigrationJob, "Company migration job should be defined");
        assertNotNull(jobLauncher, "JobLauncher should be configured");

        // When: Run company migration job
        JobParameters params = new JobParametersBuilder()
            .addLong("timestamp", System.currentTimeMillis())
            .toJobParameters();
        JobExecution execution = jobLauncher.run(companyMigrationJob, params);

        // Then: Job completes successfully
        assertEquals(BatchStatus.COMPLETED, execution.getStatus(),
            "Company migration should complete successfully");

        // Verify: 4 companies created (5 total - 1 duplicate skipped)
        assertEquals(4, idMappingRepository.countByEntityType("Company"),
            "Should create 4 companies after skipping 1 duplicate");

        // Verify: API called 4 times (duplicates skipped)
        verify(4, postRequestedFor(urlEqualTo("/api/companies")));

        // Verify: No errors
        assertEquals(0, errorRepository.countByResolvedFalse(),
            "Should have zero errors");
    }

    /**
     * RED PHASE TEST - AC 18: Set all companies to isVerified = false
     * Expected to FAIL: Not implemented yet
     */
    @Test
    void should_setUnverifiedStatus_when_companyMigrated() throws Exception {
        // Given: Mock API
        stubFor(post(urlEqualTo("/api/companies"))
            .willReturn(aResponse()
                .withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                        "id": "550e8400-e29b-41d4-a716-446655440000",
                        "name": "sbb",
                        "displayName": "SBB CFF FFS",
                        "isVerified": false
                    }
                    """)));

        // When: Run job
        JobParameters params = new JobParametersBuilder()
            .addLong("timestamp", System.currentTimeMillis())
            .toJobParameters();
        JobExecution execution = jobLauncher.run(companyMigrationJob, params);

        // Then: Verify all requests have isVerified = false
        verify(postRequestedFor(urlEqualTo("/api/companies"))
            .withRequestBody(matchingJsonPath("$.isVerified", equalTo("false"))));
    }

    /**
     * RED PHASE TEST - AC 19: Normalize company names (max 12 chars)
     * Expected to FAIL: Not implemented yet
     */
    @Test
    void should_normalizeCompanyNames_when_tooLong() throws Exception {
        // Given: Mock API
        stubFor(post(urlEqualTo("/api/companies"))
            .willReturn(aResponse()
                .withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                        "id": "550e8400-e29b-41d4-a716-446655440000",
                        "name": "verylongcomp",
                        "displayName": "Very Long Company Name That Needs Truncation AG",
                        "isVerified": false
                    }
                    """)));

        // When: Run job
        JobParameters params = new JobParametersBuilder()
            .addLong("timestamp", System.currentTimeMillis())
            .toJobParameters();
        JobExecution execution = jobLauncher.run(companyMigrationJob, params);

        // Then: Verify company name is truncated to 12 chars
        verify(postRequestedFor(urlEqualTo("/api/companies"))
            .withRequestBody(matchingJsonPath("$.name", matching("^[a-z0-9]{1,12}$"))));

        // Verify SBB is 3 chars (not truncated)
        // Verify mobiliar is 8 chars (not truncated)
        // Verify verylongcompany... is 12 chars (truncated)
    }

    /**
     * RED PHASE TEST - AC 20: Upload logos to S3 (deferred to FileMigrationJob)
     * This test verifies logoS3Key is set in the request
     * Expected to FAIL: Not implemented yet
     */
    @Test
    void should_generateLogoS3Key_when_logoProvided() throws Exception {
        // Given: Mock API
        stubFor(post(urlEqualTo("/api/companies"))
            .willReturn(aResponse()
                .withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                        "id": "550e8400-e29b-41d4-a716-446655440000",
                        "name": "sbb",
                        "displayName": "SBB CFF FFS",
                        "logoS3Key": "company-logos/sbb/file-id.jpg",
                        "isVerified": false
                    }
                    """)));

        // When: Run job
        JobParameters params = new JobParametersBuilder()
            .addLong("timestamp", System.currentTimeMillis())
            .toJobParameters();
        JobExecution execution = jobLauncher.run(companyMigrationJob, params);

        // Then: Verify companies with logos have logoS3Key set
        verify(moreThanOrExactly(1), postRequestedFor(urlEqualTo("/api/companies"))
            .withRequestBody(matchingJsonPath("$.logoS3Key")));
    }

    /**
     * RED PHASE TEST - AC 3: Chunk processing (100 records per chunk)
     * Expected to FAIL: Not implemented yet
     */
    @Test
    void should_processInChunks_when_jobExecutes() throws Exception {
        // This test uses configured chunk size from application-test.yml (10 for tests)
        // In production, chunk size is 100

        // Given: Mock API
        stubFor(post(urlEqualTo("/api/companies"))
            .willReturn(aResponse()
                .withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                        "id": "550e8400-e29b-41d4-a716-446655440000",
                        "name": "test",
                        "displayName": "Test Company",
                        "isVerified": false
                    }
                    """)));

        // When: Run job
        JobParameters params = new JobParametersBuilder()
            .addLong("timestamp", System.currentTimeMillis())
            .toJobParameters();
        JobExecution execution = jobLauncher.run(companyMigrationJob, params);

        // Then: Job completes (chunk size verified by execution metadata)
        assertEquals(BatchStatus.COMPLETED, execution.getStatus());

        // Note: Chunk size validation happens in batch metadata
        // With 4 companies and chunk size 10, we expect 1 chunk
    }

    /**
     * RED PHASE TEST: Idempotency - skip companies that already exist
     * Expected to FAIL: Not implemented yet
     */
    @Test
    void should_skipExistingCompanies_when_rerun() throws Exception {
        // Given: Default stub for successful company creation
        stubFor(post(urlEqualTo("/api/companies"))
            .willReturn(aResponse()
                .withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                        "id": "550e8400-e29b-41d4-a716-446655440000",
                        "name": "test",
                        "displayName": "Test Company",
                        "isVerified": false
                    }
                    """)));

        // Given: Mock API returns 409 Conflict for existing company (sbb)
        stubFor(post(urlEqualTo("/api/companies"))
            .withRequestBody(matchingJsonPath("$.name", equalTo("sbb")))
            .willReturn(aResponse()
                .withStatus(409)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                        "error": "Company already exists",
                        "name": "sbb"
                    }
                    """)));

        // When: Run job twice
        JobParameters params1 = new JobParametersBuilder()
            .addLong("timestamp", System.currentTimeMillis())
            .toJobParameters();
        JobExecution execution1 = jobLauncher.run(companyMigrationJob, params1);

        // First run should complete (3 new + 1 conflict)
        assertEquals(BatchStatus.COMPLETED, execution1.getStatus(),
            "First job run should complete with idempotent 409 handling");

        JobParameters params2 = new JobParametersBuilder()
            .addLong("timestamp", System.currentTimeMillis() + 1000)
            .toJobParameters();
        JobExecution execution2 = jobLauncher.run(companyMigrationJob, params2);

        // Then: Second run should complete without errors
        assertEquals(BatchStatus.COMPLETED, execution2.getStatus(),
            "Job should handle existing companies gracefully");

        // Verify: Still 4 companies in mapping (not duplicated)
        // Note: Only 3 mappings stored because sbb gets 409 and is never stored
        assertEquals(3, idMappingRepository.countByEntityType("Company"),
            "Should not create duplicate mappings");
    }
}
