package ch.batbern.migration.listener;

import ch.batbern.migration.model.target.MigrationError;
import ch.batbern.migration.repository.MigrationErrorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.SkipListener;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.annotation.BeforeStep;
import org.springframework.stereotype.Component;

/**
 * Migration Skip Listener
 *
 * Logs skipped items to migration_errors table for manual review.
 * Implements Spring Batch SkipListener to capture skip events during processing.
 *
 * Story: 3.2.1 - Migration Tool Implementation
 * AC: Task 8 - Error Handling & Retry Logic
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MigrationSkipListener<T, S> implements SkipListener<T, S> {

    private final MigrationErrorRepository errorRepository;

    private Long jobExecutionId;
    private String stepName;

    @BeforeStep
    public void beforeStep(StepExecution stepExecution) {
        this.jobExecutionId = stepExecution.getJobExecutionId();
        this.stepName = stepExecution.getStepName();
    }

    /**
     * Called when an item fails during read phase
     */
    @Override
    public void onSkipInRead(Throwable throwable) {
        log.warn("Skipped item during READ in step {}: {}", stepName, throwable.getMessage());

        MigrationError error = createError(
            "UNKNOWN", // No item available during read failures
            "READ",
            throwable
        );
        errorRepository.save(error);
    }

    /**
     * Called when an item fails during write phase
     */
    @Override
    public void onSkipInWrite(S item, Throwable throwable) {
        log.warn("Skipped item during WRITE in step {}: item={}, error={}",
            stepName, item, throwable.getMessage());

        String legacyId = extractLegacyId(item);
        MigrationError error = createError(legacyId, "WRITE", throwable);
        errorRepository.save(error);
    }

    /**
     * Called when an item fails during process phase
     */
    @Override
    public void onSkipInProcess(T item, Throwable throwable) {
        log.warn("Skipped item during PROCESS in step {}: item={}, error={}",
            stepName, item, throwable.getMessage());

        String legacyId = extractLegacyId(item);
        MigrationError error = createError(legacyId, "PROCESS", throwable);
        errorRepository.save(error);
    }

    /**
     * Create MigrationError entity from exception
     */
    private MigrationError createError(String legacyId, String phase, Throwable throwable) {
        MigrationError error = new MigrationError();
        error.setJobExecutionId(jobExecutionId);
        error.setEntityType(extractEntityType(stepName));
        error.setLegacyId(legacyId);
        error.setPhase(phase);
        error.setErrorMessage(throwable.getMessage());
        error.setStackTrace(getStackTrace(throwable));
        error.setResolved(false);
        return error;
    }

    /**
     * Extract entity type from step name
     * Example: "companyMigrationStep" -> "Company"
     */
    private String extractEntityType(String stepName) {
        if (stepName == null) return "UNKNOWN";

        if (stepName.contains("company")) return "Company";
        if (stepName.contains("event")) return "Event";
        if (stepName.contains("user") || stepName.contains("speaker")) return "User";
        if (stepName.contains("session")) return "Session";
        if (stepName.contains("file")) return "File";

        return "UNKNOWN";
    }

    /**
     * Extract legacy ID from item (best effort)
     */
    private String extractLegacyId(Object item) {
        if (item == null) return "UNKNOWN";

        // Use reflection to get ID field if available
        try {
            var idField = item.getClass().getDeclaredField("id");
            idField.setAccessible(true);
            Object id = idField.get(item);
            return id != null ? id.toString() : "UNKNOWN";
        } catch (Exception e) {
            // Fallback to toString
            return item.toString();
        }
    }

    /**
     * Get stack trace as string
     */
    private String getStackTrace(Throwable throwable) {
        StringBuilder sb = new StringBuilder();
        sb.append(throwable.getClass().getName()).append(": ").append(throwable.getMessage()).append("\n");

        for (StackTraceElement element : throwable.getStackTrace()) {
            sb.append("\tat ").append(element.toString()).append("\n");

            // Limit to first 10 stack frames
            if (sb.length() > 2000) {
                sb.append("\t... (truncated)");
                break;
            }
        }

        return sb.toString();
    }
}
