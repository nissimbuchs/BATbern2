package ch.batbern.migration.orchestrator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * Migration Job Orchestrator
 *
 * Executes all migration jobs in sequential order with dependency validation.
 *
 * Execution Order:
 * 1. Company Migration (no dependencies)
 * 2. Event Migration (no dependencies)
 * 3. User+Speaker Migration (depends on Company, creates both User and Speaker entities)
 * 4. Session Migration (depends on Event + User)
 * 5. File Migration (no dependencies, runs last)
 *
 * Story: 3.2.1 - Migration Tool Implementation
 * AC: 1-4 (Batch Processing Infrastructure)
 */
@Component
public class MigrationJobOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(MigrationJobOrchestrator.class);

    @Autowired
    private JobLauncher jobLauncher;

    @Autowired
    @Qualifier("companyMigrationJob")
    private Job companyMigrationJob;

    @Autowired
    @Qualifier("eventMigrationJob")
    private Job eventMigrationJob;

    @Autowired
    @Qualifier("userSpeakerMigrationJob")
    private Job userSpeakerMigrationJob;

    @Autowired
    @Qualifier("sessionMigrationJob")
    private Job sessionMigrationJob;

    @Autowired
    @Qualifier("fileMigrationJob")
    private Job fileMigrationJob;

    /**
     * Execute all migration jobs in sequential order
     *
     * @return Overall migration status
     * @throws Exception if any job fails
     */
    public MigrationResult executeFullMigration() throws Exception {
        log.info("Starting full migration workflow...");
        MigrationResult result = new MigrationResult();
        long startTime = System.currentTimeMillis();

        try {
            // Step 1: Company Migration (AC 17-20)
            log.info("Step 1/6: Migrating companies...");
            JobExecution companyExecution = runJob(companyMigrationJob, "Company");
            result.addJobExecution("Company", companyExecution);
            validateJobSuccess(companyExecution, "Company");

            // Step 2: Event Migration (AC 5-8)
            log.info("Step 2/6: Migrating events...");
            JobExecution eventExecution = runJob(eventMigrationJob, "Event");
            result.addJobExecution("Event", eventExecution);
            validateJobSuccess(eventExecution, "Event");

            // Step 3: User+Speaker Migration (AC 9-12, depends on Company)
            log.info("Step 3/5: Migrating users and speakers...");
            JobExecution userSpeakerExecution = runJob(userSpeakerMigrationJob, "UserSpeaker");
            result.addJobExecution("UserSpeaker", userSpeakerExecution);
            validateJobSuccess(userSpeakerExecution, "UserSpeaker");

            // Step 4: Session Migration (AC 6, depends on Event + User)
            log.info("Step 4/5: Migrating sessions...");
            JobExecution sessionExecution = runJob(sessionMigrationJob, "Session");
            result.addJobExecution("Session", sessionExecution);
            validateJobSuccess(sessionExecution, "Session");

            // Step 5: File Migration (AC 13-16)
            log.info("Step 5/5: Migrating files...");
            JobExecution fileExecution = runJob(fileMigrationJob, "File");
            result.addJobExecution("File", fileExecution);
            validateJobSuccess(fileExecution, "File");

            long endTime = System.currentTimeMillis();
            result.setTotalDurationMs(endTime - startTime);
            result.setSuccess(true);

            log.info("Full migration workflow completed successfully in {}ms", result.getTotalDurationMs());
            return result;

        } catch (Exception e) {
            long endTime = System.currentTimeMillis();
            result.setTotalDurationMs(endTime - startTime);
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());

            log.error("Migration workflow failed: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Run a single migration job with unique timestamp parameter
     */
    private JobExecution runJob(Job job, String jobName) throws Exception {
        JobParameters params = new JobParametersBuilder()
            .addLong("timestamp", System.currentTimeMillis())
            .addString("jobName", jobName)
            .toJobParameters();

        JobExecution execution = jobLauncher.run(job, params);

        log.info("{} migration completed with status: {}", jobName, execution.getStatus());
        log.info("{} - Read: {}, Written: {}, Skipped: {}",
            jobName,
            execution.getStepExecutions().stream().mapToLong(se -> se.getReadCount()).sum(),
            execution.getStepExecutions().stream().mapToLong(se -> se.getWriteCount()).sum(),
            execution.getStepExecutions().stream().mapToLong(se -> se.getSkipCount()).sum()
        );

        return execution;
    }

    /**
     * Validate job execution completed successfully
     */
    private void validateJobSuccess(JobExecution execution, String jobName) {
        if (execution.getStatus() != BatchStatus.COMPLETED) {
            throw new IllegalStateException(
                String.format("%s migration failed with status: %s", jobName, execution.getStatus())
            );
        }
    }

    /**
     * Migration result containing all job executions and overall status
     */
    public static class MigrationResult {
        private boolean success;
        private String errorMessage;
        private long totalDurationMs;
        private java.util.Map<String, JobExecution> jobExecutions = new java.util.LinkedHashMap<>();

        public void addJobExecution(String jobName, JobExecution execution) {
            jobExecutions.put(jobName, execution);
        }

        // Getters and setters
        public boolean isSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public void setErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
        }

        public long getTotalDurationMs() {
            return totalDurationMs;
        }

        public void setTotalDurationMs(long totalDurationMs) {
            this.totalDurationMs = totalDurationMs;
        }

        public java.util.Map<String, JobExecution> getJobExecutions() {
            return jobExecutions;
        }

        public void setJobExecutions(java.util.Map<String, JobExecution> jobExecutions) {
            this.jobExecutions = jobExecutions;
        }
    }
}
