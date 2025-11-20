package ch.batbern.migration;

import ch.batbern.migration.repository.EntityIdMappingRepository;
import ch.batbern.migration.repository.MigrationErrorRepository;
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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Full Migration Workflow E2E Test
 *
 * Tests complete migration workflow:
 * 1. Company migration (65 companies after deduplication)
 * 2. Event migration (60 events)
 * 3. User migration (269 users)
 * 4. Speaker migration (269 speakers)
 * 5. Session migration (302 sessions)
 * 6. File migration (presentations, photos, logos)
 *
 * Story: 3.2.1 - Migration Tool Implementation
 * AC: All (1-20)
 *
 * RED PHASE: This test will FAIL until jobs are implemented
 */
@Tag("e2e")
@DirtiesContext
class FullMigrationWorkflowTest extends AbstractIntegrationTest {

    @Autowired(required = false)
    private JobLauncher jobLauncher;

    @Autowired(required = false)
    @Qualifier("companyMigrationJob")
    private Job companyMigrationJob;

    @Autowired(required = false)
    @Qualifier("eventMigrationJob")
    private Job eventMigrationJob;

    @Autowired(required = false)
    @Qualifier("userMigrationJob")
    private Job userMigrationJob;

    @Autowired(required = false)
    @Qualifier("speakerMigrationJob")
    private Job speakerMigrationJob;

    @Autowired(required = false)
    @Qualifier("sessionMigrationJob")
    private Job sessionMigrationJob;

    @Autowired(required = false)
    @Qualifier("fileMigrationJob")
    private Job fileMigrationJob;

    @Autowired
    private EntityIdMappingRepository idMappingRepository;

    @Autowired
    private MigrationErrorRepository errorRepository;

    /**
     * RED PHASE TEST
     * Expected to FAIL: Jobs not implemented yet
     *
     * AC 1-4: Batch processing infrastructure
     * AC 5-8: Event migration (60 events)
     * AC 9-12: Speaker migration (269 speakers)
     * AC 13-16: File migration
     * AC 17-20: Company migration (65 companies)
     */
    @Test
    void should_migrateAllEntities_when_fullWorkflowExecutes() throws Exception {
        // Given: Test data exists (will be created in test resources)
        assertNotNull(jobLauncher, "JobLauncher should be configured");

        // Then: All jobs should be defined (will fail here initially)
        assertNotNull(companyMigrationJob, "Company migration job should exist");
        assertNotNull(eventMigrationJob, "Event migration job should exist");
        assertNotNull(userMigrationJob, "User migration job should exist");
        assertNotNull(speakerMigrationJob, "Speaker migration job should exist");
        assertNotNull(sessionMigrationJob, "Session migration job should exist");
        assertNotNull(fileMigrationJob, "File migration job should exist");

        // When: Run Company migration job (AC 17-20)
        JobParameters companyParams = new JobParametersBuilder()
            .addLong("timestamp", System.currentTimeMillis())
            .toJobParameters();
        JobExecution companyExecution = jobLauncher.run(companyMigrationJob, companyParams);

        // Then: Company job completes successfully
        assertEquals(BatchStatus.COMPLETED, companyExecution.getStatus(),
            "Company migration should complete successfully");

        // Verify: 65 companies created (AC 17)
        assertEquals(65, idMappingRepository.countByEntityType("Company"),
            "Should create 65 companies after deduplication");

        // When: Run Event migration job (AC 5-8)
        JobParameters eventParams = new JobParametersBuilder()
            .addLong("timestamp", System.currentTimeMillis())
            .toJobParameters();
        JobExecution eventExecution = jobLauncher.run(eventMigrationJob, eventParams);

        // Then: Event job completes successfully
        assertEquals(BatchStatus.COMPLETED, eventExecution.getStatus(),
            "Event migration should complete successfully");

        // Verify: 60 events created (AC 5)
        assertEquals(60, idMappingRepository.countByEntityType("Event"),
            "Should create 60 historical events");

        // When: Run User migration job (AC 9-12, depends on Company)
        JobParameters userParams = new JobParametersBuilder()
            .addLong("timestamp", System.currentTimeMillis())
            .toJobParameters();
        JobExecution userExecution = jobLauncher.run(userMigrationJob, userParams);

        // Then: User job completes successfully
        assertEquals(BatchStatus.COMPLETED, userExecution.getStatus(),
            "User migration should complete successfully");

        // Verify: 269 users created (AC 9)
        assertEquals(269, idMappingRepository.countByEntityType("User"),
            "Should create 269 unique users");

        // When: Run Speaker migration job (AC 9-12, depends on User)
        JobParameters speakerParams = new JobParametersBuilder()
            .addLong("timestamp", System.currentTimeMillis())
            .toJobParameters();
        JobExecution speakerExecution = jobLauncher.run(speakerMigrationJob, speakerParams);

        // Then: Speaker job completes successfully
        assertEquals(BatchStatus.COMPLETED, speakerExecution.getStatus(),
            "Speaker migration should complete successfully");

        // Verify: 269 speakers created (AC 9)
        assertEquals(269, idMappingRepository.countByEntityType("Speaker"),
            "Should create 269 speaker profiles");

        // When: Run Session migration job (AC 6, depends on Event + User)
        JobParameters sessionParams = new JobParametersBuilder()
            .addLong("timestamp", System.currentTimeMillis())
            .toJobParameters();
        JobExecution sessionExecution = jobLauncher.run(sessionMigrationJob, sessionParams);

        // Then: Session job completes successfully
        assertEquals(BatchStatus.COMPLETED, sessionExecution.getStatus(),
            "Session migration should complete successfully");

        // Verify: 302 sessions created (AC 6)
        assertEquals(302, idMappingRepository.countByEntityType("Session"),
            "Should create 302 event sessions");

        // When: Run File migration job (AC 13-16)
        JobParameters fileParams = new JobParametersBuilder()
            .addLong("timestamp", System.currentTimeMillis())
            .toJobParameters();
        JobExecution fileExecution = jobLauncher.run(fileMigrationJob, fileParams);

        // Then: File job completes successfully
        assertEquals(BatchStatus.COMPLETED, fileExecution.getStatus(),
            "File migration should complete successfully");

        // Verify: Zero errors (AC validation)
        assertEquals(0, errorRepository.countByResolvedFalse(),
            "Should have zero unresolved errors after migration");

        // Verify: All entity types have mappings
        assertTrue(idMappingRepository.countByEntityType("Company") > 0, "Companies should be migrated");
        assertTrue(idMappingRepository.countByEntityType("Event") > 0, "Events should be migrated");
        assertTrue(idMappingRepository.countByEntityType("User") > 0, "Users should be migrated");
        assertTrue(idMappingRepository.countByEntityType("Speaker") > 0, "Speakers should be migrated");
        assertTrue(idMappingRepository.countByEntityType("Session") > 0, "Sessions should be migrated");
    }

    /**
     * Test parallel execution capability
     * AC4: Company + Event should run in parallel (no dependencies)
     */
    @Test
    void should_runJobsInParallel_when_noDependencies() {
        // This test will be implemented when parallel orchestration is added (Task 7)
        // For now, we verify jobs can run independently
        assertNotNull(jobLauncher, "JobLauncher should be configured");
    }
}
