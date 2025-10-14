package ch.batbern.companyuser.service;

import ch.batbern.companyuser.domain.Company;
import ch.batbern.companyuser.dto.PresignedUploadUrl;
import ch.batbern.companyuser.dto.ContentMetadata;
import ch.batbern.companyuser.exception.FileSizeExceededException;
import ch.batbern.companyuser.exception.InvalidFileTypeException;
import ch.batbern.companyuser.exception.CompanyNotFoundException;
import ch.batbern.companyuser.repository.CompanyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URL;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.mockito.ArgumentCaptor;

/**
 * Unit tests for CompanyLogoService
 * Tests AC8: File Storage
 * - AC8.1: Generate presigned URLs for logo uploads
 * - AC8.2: Validate file types (PNG, JPG, SVG only)
 * - AC8.3: Store S3 key after upload completion
 * - AC8.4: Serve logos through CloudFront CDN
 */
@ExtendWith(MockitoExtension.class)
class CompanyLogoServiceTest {

    @Mock
    private S3Presigner s3Presigner;

    @Mock
    private S3Client s3Client;

    @Mock
    private CompanyRepository companyRepository;

    private CompanyLogoService companyLogoService;

    private static final String BUCKET_NAME = "batbern-test-company-logos";
    private static final String CLOUDFRONT_DOMAIN = "https://cdn.batbern.ch";
    private UUID testUserId;
    private UUID testCompanyId;
    private Company testCompany;

    @BeforeEach
    void setUp() {
        testUserId = UUID.randomUUID();
        testCompanyId = UUID.randomUUID();
        testCompany = Company.builder()
                .id(testCompanyId)
                .name("Test Company")
                .displayName("Test Company Ltd")
                .createdBy(testUserId.toString())
                .build();

        // Initialize service with configuration values
        companyLogoService = new CompanyLogoService(
                s3Presigner,
                companyRepository,
                BUCKET_NAME,
                CLOUDFRONT_DOMAIN
        );
    }

    // AC8.1: Test presigned URL generation
    @Test
    void should_generatePresignedURL_when_logoUploadRequested() throws Exception {
        // Given
        String filename = "company-logo.png";
        long fileSizeBytes = 1024 * 1024; // 1 MB

        URL mockPresignedUrl = new URL("https://batbern-test-company-logos.s3.amazonaws.com/logos/2024/" + testCompanyId + "/logo-123.png?X-Amz-Signature=abc123");
        PresignedPutObjectRequest mockPresignedRequest = mock(PresignedPutObjectRequest.class);
        when(mockPresignedRequest.url()).thenReturn(mockPresignedUrl);

        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class)))
                .thenReturn(mockPresignedRequest);

        // When
        PresignedUploadUrl result = companyLogoService.generateLogoUploadUrl(
                testUserId.toString(),
                filename,
                fileSizeBytes
        );

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getUploadUrl()).isNotEmpty();
        assertThat(result.getFileId()).isNotEmpty();
        assertThat(result.getExpiresInMinutes()).isEqualTo(15);
        verify(s3Presigner).presignPutObject(any(PutObjectPresignRequest.class));
    }

    @Test
    void should_throwFileSizeExceededException_when_fileSizeExceeds5MB() {
        // Given
        String filename = "large-logo.png";
        long fileSizeBytes = 6 * 1024 * 1024; // 6 MB (exceeds 5 MB limit)

        // When & Then
        assertThatThrownBy(() ->
            companyLogoService.generateLogoUploadUrl(testUserId.toString(), filename, fileSizeBytes)
        )
        .isInstanceOf(FileSizeExceededException.class)
        .hasMessageContaining("Logo file size exceeds 5MB limit");
    }

    @Test
    void should_generatePresignedURL_when_fileSizeIsExactly5MB() throws Exception {
        // Given
        String filename = "max-size-logo.png";
        long fileSizeBytes = 5 * 1024 * 1024; // Exactly 5 MB

        URL mockPresignedUrl = new URL("https://batbern-test-company-logos.s3.amazonaws.com/logos/2024/" + testCompanyId + "/logo-123.png?X-Amz-Signature=abc123");
        PresignedPutObjectRequest mockPresignedRequest = mock(PresignedPutObjectRequest.class);
        when(mockPresignedRequest.url()).thenReturn(mockPresignedUrl);
        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class)))
                .thenReturn(mockPresignedRequest);

        // When
        PresignedUploadUrl result = companyLogoService.generateLogoUploadUrl(
                testUserId.toString(),
                filename,
                fileSizeBytes
        );

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getUploadUrl()).isNotEmpty();
    }

    @Test
    void should_generate15MinutePresignedURL_when_requested() throws Exception {
        // Given
        String filename = "company-logo.png";
        long fileSizeBytes = 1024 * 1024;

        URL mockPresignedUrl = new URL("https://batbern-test-company-logos.s3.amazonaws.com/logos/2024/" + testCompanyId + "/logo-123.png?X-Amz-Signature=abc123");
        PresignedPutObjectRequest mockPresignedRequest = mock(PresignedPutObjectRequest.class);
        when(mockPresignedRequest.url()).thenReturn(mockPresignedUrl);
        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class)))
                .thenReturn(mockPresignedRequest);

        // When
        PresignedUploadUrl result = companyLogoService.generateLogoUploadUrl(
                testUserId.toString(),
                filename,
                fileSizeBytes
        );

        // Then
        assertThat(result.getExpiresInMinutes()).isEqualTo(15);
        verify(s3Presigner).presignPutObject(any(PutObjectPresignRequest.class));
    }

    // AC8.2: Test file type validation
    @Test
    void should_acceptPNGFile_when_validPNGProvided() throws Exception {
        // Given
        String filename = "logo.png";
        long fileSizeBytes = 1024 * 1024;

        URL mockPresignedUrl = new URL("https://batbern-test-company-logos.s3.amazonaws.com/logos/2024/" + testCompanyId + "/logo-123.png?X-Amz-Signature=abc123");
        PresignedPutObjectRequest mockPresignedRequest = mock(PresignedPutObjectRequest.class);
        when(mockPresignedRequest.url()).thenReturn(mockPresignedUrl);
        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class)))
                .thenReturn(mockPresignedRequest);

        // When
        PresignedUploadUrl result = companyLogoService.generateLogoUploadUrl(
                testUserId.toString(),
                filename,
                fileSizeBytes
        );

        // Then
        assertThat(result).isNotNull();
    }

    @Test
    void should_acceptJPGFile_when_validJPGProvided() throws Exception {
        // Given
        String filename = "logo.jpg";
        long fileSizeBytes = 1024 * 1024;

        URL mockPresignedUrl = new URL("https://batbern-test-company-logos.s3.amazonaws.com/logos/2024/" + testCompanyId + "/logo-123.jpg?X-Amz-Signature=abc123");
        PresignedPutObjectRequest mockPresignedRequest = mock(PresignedPutObjectRequest.class);
        when(mockPresignedRequest.url()).thenReturn(mockPresignedUrl);
        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class)))
                .thenReturn(mockPresignedRequest);

        // When
        PresignedUploadUrl result = companyLogoService.generateLogoUploadUrl(
                testUserId.toString(),
                filename,
                fileSizeBytes
        );

        // Then
        assertThat(result).isNotNull();
    }

    @Test
    void should_acceptJPEGFile_when_validJPEGProvided() throws Exception {
        // Given
        String filename = "logo.jpeg";
        long fileSizeBytes = 1024 * 1024;

        URL mockPresignedUrl = new URL("https://batbern-test-company-logos.s3.amazonaws.com/logos/2024/" + testCompanyId + "/logo-123.jpeg?X-Amz-Signature=abc123");
        PresignedPutObjectRequest mockPresignedRequest = mock(PresignedPutObjectRequest.class);
        when(mockPresignedRequest.url()).thenReturn(mockPresignedUrl);
        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class)))
                .thenReturn(mockPresignedRequest);

        // When
        PresignedUploadUrl result = companyLogoService.generateLogoUploadUrl(
                testUserId.toString(),
                filename,
                fileSizeBytes
        );

        // Then
        assertThat(result).isNotNull();
    }

    @Test
    void should_acceptSVGFile_when_validSVGProvided() throws Exception {
        // Given
        String filename = "logo.svg";
        long fileSizeBytes = 1024 * 1024;

        URL mockPresignedUrl = new URL("https://batbern-test-company-logos.s3.amazonaws.com/logos/2024/" + testCompanyId + "/logo-123.svg?X-Amz-Signature=abc123");
        PresignedPutObjectRequest mockPresignedRequest = mock(PresignedPutObjectRequest.class);
        when(mockPresignedRequest.url()).thenReturn(mockPresignedUrl);
        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class)))
                .thenReturn(mockPresignedRequest);

        // When
        PresignedUploadUrl result = companyLogoService.generateLogoUploadUrl(
                testUserId.toString(),
                filename,
                fileSizeBytes
        );

        // Then
        assertThat(result).isNotNull();
    }

    @Test
    void should_throwInvalidFileTypeException_when_PDFFileProvided() {
        // Given
        String filename = "document.pdf";
        long fileSizeBytes = 1024 * 1024;

        // When & Then
        assertThatThrownBy(() ->
            companyLogoService.generateLogoUploadUrl(testUserId.toString(), filename, fileSizeBytes)
        )
        .isInstanceOf(InvalidFileTypeException.class)
        .hasMessageContaining("Invalid file type");
    }

    @Test
    void should_throwInvalidFileTypeException_when_TXTFileProvided() {
        // Given
        String filename = "readme.txt";
        long fileSizeBytes = 1024;

        // When & Then
        assertThatThrownBy(() ->
            companyLogoService.generateLogoUploadUrl(testUserId.toString(), filename, fileSizeBytes)
        )
        .isInstanceOf(InvalidFileTypeException.class)
        .hasMessageContaining("Invalid file type");
    }

    @Test
    void should_throwInvalidFileTypeException_when_noFileExtensionProvided() {
        // Given
        String filename = "logo";
        long fileSizeBytes = 1024 * 1024;

        // When & Then
        assertThatThrownBy(() ->
            companyLogoService.generateLogoUploadUrl(testUserId.toString(), filename, fileSizeBytes)
        )
        .isInstanceOf(InvalidFileTypeException.class)
        .hasMessageContaining("Invalid file type");
    }

    // AC8.3: Test S3 key storage after upload completion
    @Test
    void should_storeS3Key_when_uploadCompleted() {
        // Given
        String fileId = UUID.randomUUID().toString();
        String checksum = "abc123checksum";

        when(companyRepository.findById(testCompanyId)).thenReturn(Optional.of(testCompany));
        when(companyRepository.save(any(Company.class))).thenReturn(testCompany);

        // When
        companyLogoService.confirmLogoUpload(testCompanyId, fileId, checksum);

        // Then
        ArgumentCaptor<Company> companyCaptor = ArgumentCaptor.forClass(Company.class);
        verify(companyRepository).findById(testCompanyId);
        verify(companyRepository).save(companyCaptor.capture());

        Company savedCompany = companyCaptor.getValue();
        assertThat(savedCompany.getLogoUrl()).isNotNull();
        assertThat(savedCompany.getLogoUrl()).contains(CLOUDFRONT_DOMAIN);
        assertThat(savedCompany.getLogoS3Key()).isNotNull();
        assertThat(savedCompany.getLogoFileId()).isEqualTo(fileId);
    }

    @Test
    void should_throwCompanyNotFoundException_when_confirmingUploadForNonexistentCompany() {
        // Given
        UUID nonExistentCompanyId = UUID.randomUUID();
        String fileId = UUID.randomUUID().toString();
        String checksum = "abc123checksum";

        when(companyRepository.findById(nonExistentCompanyId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() ->
            companyLogoService.confirmLogoUpload(nonExistentCompanyId, fileId, checksum)
        )
        .isInstanceOf(CompanyNotFoundException.class);
    }

    @Test
    void should_updateExistingLogo_when_companyAlreadyHasLogo() {
        // Given
        String oldFileId = UUID.randomUUID().toString();
        String oldS3Key = "/logos/2024/" + testCompanyId + "/logo-" + oldFileId + ".png";
        testCompany.setLogoFileId(oldFileId);
        testCompany.setLogoS3Key(oldS3Key);
        testCompany.setLogoUrl(CLOUDFRONT_DOMAIN + oldS3Key);

        String newFileId = UUID.randomUUID().toString();
        String newChecksum = "newchecksum123";

        when(companyRepository.findById(testCompanyId)).thenReturn(Optional.of(testCompany));
        when(companyRepository.save(any(Company.class))).thenReturn(testCompany);

        // When
        companyLogoService.confirmLogoUpload(testCompanyId, newFileId, newChecksum);

        // Then
        ArgumentCaptor<Company> companyCaptor = ArgumentCaptor.forClass(Company.class);
        verify(companyRepository).save(companyCaptor.capture());

        Company savedCompany = companyCaptor.getValue();
        assertThat(savedCompany.getLogoFileId()).isEqualTo(newFileId);
        assertThat(savedCompany.getLogoS3Key()).isNotNull();
        assertThat(savedCompany.getLogoUrl()).isNotNull();
        assertThat(savedCompany.getLogoUrl()).contains(CLOUDFRONT_DOMAIN);
    }

    // AC8.4: Test CloudFront URL format
    @Test
    void should_returnCloudFrontURL_when_logoRequested() {
        // Given
        String fileId = UUID.randomUUID().toString();
        String checksum = "abc123checksum";

        when(companyRepository.findById(testCompanyId)).thenReturn(Optional.of(testCompany));
        when(companyRepository.save(any(Company.class))).thenReturn(testCompany);

        // When
        companyLogoService.confirmLogoUpload(testCompanyId, fileId, checksum);

        // Then
        ArgumentCaptor<Company> companyCaptor = ArgumentCaptor.forClass(Company.class);
        verify(companyRepository).save(companyCaptor.capture());

        Company savedCompany = companyCaptor.getValue();
        assertThat(savedCompany.getLogoUrl()).isNotNull();
        assertThat(savedCompany.getLogoUrl()).startsWith(CLOUDFRONT_DOMAIN);
        assertThat(savedCompany.getLogoUrl()).contains("/logos/");
    }

    @Test
    void should_generateUniqueS3Key_when_uploadRequested() throws Exception {
        // Given
        String filename = "logo.png";
        long fileSizeBytes = 1024 * 1024;

        URL mockPresignedUrl = new URL("https://batbern-test-company-logos.s3.amazonaws.com/logos/2024/unique-id/logo-123.png?X-Amz-Signature=abc123");
        PresignedPutObjectRequest mockPresignedRequest = mock(PresignedPutObjectRequest.class);
        when(mockPresignedRequest.url()).thenReturn(mockPresignedUrl);
        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class)))
                .thenReturn(mockPresignedRequest);

        // When
        PresignedUploadUrl result1 = companyLogoService.generateLogoUploadUrl(testUserId.toString(), filename, fileSizeBytes);
        PresignedUploadUrl result2 = companyLogoService.generateLogoUploadUrl(testUserId.toString(), filename, fileSizeBytes);

        // Then
        assertThat(result1.getFileId()).isNotEqualTo(result2.getFileId());
    }
}
