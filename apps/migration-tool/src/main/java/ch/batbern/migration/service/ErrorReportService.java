package ch.batbern.migration.service;

import ch.batbern.migration.model.target.MigrationError;
import ch.batbern.migration.repository.MigrationErrorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Error Report Service
 *
 * Generates CSV reports of migration errors for manual review and analysis.
 *
 * Story: 3.2.1 - Task 8: Error Handling & Retry Logic
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ErrorReportService {

    private final MigrationErrorRepository errorRepository;

    private static final String CSV_HEADER =
        "ID,Job Execution ID,Entity Type,Legacy ID,Phase,Error Message,Retry Count,Created At,Resolved,Resolved At\n";

    private static final DateTimeFormatter DATETIME_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Generate CSV report of all unresolved errors
     *
     * @param outputPath Path to write CSV file
     * @return Number of errors exported
     */
    public int generateErrorReport(Path outputPath) throws IOException {
        List<MigrationError> errors = errorRepository.findByResolvedFalse();
        return writeErrorsToCsv(errors, outputPath);
    }

    /**
     * Generate CSV report of errors for a specific job execution
     *
     * @param jobExecutionId Job execution ID to filter by
     * @param outputPath     Path to write CSV file
     * @return Number of errors exported
     */
    public int generateErrorReportForJob(Long jobExecutionId, Path outputPath) throws IOException {
        List<MigrationError> errors = errorRepository.findByJobExecutionId(jobExecutionId);
        return writeErrorsToCsv(errors, outputPath);
    }

    /**
     * Generate CSV report of errors for a specific entity type
     *
     * @param entityType Entity type to filter by (Company, Event, User, etc.)
     * @param outputPath Path to write CSV file
     * @return Number of errors exported
     */
    public int generateErrorReportForEntityType(String entityType, Path outputPath) throws IOException {
        List<MigrationError> errors = errorRepository.findByEntityType(entityType);
        return writeErrorsToCsv(errors, outputPath);
    }

    /**
     * Write errors to CSV file
     */
    private int writeErrorsToCsv(List<MigrationError> errors, Path outputPath) throws IOException {
        log.info("Writing {} errors to CSV: {}", errors.size(), outputPath);

        try (FileWriter writer = new FileWriter(outputPath.toFile())) {
            // Write header
            writer.write(CSV_HEADER);

            // Write data rows
            for (MigrationError error : errors) {
                writer.write(toCsvRow(error));
            }
        }

        log.info("Successfully exported {} errors to {}", errors.size(), outputPath);
        return errors.size();
    }

    /**
     * Convert MigrationError to CSV row
     */
    private String toCsvRow(MigrationError error) {
        return String.format("%d,%d,%s,%s,%s,\"%s\",%d,%s,%s,%s\n",
            error.getId(),
            error.getJobExecutionId() != null ? error.getJobExecutionId() : 0,
            csvEscape(error.getEntityType()),
            csvEscape(error.getLegacyId()),
            csvEscape(error.getPhase()),
            csvEscape(error.getErrorMessage()),
            error.getRetryCount() != null ? error.getRetryCount() : 0,
            error.getCreatedAt() != null ? error.getCreatedAt().format(DATETIME_FORMATTER) : "",
            error.getResolved() != null && error.getResolved() ? "true" : "false",
            error.getResolvedAt() != null ? error.getResolvedAt().format(DATETIME_FORMATTER) : ""
        );
    }

    /**
     * Escape CSV special characters
     */
    private String csvEscape(String value) {
        if (value == null) {
            return "";
        }

        // Remove newlines and truncate long messages
        String escaped = value.replaceAll("[\r\n]+", " ");
        if (escaped.length() > 200) {
            escaped = escaped.substring(0, 197) + "...";
        }

        // Escape quotes
        return escaped.replace("\"", "\"\"");
    }

    /**
     * Get summary statistics of migration errors
     */
    public ErrorSummary getErrorSummary() {
        long totalErrors = errorRepository.count();
        long unresolvedErrors = errorRepository.countByResolvedFalse();
        long resolvedErrors = totalErrors - unresolvedErrors;

        log.info("Error summary: Total={}, Unresolved={}, Resolved={}",
            totalErrors, unresolvedErrors, resolvedErrors);

        return new ErrorSummary(totalErrors, unresolvedErrors, resolvedErrors);
    }

    /**
     * Error summary statistics
     */
    public record ErrorSummary(long totalErrors, long unresolvedErrors, long resolvedErrors) {
    }
}
