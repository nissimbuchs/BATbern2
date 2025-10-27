package ch.batbern.companyuser.service;

import ch.batbern.companyuser.domain.Logo;
import ch.batbern.companyuser.domain.LogoStatus;
import ch.batbern.companyuser.repository.LogoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;

import java.time.Instant;
import java.util.List;

/**
 * Scheduled cleanup service for orphaned logo uploads
 * Story 1.16.3: Generic File Upload Service
 * ADR-002: Generic File Upload Service Architecture
 *
 * Cleanup Rules:
 * - PENDING uploads > 24 hours old: Deleted (user never uploaded file)
 * - CONFIRMED uploads > 7 days old: Deleted (entity never created)
 * - ASSOCIATED uploads: Kept indefinitely (in use)
 *
 * Runs daily at 2 AM to minimize impact on production traffic
 */
@Service
@Slf4j
public class LogoCleanupService {

    private final LogoRepository logoRepository;
    private final S3Client s3Client;
    private final String bucketName;

    public LogoCleanupService(
            LogoRepository logoRepository,
            S3Client s3Client,
            @org.springframework.beans.factory.annotation.Value("${aws.s3.bucket-name:batbern-development-company-logos}") String bucketName) {
        this.logoRepository = logoRepository;
        this.s3Client = s3Client;
        this.bucketName = bucketName;
    }

    /**
     * Scheduled cleanup job - runs daily at 2 AM
     * Cron expression: "0 0 2 * * *"
     * - Second: 0
     * - Minute: 0
     * - Hour: 2 (2 AM)
     * - Day of month: * (every day)
     * - Month: * (every month)
     * - Day of week: * (every day of week)
     */
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void cleanupOrphanedLogos() {
        log.info("Starting scheduled cleanup of orphaned logos");

        Instant now = Instant.now();
        int totalDeleted = 0;

        // Cleanup PENDING logos (> 24 hours old)
        totalDeleted += cleanupLogosByStatus(LogoStatus.PENDING, now);

        // Cleanup CONFIRMED logos (> 7 days old)
        totalDeleted += cleanupLogosByStatus(LogoStatus.CONFIRMED, now);

        log.info("Completed cleanup of orphaned logos. Total deleted: {}", totalDeleted);
    }

    /**
     * Cleanup logos by status and expiration time
     *
     * @param status Logo status to clean up
     * @param now    Current timestamp
     * @return Number of logos deleted
     */
    private int cleanupLogosByStatus(LogoStatus status, Instant now) {
        List<Logo> expiredLogos = logoRepository.findByStatusAndExpiresAtBefore(status, now);

        if (expiredLogos.isEmpty()) {
            log.info("No expired logos found for status: {}", status);
            return 0;
        }

        log.info("Found {} expired logos with status: {}", expiredLogos.size(), status);

        int deleted = 0;
        for (Logo logo : expiredLogos) {
            try {
                // Delete S3 file
                deleteS3Object(logo.getS3Key());

                // Delete database record
                logoRepository.delete(logo);

                deleted++;
                log.debug("Cleaned up expired logo: uploadId={}, status={}, s3Key={}",
                        logo.getUploadId(), status, logo.getS3Key());
            } catch (Exception e) {
                log.error("Failed to cleanup logo: uploadId={}, s3Key={}",
                        logo.getUploadId(), logo.getS3Key(), e);
                // Continue with next logo - don't let one failure stop cleanup
            }
        }

        log.info("Cleaned up {} of {} expired logos with status: {}",
                deleted, expiredLogos.size(), status);

        return deleted;
    }

    /**
     * Delete S3 object
     * Gracefully handles failures to prevent cleanup job from breaking
     */
    private void deleteS3Object(String s3Key) {
        try {
            DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .build();

            s3Client.deleteObject(deleteRequest);

            log.debug("Deleted S3 object: {}", s3Key);
        } catch (Exception e) {
            log.warn("Failed to delete S3 object: {}", s3Key, e);
            // Don't throw - allow cleanup to continue with other files
        }
    }

    /**
     * Manual trigger for cleanup (for testing or emergency use)
     * Can be called via admin endpoint if needed
     */
    public void triggerManualCleanup() {
        log.warn("Manual cleanup triggered");
        cleanupOrphanedLogos();
    }

    /**
     * Get cleanup statistics
     * Returns counts of logos in each status
     */
    public CleanupStatistics getCleanupStatistics() {
        long pendingCount = logoRepository.countByStatus(LogoStatus.PENDING);
        long confirmedCount = logoRepository.countByStatus(LogoStatus.CONFIRMED);
        long associatedCount = logoRepository.countByStatus(LogoStatus.ASSOCIATED);

        Instant now = Instant.now();
        long expiredPending = logoRepository.findByStatusAndExpiresAtBefore(LogoStatus.PENDING, now).size();
        long expiredConfirmed = logoRepository.findByStatusAndExpiresAtBefore(LogoStatus.CONFIRMED, now).size();

        return new CleanupStatistics(
                pendingCount,
                confirmedCount,
                associatedCount,
                expiredPending,
                expiredConfirmed
        );
    }

    /**
     * DTO for cleanup statistics
     */
    public record CleanupStatistics(
            long pendingCount,
            long confirmedCount,
            long associatedCount,
            long expiredPendingCount,
            long expiredConfirmedCount
    ) {
    }
}
