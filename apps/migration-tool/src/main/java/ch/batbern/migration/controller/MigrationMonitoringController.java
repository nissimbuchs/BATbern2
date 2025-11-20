package ch.batbern.migration.controller;

import ch.batbern.migration.orchestrator.MigrationJobOrchestrator;
import ch.batbern.migration.repository.EntityIdMappingRepository;
import ch.batbern.migration.repository.MigrationErrorRepository;
import ch.batbern.migration.service.ErrorReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * Migration Monitoring Controller
 *
 * REST endpoints for monitoring migration progress and errors.
 *
 * Story: 3.2.1 - Task 9: Progress Monitoring Dashboard
 * AC: 1 - Configure monitoring endpoints
 */
@RestController
@RequestMapping("/migration")
@RequiredArgsConstructor
@Slf4j
public class MigrationMonitoringController {

    private final MigrationJobOrchestrator orchestrator;
    private final JobExplorer jobExplorer;
    private final EntityIdMappingRepository idMappingRepository;
    private final MigrationErrorRepository errorRepository;
    private final ErrorReportService errorReportService;

    /**
     * Start full migration workflow
     *
     * POST /migration/start
     */
    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> startMigration() {
        log.info("Starting migration via API request");

        try {
            MigrationJobOrchestrator.MigrationResult result = orchestrator.executeFullMigration();

            Map<String, Object> response = new HashMap<>();
            response.put("success", result.isSuccess());
            response.put("durationMs", result.getTotalDurationMs());
            response.put("jobsExecuted", result.getJobExecutions().size());

            if (!result.isSuccess()) {
                response.put("error", result.getErrorMessage());
            }

            log.info("Migration completed: success={}, duration={}ms",
                result.isSuccess(), result.getTotalDurationMs());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Migration failed: {}", e.getMessage(), e);

            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());

            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Get migration status and progress
     *
     * GET /migration/status
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getMigrationStatus() {
        log.debug("Fetching migration status");

        Map<String, Object> status = new HashMap<>();

        // Entity counts
        Map<String, Long> entityCounts = new HashMap<>();
        entityCounts.put("companies", idMappingRepository.countByEntityType("Company"));
        entityCounts.put("events", idMappingRepository.countByEntityType("Event"));
        entityCounts.put("users", idMappingRepository.countByEntityType("User"));
        entityCounts.put("speakers", idMappingRepository.countByEntityType("Speaker"));
        entityCounts.put("sessions", idMappingRepository.countByEntityType("Session"));
        status.put("entityCounts", entityCounts);

        // Error summary
        ErrorReportService.ErrorSummary errorSummary = errorReportService.getErrorSummary();
        Map<String, Long> errors = new HashMap<>();
        errors.put("total", errorSummary.totalErrors());
        errors.put("unresolved", errorSummary.unresolvedErrors());
        errors.put("resolved", errorSummary.resolvedErrors());
        status.put("errors", errors);

        // Recent job executions
        var recentJobs = jobExplorer.findJobInstancesByJobName("companyMigrationJob", 0, 1);
        if (!recentJobs.isEmpty()) {
            var latestInstance = recentJobs.get(0);
            var executions = jobExplorer.getJobExecutions(latestInstance);
            if (!executions.isEmpty()) {
                JobExecution latestExecution = executions.iterator().next();
                Map<String, Object> lastJob = new HashMap<>();
                lastJob.put("status", latestExecution.getStatus().toString());
                lastJob.put("startTime", latestExecution.getStartTime());
                lastJob.put("endTime", latestExecution.getEndTime());
                status.put("lastJobExecution", lastJob);
            }
        }

        return ResponseEntity.ok(status);
    }

    /**
     * Get migration errors
     *
     * GET /migration/errors
     */
    @GetMapping("/errors")
    public ResponseEntity<Map<String, Object>> getErrors(
        @RequestParam(required = false) String entityType,
        @RequestParam(required = false) Long jobExecutionId
    ) {
        log.debug("Fetching migration errors: entityType={}, jobExecutionId={}", entityType, jobExecutionId);

        var errors = errorRepository.findByResolvedFalse();

        Map<String, Object> response = new HashMap<>();
        response.put("count", errors.size());
        response.put("errors", errors);

        return ResponseEntity.ok(response);
    }

    /**
     * Export errors to CSV
     *
     * GET /migration/errors/export
     */
    @GetMapping("/errors/export")
    public ResponseEntity<Map<String, Object>> exportErrors(
        @RequestParam(required = false, defaultValue = "/tmp/migration-errors.csv") String outputPath
    ) {
        log.info("Exporting migration errors to CSV: {}", outputPath);

        try {
            Path path = Paths.get(outputPath);
            int errorCount = errorReportService.generateErrorReport(path);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("errorCount", errorCount);
            response.put("outputPath", outputPath);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to export errors: {}", e.getMessage(), e);

            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());

            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Health check endpoint
     *
     * GET /migration/health
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> health = new HashMap<>();
        health.put("status", "UP");
        health.put("service", "migration-tool");
        return ResponseEntity.ok(health);
    }
}
