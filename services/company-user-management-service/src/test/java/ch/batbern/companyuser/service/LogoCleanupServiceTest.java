package ch.batbern.companyuser.service;

import ch.batbern.companyuser.domain.Logo;
import ch.batbern.companyuser.domain.LogoStatus;
import ch.batbern.companyuser.repository.LogoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for LogoCleanupService
 * Story 1.16.3: Generic File Upload Service
 * Tests AC7: Automatic cleanup of orphaned uploads
 */
@ExtendWith(MockitoExtension.class)
class LogoCleanupServiceTest {

    @Mock
    private LogoRepository logoRepository;

    @Mock
    private S3Client s3Client;

    private LogoCleanupService logoCleanupService;

    private final String bucketName = "test-bucket";

    @BeforeEach
    void setUp() {
        logoCleanupService = new LogoCleanupService(logoRepository, s3Client, bucketName);
    }

    @Test
    @DisplayName("AC7.1: Should cleanup PENDING logos older than 24 hours")
    void shouldCleanupPendingLogos_whenOlderThan24Hours() {
        // Arrange
        Instant now = Instant.now();
        Instant expiredTime = now.minus(25, ChronoUnit.HOURS);

        Logo pendingLogo = createLogo(
                "upload-123",
                LogoStatus.PENDING,
                "logos/temp/upload-123/logo-file-1.png",
                expiredTime
        );

        when(logoRepository.findByStatusAndExpiresAtBefore(eq(LogoStatus.PENDING), any(Instant.class)))
                .thenReturn(List.of(pendingLogo));
        when(logoRepository.findByStatusAndExpiresAtBefore(eq(LogoStatus.CONFIRMED), any(Instant.class)))
                .thenReturn(Collections.emptyList());

        // Act
        logoCleanupService.cleanupOrphanedLogos();

        // Assert
        verify(logoRepository).findByStatusAndExpiresAtBefore(eq(LogoStatus.PENDING), any(Instant.class));
        verify(s3Client).deleteObject(any(DeleteObjectRequest.class));
        verify(logoRepository).delete(pendingLogo);
    }

    @Test
    @DisplayName("AC7.2: Should cleanup CONFIRMED logos older than 7 days")
    void shouldCleanupConfirmedLogos_whenOlderThan7Days() {
        // Arrange
        Instant now = Instant.now();
        Instant expiredTime = now.minus(8, ChronoUnit.DAYS);

        Logo confirmedLogo = createLogo(
                "upload-456",
                LogoStatus.CONFIRMED,
                "logos/temp/upload-456/logo-file-2.png",
                expiredTime
        );

        when(logoRepository.findByStatusAndExpiresAtBefore(eq(LogoStatus.PENDING), any(Instant.class)))
                .thenReturn(Collections.emptyList());
        when(logoRepository.findByStatusAndExpiresAtBefore(eq(LogoStatus.CONFIRMED), any(Instant.class)))
                .thenReturn(List.of(confirmedLogo));

        // Act
        logoCleanupService.cleanupOrphanedLogos();

        // Assert
        verify(logoRepository).findByStatusAndExpiresAtBefore(eq(LogoStatus.CONFIRMED), any(Instant.class));
        verify(s3Client).deleteObject(any(DeleteObjectRequest.class));
        verify(logoRepository).delete(confirmedLogo);
    }

    @Test
    @DisplayName("AC7.3: Should NOT cleanup ASSOCIATED logos")
    void shouldNotCleanupAssociatedLogos() {
        // Arrange
        when(logoRepository.findByStatusAndExpiresAtBefore(eq(LogoStatus.PENDING), any(Instant.class)))
                .thenReturn(Collections.emptyList());
        when(logoRepository.findByStatusAndExpiresAtBefore(eq(LogoStatus.CONFIRMED), any(Instant.class)))
                .thenReturn(Collections.emptyList());

        // Act
        logoCleanupService.cleanupOrphanedLogos();

        // Assert
        verify(logoRepository, times(2)).findByStatusAndExpiresAtBefore(any(LogoStatus.class), any(Instant.class));
        verify(logoRepository, never()).delete(any(Logo.class));
        verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    @DisplayName("AC7.4: Should delete S3 files for expired logos")
    void shouldDeleteS3Files_forExpiredLogos() {
        // Arrange
        Instant expiredTime = Instant.now().minus(25, ChronoUnit.HOURS);
        String s3Key = "logos/temp/upload-789/logo-file-3.png";

        Logo pendingLogo = createLogo("upload-789", LogoStatus.PENDING, s3Key, expiredTime);

        when(logoRepository.findByStatusAndExpiresAtBefore(eq(LogoStatus.PENDING), any(Instant.class)))
                .thenReturn(List.of(pendingLogo));
        when(logoRepository.findByStatusAndExpiresAtBefore(eq(LogoStatus.CONFIRMED), any(Instant.class)))
                .thenReturn(Collections.emptyList());

        // Act
        logoCleanupService.cleanupOrphanedLogos();

        // Assert
        verify(s3Client).deleteObject(argThat((DeleteObjectRequest request) ->
                request.bucket().equals(bucketName) &&
                        request.key().equals(s3Key)
        ));
    }

    @Test
    @DisplayName("AC7.5: Should delete database records for expired logos")
    void shouldDeleteDatabaseRecords_forExpiredLogos() {
        // Arrange
        Instant expiredTime = Instant.now().minus(25, ChronoUnit.HOURS);

        Logo pendingLogo = createLogo(
                "upload-101",
                LogoStatus.PENDING,
                "logos/temp/upload-101/logo.png",
                expiredTime
        );

        when(logoRepository.findByStatusAndExpiresAtBefore(eq(LogoStatus.PENDING), any(Instant.class)))
                .thenReturn(List.of(pendingLogo));
        when(logoRepository.findByStatusAndExpiresAtBefore(eq(LogoStatus.CONFIRMED), any(Instant.class)))
                .thenReturn(Collections.emptyList());

        // Act
        logoCleanupService.cleanupOrphanedLogos();

        // Assert
        verify(logoRepository).delete(pendingLogo);
    }

    @Test
    @DisplayName("AC7.6: Should continue cleanup even if S3 deletion fails")
    void shouldContinueCleanup_whenS3DeletionFails() {
        // Arrange
        Instant expiredTime = Instant.now().minus(25, ChronoUnit.HOURS);

        Logo logo1 = createLogo("upload-201", LogoStatus.PENDING, "logos/temp/upload-201/logo.png", expiredTime);
        Logo logo2 = createLogo("upload-202", LogoStatus.PENDING, "logos/temp/upload-202/logo.png", expiredTime);

        when(logoRepository.findByStatusAndExpiresAtBefore(eq(LogoStatus.PENDING), any(Instant.class)))
                .thenReturn(Arrays.asList(logo1, logo2));
        when(logoRepository.findByStatusAndExpiresAtBefore(eq(LogoStatus.CONFIRMED), any(Instant.class)))
                .thenReturn(Collections.emptyList());

        // Simulate S3 deletion failure for first logo
        doThrow(S3Exception.builder().message("Access denied").build())
                .doNothing()
                .when(s3Client).deleteObject(any(DeleteObjectRequest.class));

        // Act
        logoCleanupService.cleanupOrphanedLogos();

        // Assert - both database records should be deleted despite S3 failure
        verify(s3Client, times(2)).deleteObject(any(DeleteObjectRequest.class));
        verify(logoRepository).delete(logo1);
        verify(logoRepository).delete(logo2);
    }

    @Test
    @DisplayName("AC7.7: Should continue cleanup even if database deletion fails")
    void shouldContinueCleanup_whenDatabaseDeletionFails() {
        // Arrange
        Instant expiredTime = Instant.now().minus(25, ChronoUnit.HOURS);

        Logo logo1 = createLogo("upload-301", LogoStatus.PENDING, "logos/temp/upload-301/logo.png", expiredTime);
        Logo logo2 = createLogo("upload-302", LogoStatus.PENDING, "logos/temp/upload-302/logo.png", expiredTime);

        when(logoRepository.findByStatusAndExpiresAtBefore(eq(LogoStatus.PENDING), any(Instant.class)))
                .thenReturn(Arrays.asList(logo1, logo2));
        when(logoRepository.findByStatusAndExpiresAtBefore(eq(LogoStatus.CONFIRMED), any(Instant.class)))
                .thenReturn(Collections.emptyList());

        // Simulate database deletion failure for first logo
        doThrow(new RuntimeException("Database error"))
                .doNothing()
                .when(logoRepository).delete(any(Logo.class));

        // Act
        logoCleanupService.cleanupOrphanedLogos();

        // Assert - cleanup should attempt both logos
        verify(s3Client, times(2)).deleteObject(any(DeleteObjectRequest.class));
        verify(logoRepository, times(2)).delete(any(Logo.class));
    }

    @Test
    @DisplayName("AC7.8: Should cleanup multiple logos in single run")
    void shouldCleanupMultipleLogos_inSingleRun() {
        // Arrange
        Instant now = Instant.now();
        Instant pendingExpired = now.minus(25, ChronoUnit.HOURS);
        Instant confirmedExpired = now.minus(8, ChronoUnit.DAYS);

        Logo pendingLogo1 = createLogo("upload-401", LogoStatus.PENDING, "logos/temp/upload-401/logo.png", pendingExpired);
        Logo pendingLogo2 = createLogo("upload-402", LogoStatus.PENDING, "logos/temp/upload-402/logo.png", pendingExpired);
        Logo confirmedLogo1 = createLogo("upload-403", LogoStatus.CONFIRMED, "logos/temp/upload-403/logo.png", confirmedExpired);

        when(logoRepository.findByStatusAndExpiresAtBefore(eq(LogoStatus.PENDING), any(Instant.class)))
                .thenReturn(Arrays.asList(pendingLogo1, pendingLogo2));
        when(logoRepository.findByStatusAndExpiresAtBefore(eq(LogoStatus.CONFIRMED), any(Instant.class)))
                .thenReturn(List.of(confirmedLogo1));

        // Act
        logoCleanupService.cleanupOrphanedLogos();

        // Assert
        verify(s3Client, times(3)).deleteObject(any(DeleteObjectRequest.class));
        verify(logoRepository).delete(pendingLogo1);
        verify(logoRepository).delete(pendingLogo2);
        verify(logoRepository).delete(confirmedLogo1);
    }

    @Test
    @DisplayName("Should handle empty cleanup gracefully")
    void shouldHandleEmptyCleanup_gracefully() {
        // Arrange
        when(logoRepository.findByStatusAndExpiresAtBefore(eq(LogoStatus.PENDING), any(Instant.class)))
                .thenReturn(Collections.emptyList());
        when(logoRepository.findByStatusAndExpiresAtBefore(eq(LogoStatus.CONFIRMED), any(Instant.class)))
                .thenReturn(Collections.emptyList());

        // Act
        logoCleanupService.cleanupOrphanedLogos();

        // Assert
        verify(logoRepository, times(2)).findByStatusAndExpiresAtBefore(any(LogoStatus.class), any(Instant.class));
        verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
        verify(logoRepository, never()).delete(any(Logo.class));
    }

    @Test
    @DisplayName("Should trigger manual cleanup")
    void shouldTriggerManualCleanup() {
        // Arrange
        when(logoRepository.findByStatusAndExpiresAtBefore(eq(LogoStatus.PENDING), any(Instant.class)))
                .thenReturn(Collections.emptyList());
        when(logoRepository.findByStatusAndExpiresAtBefore(eq(LogoStatus.CONFIRMED), any(Instant.class)))
                .thenReturn(Collections.emptyList());

        // Act
        logoCleanupService.triggerManualCleanup();

        // Assert
        verify(logoRepository, times(2)).findByStatusAndExpiresAtBefore(any(LogoStatus.class), any(Instant.class));
    }

    @Test
    @DisplayName("Should return cleanup statistics with correct counts")
    void shouldReturnCleanupStatistics_withCorrectCounts() {
        // Arrange
        Instant now = Instant.now();
        Instant expiredTime = now.minus(25, ChronoUnit.HOURS);

        Logo expiredPending = createLogo("upload-501", LogoStatus.PENDING, "logos/temp/upload-501/logo.png", expiredTime);
        Logo expiredConfirmed = createLogo("upload-502", LogoStatus.CONFIRMED, "logos/temp/upload-502/logo.png", expiredTime);

        when(logoRepository.countByStatus(LogoStatus.PENDING)).thenReturn(10L);
        when(logoRepository.countByStatus(LogoStatus.CONFIRMED)).thenReturn(5L);
        when(logoRepository.countByStatus(LogoStatus.ASSOCIATED)).thenReturn(100L);
        when(logoRepository.findByStatusAndExpiresAtBefore(eq(LogoStatus.PENDING), any(Instant.class)))
                .thenReturn(List.of(expiredPending));
        when(logoRepository.findByStatusAndExpiresAtBefore(eq(LogoStatus.CONFIRMED), any(Instant.class)))
                .thenReturn(List.of(expiredConfirmed));

        // Act
        LogoCleanupService.CleanupStatistics stats = logoCleanupService.getCleanupStatistics();

        // Assert
        assertThat(stats.pendingCount()).isEqualTo(10L);
        assertThat(stats.confirmedCount()).isEqualTo(5L);
        assertThat(stats.associatedCount()).isEqualTo(100L);
        assertThat(stats.expiredPendingCount()).isEqualTo(1L);
        assertThat(stats.expiredConfirmedCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Should return zero statistics when no logos exist")
    void shouldReturnZeroStatistics_whenNoLogosExist() {
        // Arrange
        when(logoRepository.countByStatus(any(LogoStatus.class))).thenReturn(0L);
        when(logoRepository.findByStatusAndExpiresAtBefore(any(LogoStatus.class), any(Instant.class)))
                .thenReturn(Collections.emptyList());

        // Act
        LogoCleanupService.CleanupStatistics stats = logoCleanupService.getCleanupStatistics();

        // Assert
        assertThat(stats.pendingCount()).isZero();
        assertThat(stats.confirmedCount()).isZero();
        assertThat(stats.associatedCount()).isZero();
        assertThat(stats.expiredPendingCount()).isZero();
        assertThat(stats.expiredConfirmedCount()).isZero();
    }

    // ============ Helper Methods ============

    private Logo createLogo(String uploadId, LogoStatus status, String s3Key, Instant expiresAt) {
        Logo logo = new Logo();
        logo.setId(UUID.randomUUID());
        logo.setUploadId(uploadId);
        logo.setS3Key(s3Key);
        logo.setFileExtension("png");
        logo.setFileSize(1024L);
        logo.setMimeType("image/png");
        logo.setStatus(status);
        logo.setExpiresAt(expiresAt);
        logo.setCreatedAt(Instant.now().minus(30, ChronoUnit.DAYS));
        logo.setUpdatedAt(Instant.now().minus(30, ChronoUnit.DAYS));
        return logo;
    }
}
