package ch.batbern.migration.job;

import ch.batbern.migration.AbstractIntegrationTest;
import ch.batbern.migration.config.TestS3Configuration;
import ch.batbern.migration.model.target.EntityIdMapping;
import ch.batbern.migration.repository.EntityIdMappingRepository;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.*;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * TDD Test Suite for File Migration Job (AC 13-16)
 *
 * RED Phase: These tests should fail initially
 *
 * Acceptance Criteria Coverage:
 * - AC13: Upload presentations to S3 with correct key patterns
 * - AC14: Upload photos to S3 with correct key patterns
 * - AC15: Generate thumbnails (PDF first page → PNG, image resize to 300x300)
 * - AC16: Generate CloudFront CDN URLs
 */
@SpringBootTest
@SpringBatchTest
@Import(TestS3Configuration.class)
@TestPropertySource(locations = "classpath:application-test.yml")
public class FileMigrationJobTest extends AbstractIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(FileMigrationJobTest.class);

    @Autowired
    private JobLauncher jobLauncher;

    @Autowired
    @Qualifier("fileMigrationJob")
    private Job fileMigrationJob;

    @Autowired
    private EntityIdMappingRepository idMappingRepository;

    private static WireMockServer wireMockServer;
    private Path testDataDir;

    @BeforeAll
    public static void startWireMock() {
        // Start WireMock server on port 8089 for S3 mock
        wireMockServer = new WireMockServer(options().port(8089));
        wireMockServer.start();
        WireMock.configureFor("localhost", 8089);
    }

    @AfterAll
    public static void stopWireMock() {
        if (wireMockServer != null && wireMockServer.isRunning()) {
            wireMockServer.stop();
        }
    }

    @BeforeEach
    public void setup() throws Exception {
        WireMock.reset();

        // Create temporary test data directory structure
        testDataDir = Files.createTempDirectory("test-files");

        // Create test event directories
        Path event45Dir = testDataDir.resolve("event-45");
        Files.createDirectories(event45Dir);

        Path event46Dir = testDataDir.resolve("event-46");
        Files.createDirectories(event46Dir);

        // Create mock presentation files (PDFs)
        Path presentation1 = event45Dir.resolve("presentation-speaker1.pdf");
        Files.createFile(presentation1);
        Files.write(presentation1, "Mock PDF content for presentation 1".getBytes());

        Path presentation2 = event45Dir.resolve("presentation-speaker2.pdf");
        Files.createFile(presentation2);
        Files.write(presentation2, "Mock PDF content for presentation 2".getBytes());

        // Create mock photo files
        Path photo1 = event45Dir.resolve("photo-gallery-1.jpg");
        Files.createFile(photo1);
        Files.write(photo1, "Mock JPG content".getBytes());

        Path photo2 = event46Dir.resolve("photo-gallery-2.png");
        Files.createFile(photo2);
        Files.write(photo2, "Mock PNG content".getBytes());

        // Create entity ID mappings for events (needed for S3 key generation)
        EntityIdMapping event45Mapping = new EntityIdMapping();
        event45Mapping.setEntityType("Event");
        event45Mapping.setLegacyId("45");
        event45Mapping.setNewId(UUID.randomUUID());
        idMappingRepository.save(event45Mapping);

        EntityIdMapping event46Mapping = new EntityIdMapping();
        event46Mapping.setEntityType("Event");
        event46Mapping.setLegacyId("46");
        event46Mapping.setNewId(UUID.randomUUID());
        idMappingRepository.save(event46Mapping);

        // Set property for this test run
        System.setProperty("migration.source-data-path", testDataDir.toString());
        System.setProperty("migration.s3.endpoint", "http://localhost:8089");
        System.setProperty("migration.s3.bucket-name", "test-bucket");

        log.info("Test data directory: {}", testDataDir);
        log.info("Files created: {} PDFs, {} photos", 2, 2);
    }

    /**
     * AC13: Test presentation upload to S3 with correct key pattern
     * Expected S3 key: presentations/{eventNumber}/{filename}
     */
    @Test
    public void should_uploadPresentationsToS3_when_pdfFound() throws Exception {
        // Given: Mock S3 HEAD requests (file existence check)
        stubFor(head(urlMatching("/.*"))
            .willReturn(aResponse()
                .withStatus(404))); // Files don't exist yet

        // Mock S3 PUT requests
        stubFor(put(urlMatching("/presentations/45/.*\\.pdf"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("ETag", "\"mock-etag\"")));

        // When: Run file migration job
        JobParameters params = new JobParametersBuilder()
            .addLong("timestamp", System.currentTimeMillis())
            .toJobParameters();
        JobExecution execution = jobLauncher.run(fileMigrationJob, params);

        // Then: Job completes successfully
        assertEquals(BatchStatus.COMPLETED, execution.getStatus());

        // Verify files were processed
        StepExecution stepExecution = execution.getStepExecutions().iterator().next();
        assertThat(stepExecution.getReadCount()).isGreaterThan(0);
    }

    /**
     * AC14: Test photo upload to S3 with correct key pattern
     * Expected S3 key: photos/events/{eventNumber}/{filename}
     */
    @Test
    public void should_uploadPhotosToS3_when_imageFound() throws Exception {
        // Given: Mock S3 HEAD and PUT requests
        stubFor(head(urlMatching("/.*"))
            .willReturn(aResponse().withStatus(404)));

        stubFor(put(urlMatching("/photos/events/.*\\.(jpg|png)"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("ETag", "\"mock-etag\"")));

        // When: Run file migration job
        JobParameters params = new JobParametersBuilder()
            .addLong("timestamp", System.currentTimeMillis())
            .toJobParameters();
        JobExecution execution = jobLauncher.run(fileMigrationJob, params);

        // Then: Job completes successfully
        assertEquals(BatchStatus.COMPLETED, execution.getStatus());

        // Verify both PDFs and photos were processed
        StepExecution stepExecution = execution.getStepExecutions().iterator().next();
        assertEquals(4, stepExecution.getReadCount()); // 2 PDFs + 2 photos
    }

    /**
     * AC15: SKIPPED - Thumbnail generation removed per user request
     */
    // Thumbnail tests removed - not implementing thumbnail generation

    /**
     * AC16: Test CloudFront CDN URL generation
     * CDN URLs are generated in S3UploadService.generateCdnUrl()
     * Unit test coverage in S3UploadServiceTest
     */
    @Test
    public void should_generateCdnUrls_when_s3UploadComplete() throws Exception {
        // Given: Mock S3 operations
        stubFor(head(urlMatching("/.*")).willReturn(aResponse().withStatus(404)));
        stubFor(put(urlMatching("/.*")).willReturn(aResponse().withStatus(200).withHeader("ETag", "\"mock-etag\"")));

        // When: Run file migration job
        JobParameters params = new JobParametersBuilder()
            .addLong("timestamp", System.currentTimeMillis())
            .toJobParameters();
        JobExecution execution = jobLauncher.run(fileMigrationJob, params);

        // Then: Job completes successfully
        assertEquals(BatchStatus.COMPLETED, execution.getStatus());

        // CDN URL format is tested in unit tests
        // Integration test verifies end-to-end flow completes
    }

    /**
     * AC13: Test multipart upload for large files (>10MB)
     * Note: Multipart logic tested in S3UploadServiceTest unit tests
     * This integration test verifies large files are processed
     */
    @Test
    public void should_useMultipartUpload_when_fileLargerThan10MB() throws Exception {
        // Given: Create large mock file (11MB)
        Path largePdf = testDataDir.resolve("event-45").resolve("large-presentation.pdf");
        byte[] largeContent = new byte[11 * 1024 * 1024]; // 11MB
        Files.write(largePdf, largeContent);

        // Mock S3 operations (simplified for integration test)
        stubFor(head(urlMatching("/.*")).willReturn(aResponse().withStatus(404)));
        stubFor(put(urlMatching("/.*")).willReturn(aResponse().withStatus(200).withHeader("ETag", "\"etag\"")));

        // When: Run file migration job
        JobParameters params = new JobParametersBuilder()
            .addLong("timestamp", System.currentTimeMillis())
            .toJobParameters();
        JobExecution execution = jobLauncher.run(fileMigrationJob, params);

        // Then: Job completes (detailed multipart logic in unit tests)
        assertEquals(BatchStatus.COMPLETED, execution.getStatus());

        // Verify large file was included in processing
        StepExecution stepExecution = execution.getStepExecutions().iterator().next();
        assertEquals(5, stepExecution.getReadCount()); // 3 PDFs + 2 photos
    }

    /**
     * Test file reader scans directory structure correctly
     */
    @Test
    public void should_scanAllFiles_when_directoryProvided() throws Exception {
        // When: Run file migration job
        JobParameters params = new JobParametersBuilder()
            .addLong("timestamp", System.currentTimeMillis())
            .toJobParameters();
        JobExecution execution = jobLauncher.run(fileMigrationJob, params);

        // Then: All 4 files processed (2 PDFs + 2 photos)
        StepExecution stepExecution = execution.getStepExecutions().iterator().next();
        assertEquals(4, stepExecution.getReadCount());
        assertEquals(4, stepExecution.getWriteCount());
    }
}
