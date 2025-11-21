package ch.batbern.companyuser.service;

import ch.batbern.companyuser.domain.Logo;
import ch.batbern.companyuser.domain.LogoStatus;
import ch.batbern.companyuser.dto.PresignedUploadUrl;
import ch.batbern.companyuser.exception.FileSizeExceededException;
import ch.batbern.companyuser.exception.InvalidFileTypeException;
import ch.batbern.companyuser.exception.LogoNotFoundException;
import ch.batbern.companyuser.repository.LogoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URL;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for GenericLogoService
 * Story 1.16.3: Generic File Upload Service
 * Tests all three phases: Generate URL, Confirm Upload, Associate with Entity
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GenericLogoService Tests")
class GenericLogoServiceTest {

    @Mock
    private S3Presigner s3Presigner;

    @Mock
    private S3Client s3Client;

    @Mock
    private LogoRepository logoRepository;

    private GenericLogoService genericLogoService;

    private static final String BUCKET_NAME = "test-bucket";
    private static final String CLOUDFRONT_DOMAIN = "https://cdn.test.com";

    @BeforeEach
    void setUp() {
        genericLogoService = new GenericLogoService(
                s3Presigner,
                s3Client,
                logoRepository,
                BUCKET_NAME,
                CLOUDFRONT_DOMAIN
        );
    }

    // ========== Phase 1: Generate Presigned URL Tests ==========

    @Test
    @DisplayName("AC1.1: Should generate presigned URL for valid file")
    void shouldGeneratePresignedUrl_whenValidFileProvided() throws Exception {
        // Arrange
        String fileName = "company-logo.png";
        long fileSize = 1024 * 1024; // 1MB
        String mimeType = "image/png";

        PresignedPutObjectRequest mockPresignedRequest = mock(PresignedPutObjectRequest.class);
        when(mockPresignedRequest.url()).thenReturn(new URL("https://s3.amazonaws.com/test-bucket/key"));
        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class)))
                .thenReturn(mockPresignedRequest);
        when(logoRepository.save(any(Logo.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        PresignedUploadUrl result = genericLogoService.generatePresignedUrl(fileName, fileSize, mimeType);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getUploadUrl()).isNotNull();
        assertThat(result.getFileId()).isNotNull();
        assertThat(result.getFileExtension()).isEqualTo("png");
        assertThat(result.getExpiresInMinutes()).isEqualTo(15);

        // Verify logo entity was saved with PENDING status
        ArgumentCaptor<Logo> logoCaptor = ArgumentCaptor.forClass(Logo.class);
        verify(logoRepository).save(logoCaptor.capture());
        Logo savedLogo = logoCaptor.getValue();
        assertThat(savedLogo.getStatus()).isEqualTo(LogoStatus.PENDING);
        assertThat(savedLogo.getFileExtension()).isEqualTo("png");
        assertThat(savedLogo.getFileSize()).isEqualTo(fileSize);
        assertThat(savedLogo.getExpiresAt()).isNotNull();
    }

    @Test
    @DisplayName("AC1.2: Should create logo with PENDING status when upload requested")
    void shouldCreateLogoWithPendingStatus_whenUploadRequested() throws Exception {
        // Arrange
        String fileName = "logo.jpg";
        long fileSize = 2048;
        String mimeType = "image/jpeg";

        PresignedPutObjectRequest mockPresignedRequest = mock(PresignedPutObjectRequest.class);
        when(mockPresignedRequest.url()).thenReturn(new URL("https://s3.amazonaws.com/test"));
        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class)))
                .thenReturn(mockPresignedRequest);
        when(logoRepository.save(any(Logo.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        genericLogoService.generatePresignedUrl(fileName, fileSize, mimeType);

        // Assert
        ArgumentCaptor<Logo> logoCaptor = ArgumentCaptor.forClass(Logo.class);
        verify(logoRepository).save(logoCaptor.capture());
        Logo logo = logoCaptor.getValue();

        assertThat(logo.getStatus()).isEqualTo(LogoStatus.PENDING);
        assertThat(logo.getExpiresAt()).isAfter(Instant.now());
        assertThat(logo.getExpiresAt()).isBefore(Instant.now().plus(25, ChronoUnit.HOURS));
    }

    @Test
    @DisplayName("AC1.3: Should throw FileSizeExceeded when file size over 5MB")
    void shouldThrowFileSizeExceeded_whenFileSizeOver5MB() {
        // Arrange
        String fileName = "large-logo.png";
        long fileSize = 6 * 1024 * 1024; // 6MB
        String mimeType = "image/png";

        // Act & Assert
        assertThatThrownBy(() ->
                genericLogoService.generatePresignedUrl(fileName, fileSize, mimeType))
                .isInstanceOf(FileSizeExceededException.class)
                .hasMessageContaining("5MB limit");

        verify(logoRepository, never()).save(any());
    }

    @Test
    @DisplayName("AC1.4: Should throw InvalidFileType when unsupported extension")
    void shouldThrowInvalidFileType_whenUnsupportedExtension() {
        // Arrange
        String fileName = "document.pdf";
        long fileSize = 1024;
        String mimeType = "application/pdf";

        // Act & Assert
        assertThatThrownBy(() ->
                genericLogoService.generatePresignedUrl(fileName, fileSize, mimeType))
                .isInstanceOf(InvalidFileTypeException.class)
                .hasMessageContaining("Invalid file type");

        verify(logoRepository, never()).save(any());
    }

    @Test
    @DisplayName("AC1.5: Should generate unique temp S3 key when upload requested")
    void shouldGenerateUniqueTempS3Key_whenUploadRequested() throws Exception {
        // Arrange
        String fileName = "logo.png";
        long fileSize = 1024;
        String mimeType = "image/png";

        PresignedPutObjectRequest mockPresignedRequest = mock(PresignedPutObjectRequest.class);
        when(mockPresignedRequest.url()).thenReturn(new URL("https://s3.amazonaws.com/test"));
        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class)))
                .thenReturn(mockPresignedRequest);
        when(logoRepository.save(any(Logo.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        genericLogoService.generatePresignedUrl(fileName, fileSize, mimeType);

        // Assert
        ArgumentCaptor<Logo> logoCaptor = ArgumentCaptor.forClass(Logo.class);
        verify(logoRepository).save(logoCaptor.capture());
        Logo logo = logoCaptor.getValue();

        assertThat(logo.getS3Key()).startsWith("logos/temp/");
        assertThat(logo.getS3Key()).contains("/logo-");
        assertThat(logo.getS3Key()).endsWith(".png");
    }

    // ========== Phase 2: Confirm Upload Tests ==========

    @Test
    @DisplayName("AC2.1: Should update logo to CONFIRMED when upload confirmed")
    void shouldUpdateLogoToConfirmed_whenUploadConfirmed() {
        // Arrange
        String uploadId = "test-upload-id";
        String checksum = "sha256:abc123";

        Logo pendingLogo = Logo.builder()
                .uploadId(uploadId)
                .status(LogoStatus.PENDING)
                .s3Key("logos/temp/test/logo.png")
                .fileExtension("png")
                .fileSize(1024L)
                .mimeType("image/png")
                .build();

        when(logoRepository.findByUploadId(uploadId)).thenReturn(Optional.of(pendingLogo));
        when(logoRepository.save(any(Logo.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        genericLogoService.confirmUpload(uploadId, checksum);

        // Assert
        ArgumentCaptor<Logo> logoCaptor = ArgumentCaptor.forClass(Logo.class);
        verify(logoRepository).save(logoCaptor.capture());
        Logo confirmedLogo = logoCaptor.getValue();

        assertThat(confirmedLogo.getStatus()).isEqualTo(LogoStatus.CONFIRMED);
        assertThat(confirmedLogo.getChecksum()).isEqualTo(checksum);
        assertThat(confirmedLogo.getExpiresAt()).isNotNull();
        assertThat(confirmedLogo.getExpiresAt()).isAfter(Instant.now().plus(6, ChronoUnit.DAYS));
    }

    @Test
    @DisplayName("AC2.2: Should store checksum when confirmation provided")
    void shouldStoreChecksum_whenConfirmationProvided() {
        // Arrange
        String uploadId = "test-upload-id";
        String checksum = "checksum-value";

        Logo logo = Logo.builder()
                .uploadId(uploadId)
                .status(LogoStatus.PENDING)
                .s3Key("logos/temp/test/logo.png")
                .fileExtension("png")
                .fileSize(1024L)
                .mimeType("image/png")
                .build();

        when(logoRepository.findByUploadId(uploadId)).thenReturn(Optional.of(logo));
        when(logoRepository.save(any(Logo.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        genericLogoService.confirmUpload(uploadId, checksum);

        // Assert
        ArgumentCaptor<Logo> logoCaptor = ArgumentCaptor.forClass(Logo.class);
        verify(logoRepository).save(logoCaptor.capture());
        assertThat(logoCaptor.getValue().getChecksum()).isEqualTo(checksum);
    }

    @Test
    @DisplayName("AC2.3: Should throw LogoNotFound when invalid upload ID")
    void shouldThrowNotFound_whenInvalidUploadId() {
        // Arrange
        String uploadId = "invalid-id";
        when(logoRepository.findByUploadId(uploadId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() ->
                genericLogoService.confirmUpload(uploadId, "checksum"))
                .isInstanceOf(LogoNotFoundException.class);

        verify(logoRepository, never()).save(any());
    }

    // ========== Phase 3: Associate with Entity Tests ==========

    @Test
    @DisplayName("AC3.1: Should associate logo with company when logoUploadId provided")
    void shouldAssociateLogoWithCompany_whenLogoUploadIdProvided() {
        // Arrange
        String uploadId = "test-upload-id";
        String entityType = "COMPANY";
        String entityId = "Swisscom-AG";
        String finalS3Key = "logos/2025/companies/Swisscom-AG/logo-abc.png";

        Logo confirmedLogo = Logo.builder()
                .uploadId(uploadId)
                .status(LogoStatus.CONFIRMED)
                .s3Key("logos/temp/test/logo.png")
                .fileExtension("png")
                .fileSize(1024L)
                .mimeType("image/png")
                .build();

        when(logoRepository.findByUploadId(uploadId)).thenReturn(Optional.of(confirmedLogo));
        when(logoRepository.save(any(Logo.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        String result = genericLogoService.associateLogoWithEntity(uploadId, entityType, entityId, finalS3Key);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result).contains(finalS3Key);

        ArgumentCaptor<Logo> logoCaptor = ArgumentCaptor.forClass(Logo.class);
        verify(logoRepository).save(logoCaptor.capture());
        Logo associatedLogo = logoCaptor.getValue();

        assertThat(associatedLogo.getStatus()).isEqualTo(LogoStatus.ASSOCIATED);
        assertThat(associatedLogo.getAssociatedEntityType()).isEqualTo(entityType);
        assertThat(associatedLogo.getAssociatedEntityId()).isEqualTo(entityId);
        assertThat(associatedLogo.getS3Key()).isEqualTo(finalS3Key);
        assertThat(associatedLogo.getExpiresAt()).isNull(); // No expiration for associated logos
    }

    @Test
    @DisplayName("AC4.1: Should copy S3 object when associating logo")
    void shouldCopyS3Object_whenAssociatingLogo() {
        // Arrange
        String uploadId = "test-upload-id";
        String tempS3Key = "logos/temp/test/logo.png";
        String finalS3Key = "logos/2025/companies/Test/logo.png";

        Logo confirmedLogo = Logo.builder()
                .uploadId(uploadId)
                .status(LogoStatus.CONFIRMED)
                .s3Key(tempS3Key)
                .fileExtension("png")
                .fileSize(1024L)
                .mimeType("image/png")
                .build();

        when(logoRepository.findByUploadId(uploadId)).thenReturn(Optional.of(confirmedLogo));
        when(logoRepository.save(any(Logo.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        genericLogoService.associateLogoWithEntity(uploadId, "COMPANY", "Test", finalS3Key);

        // Assert
        ArgumentCaptor<CopyObjectRequest> copyCaptor = ArgumentCaptor.forClass(CopyObjectRequest.class);
        verify(s3Client).copyObject(copyCaptor.capture());
        CopyObjectRequest copyRequest = copyCaptor.getValue();

        assertThat(copyRequest.sourceKey()).isEqualTo(tempS3Key);
        assertThat(copyRequest.destinationKey()).isEqualTo(finalS3Key);
    }

    @Test
    @DisplayName("AC4.3: Should generate CloudFront URL when association complete")
    void shouldGenerateCloudFrontUrl_whenAssociationComplete() {
        // Arrange
        String uploadId = "test-upload-id";
        String finalS3Key = "logos/2025/companies/Test/logo.png";

        Logo confirmedLogo = Logo.builder()
                .uploadId(uploadId)
                .status(LogoStatus.CONFIRMED)
                .s3Key("logos/temp/test/logo.png")
                .fileExtension("png")
                .fileSize(1024L)
                .mimeType("image/png")
                .build();

        when(logoRepository.findByUploadId(uploadId)).thenReturn(Optional.of(confirmedLogo));
        when(logoRepository.save(any(Logo.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        String result = genericLogoService.associateLogoWithEntity(uploadId, "COMPANY", "Test", finalS3Key);

        // Assert
        assertThat(result).startsWith(CLOUDFRONT_DOMAIN);
        assertThat(result).contains(finalS3Key);
    }

    @Test
    @DisplayName("AC4.4: Should delete temp file when copy successful")
    void shouldDeleteTempFile_whenCopySuccessful() {
        // Arrange
        String uploadId = "test-upload-id";
        String tempS3Key = "logos/temp/test/logo.png";
        String finalS3Key = "logos/2025/companies/Test/logo.png";

        Logo confirmedLogo = Logo.builder()
                .uploadId(uploadId)
                .status(LogoStatus.CONFIRMED)
                .s3Key(tempS3Key)
                .fileExtension("png")
                .fileSize(1024L)
                .mimeType("image/png")
                .build();

        when(logoRepository.findByUploadId(uploadId)).thenReturn(Optional.of(confirmedLogo));
        when(logoRepository.save(any(Logo.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        genericLogoService.associateLogoWithEntity(uploadId, "COMPANY", "Test", finalS3Key);

        // Assert
        ArgumentCaptor<DeleteObjectRequest> deleteCaptor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(deleteCaptor.capture());
        DeleteObjectRequest deleteRequest = deleteCaptor.getValue();

        assertThat(deleteRequest.key()).isEqualTo(tempS3Key);
    }

    @Test
    @DisplayName("Should throw IllegalStateException when logo not CONFIRMED")
    void shouldThrowIllegalState_whenLogoNotConfirmed() {
        // Arrange
        String uploadId = "test-upload-id";

        Logo pendingLogo = Logo.builder()
                .uploadId(uploadId)
                .status(LogoStatus.PENDING)
                .s3Key("logos/temp/test/logo.png")
                .fileExtension("png")
                .fileSize(1024L)
                .mimeType("image/png")
                .build();

        when(logoRepository.findByUploadId(uploadId)).thenReturn(Optional.of(pendingLogo));

        // Act & Assert
        assertThatThrownBy(() ->
                genericLogoService.associateLogoWithEntity(uploadId, "COMPANY", "Test", "final-key"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must be CONFIRMED");

        verify(s3Client, never()).copyObject(any(CopyObjectRequest.class));
        verify(logoRepository, never()).save(any());
    }
}
