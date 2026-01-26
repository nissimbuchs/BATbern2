package ch.batbern.events.service;

import ch.batbern.events.domain.Speaker;
import ch.batbern.events.dto.PhotoConfirmRequest;
import ch.batbern.events.repository.SpeakerRepository;
import ch.batbern.events.dto.PhotoUploadRequest;
import ch.batbern.events.dto.PresignedPhotoUploadResponse;
import ch.batbern.events.dto.TokenValidationResult;
import ch.batbern.events.exception.FileSizeExceededException;
import ch.batbern.events.exception.InvalidFileTypeException;
import ch.batbern.events.exception.InvalidTokenException;
import ch.batbern.events.exception.PhotoUploadNotFoundException;
import ch.batbern.shared.types.TokenAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for SpeakerProfilePhotoService.
 * Story 6.2b: Speaker Profile Update Portal - AC7 (Profile Photo Upload)
 *
 * Tests cover:
 * - AC7.1: Photo upload via presigned URL
 * - AC7.2: Validation (file type, size)
 * - AC7.3: 5MB max file size
 * - AC7.4: Upload confirmation
 * - AC7.5: Cross-service sync
 */
@ExtendWith(MockitoExtension.class)
class SpeakerProfilePhotoServiceTest {

    @Mock
    private S3Presigner s3Presigner;

    @Mock
    private S3Client s3Client;

    @Mock
    private MagicLinkService magicLinkService;

    @Mock
    private SpeakerRepository speakerRepository;

    private SpeakerProfilePhotoService service;

    private static final String BUCKET_NAME = "test-bucket";
    private static final String CLOUDFRONT_DOMAIN = "https://cdn.test.com";
    private static final String VALID_TOKEN = "valid-magic-link-token";
    private static final String TEST_USERNAME = "test-speaker";
    private static final UUID TEST_SPEAKER_POOL_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new SpeakerProfilePhotoService(
                s3Presigner,
                s3Client,
                magicLinkService,
                speakerRepository,
                BUCKET_NAME,
                CLOUDFRONT_DOMAIN
        );
    }

    private TokenValidationResult validTokenResult() {
        return TokenValidationResult.valid(TEST_SPEAKER_POOL_ID, TEST_USERNAME, "event-code", TokenAction.RESPOND);
    }

    @Nested
    @DisplayName("generatePresignedUrl")
    class GeneratePresignedUrlTests {

        @Test
        @DisplayName("should generate presigned URL for valid JPEG upload - AC7.1")
        void shouldGeneratePresignedUrlForValidJpeg() throws MalformedURLException {
            // Given
            PhotoUploadRequest request = PhotoUploadRequest.builder()
                    .token(VALID_TOKEN)
                    .fileName("profile.jpg")
                    .contentType("image/jpeg")
                    .fileSize(1024L * 1024) // 1MB
                    .build();

            when(magicLinkService.validateToken(VALID_TOKEN)).thenReturn(validTokenResult());

            PresignedPutObjectRequest mockPresignedRequest = mock(PresignedPutObjectRequest.class);
            when(mockPresignedRequest.url()).thenReturn(new URL("https://s3.amazonaws.com/test-bucket/photo.jpg?signature=xxx"));
            when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(mockPresignedRequest);

            // When
            PresignedPhotoUploadResponse response = service.generatePresignedUrl(VALID_TOKEN, request);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getUploadUrl()).contains("s3.amazonaws.com");
            assertThat(response.getUploadId()).isNotBlank();
            assertThat(response.getS3Key()).contains("speaker-profiles");
            assertThat(response.getS3Key()).contains(TEST_USERNAME);
            assertThat(response.getS3Key()).endsWith(".jpg");
            assertThat(response.getExpiresIn()).isEqualTo(900);
            assertThat(response.getMaxSizeBytes()).isEqualTo(5L * 1024 * 1024);
        }

        @Test
        @DisplayName("should generate presigned URL for valid PNG upload - AC7.1")
        void shouldGeneratePresignedUrlForValidPng() throws MalformedURLException {
            // Given
            PhotoUploadRequest request = PhotoUploadRequest.builder()
                    .token(VALID_TOKEN)
                    .fileName("avatar.png")
                    .contentType("image/png")
                    .fileSize(2L * 1024 * 1024) // 2MB
                    .build();

            when(magicLinkService.validateToken(VALID_TOKEN)).thenReturn(validTokenResult());

            PresignedPutObjectRequest mockPresignedRequest = mock(PresignedPutObjectRequest.class);
            when(mockPresignedRequest.url()).thenReturn(new URL("https://s3.amazonaws.com/test-bucket/photo.png?signature=xxx"));
            when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(mockPresignedRequest);

            // When
            PresignedPhotoUploadResponse response = service.generatePresignedUrl(VALID_TOKEN, request);

            // Then
            assertThat(response.getS3Key()).endsWith(".png");
        }

        @Test
        @DisplayName("should generate presigned URL for valid WebP upload - AC7.1")
        void shouldGeneratePresignedUrlForValidWebp() throws MalformedURLException {
            // Given
            PhotoUploadRequest request = PhotoUploadRequest.builder()
                    .token(VALID_TOKEN)
                    .fileName("photo.webp")
                    .contentType("image/webp")
                    .fileSize(500L * 1024) // 500KB
                    .build();

            when(magicLinkService.validateToken(VALID_TOKEN)).thenReturn(validTokenResult());

            PresignedPutObjectRequest mockPresignedRequest = mock(PresignedPutObjectRequest.class);
            when(mockPresignedRequest.url()).thenReturn(new URL("https://s3.amazonaws.com/test-bucket/photo.webp?signature=xxx"));
            when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(mockPresignedRequest);

            // When
            PresignedPhotoUploadResponse response = service.generatePresignedUrl(VALID_TOKEN, request);

            // Then
            assertThat(response.getS3Key()).endsWith(".webp");
        }

        @Test
        @DisplayName("should reject invalid token - AC7.1")
        void shouldRejectInvalidToken() {
            // Given
            PhotoUploadRequest request = PhotoUploadRequest.builder()
                    .token("invalid-token")
                    .fileName("profile.jpg")
                    .contentType("image/jpeg")
                    .fileSize(1024L)
                    .build();

            when(magicLinkService.validateToken("invalid-token"))
                    .thenReturn(TokenValidationResult.notFound());

            // When/Then
            assertThatThrownBy(() -> service.generatePresignedUrl("invalid-token", request))
                    .isInstanceOf(InvalidTokenException.class);
        }

        @Test
        @DisplayName("should reject expired token - AC7.1")
        void shouldRejectExpiredToken() {
            // Given
            PhotoUploadRequest request = PhotoUploadRequest.builder()
                    .token("expired-token")
                    .fileName("profile.jpg")
                    .contentType("image/jpeg")
                    .fileSize(1024L)
                    .build();

            when(magicLinkService.validateToken("expired-token"))
                    .thenReturn(TokenValidationResult.expired());

            // When/Then
            assertThatThrownBy(() -> service.generatePresignedUrl("expired-token", request))
                    .isInstanceOf(InvalidTokenException.class);
        }

        @Test
        @DisplayName("should reject file exceeding 5MB - AC7.3")
        void shouldRejectFileTooLarge() {
            // Given
            PhotoUploadRequest request = PhotoUploadRequest.builder()
                    .token(VALID_TOKEN)
                    .fileName("large-photo.jpg")
                    .contentType("image/jpeg")
                    .fileSize(6L * 1024 * 1024) // 6MB - exceeds 5MB limit
                    .build();

            when(magicLinkService.validateToken(VALID_TOKEN)).thenReturn(validTokenResult());

            // When/Then
            assertThatThrownBy(() -> service.generatePresignedUrl(VALID_TOKEN, request))
                    .isInstanceOf(FileSizeExceededException.class)
                    .hasMessageContaining("5MB");
        }

        @Test
        @DisplayName("should accept file exactly at 5MB limit - AC7.3")
        void shouldAcceptFileAtExactLimit() throws MalformedURLException {
            // Given
            PhotoUploadRequest request = PhotoUploadRequest.builder()
                    .token(VALID_TOKEN)
                    .fileName("max-size.jpg")
                    .contentType("image/jpeg")
                    .fileSize(5L * 1024 * 1024) // Exactly 5MB
                    .build();

            when(magicLinkService.validateToken(VALID_TOKEN)).thenReturn(validTokenResult());

            PresignedPutObjectRequest mockPresignedRequest = mock(PresignedPutObjectRequest.class);
            when(mockPresignedRequest.url()).thenReturn(new URL("https://s3.amazonaws.com/test-bucket/photo.jpg?signature=xxx"));
            when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(mockPresignedRequest);

            // When
            PresignedPhotoUploadResponse response = service.generatePresignedUrl(VALID_TOKEN, request);

            // Then
            assertThat(response).isNotNull();
        }

        @ParameterizedTest
        @ValueSource(strings = {"application/pdf", "image/gif", "image/bmp", "video/mp4", "text/plain"})
        @DisplayName("should reject non-allowed content types - AC7.2")
        void shouldRejectInvalidContentTypes(String contentType) {
            // Given
            PhotoUploadRequest request = PhotoUploadRequest.builder()
                    .token(VALID_TOKEN)
                    .fileName("file.bin")
                    .contentType(contentType)
                    .fileSize(1024L)
                    .build();

            when(magicLinkService.validateToken(VALID_TOKEN)).thenReturn(validTokenResult());

            // When/Then
            assertThatThrownBy(() -> service.generatePresignedUrl(VALID_TOKEN, request))
                    .isInstanceOf(InvalidFileTypeException.class)
                    .hasMessageContaining("image/jpeg, image/png, image/webp");
        }

        @Test
        @DisplayName("should generate unique upload IDs for each request")
        void shouldGenerateUniqueUploadIds() throws MalformedURLException {
            // Given
            PhotoUploadRequest request = PhotoUploadRequest.builder()
                    .token(VALID_TOKEN)
                    .fileName("profile.jpg")
                    .contentType("image/jpeg")
                    .fileSize(1024L)
                    .build();

            when(magicLinkService.validateToken(VALID_TOKEN)).thenReturn(validTokenResult());

            PresignedPutObjectRequest mockPresignedRequest = mock(PresignedPutObjectRequest.class);
            when(mockPresignedRequest.url()).thenReturn(new URL("https://s3.amazonaws.com/test-bucket/photo.jpg?signature=xxx"));
            when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(mockPresignedRequest);

            // When
            PresignedPhotoUploadResponse response1 = service.generatePresignedUrl(VALID_TOKEN, request);
            PresignedPhotoUploadResponse response2 = service.generatePresignedUrl(VALID_TOKEN, request);

            // Then
            assertThat(response1.getUploadId()).isNotEqualTo(response2.getUploadId());
        }
    }

    @Nested
    @DisplayName("confirmUpload")
    class ConfirmUploadTests {

        @Test
        @DisplayName("should confirm upload and return CloudFront URL - AC7.4, AC7.5")
        void shouldConfirmUploadAndReturnCloudFrontUrl() {
            // Given
            String uploadId = "upload-123";
            String s3Key = "speaker-profiles/2026/test-speaker/photo-upload-123.jpg";

            PhotoConfirmRequest request = PhotoConfirmRequest.builder()
                    .token(VALID_TOKEN)
                    .uploadId(uploadId)
                    .build();

            when(magicLinkService.validateToken(VALID_TOKEN)).thenReturn(validTokenResult());

            // Mock S3 headObject to confirm file exists
            when(s3Client.headObject(any(HeadObjectRequest.class)))
                    .thenReturn(HeadObjectResponse.builder().build());

            // Mock speaker repository
            Speaker speaker = Speaker.builder().username(TEST_USERNAME).build();
            when(speakerRepository.findByUsername(TEST_USERNAME)).thenReturn(Optional.of(speaker));

            // When
            String cloudFrontUrl = service.confirmUpload(VALID_TOKEN, request, s3Key);

            // Then
            assertThat(cloudFrontUrl).isEqualTo(CLOUDFRONT_DOMAIN + "/" + s3Key);
            verify(speakerRepository).save(speaker);
            assertThat(speaker.getProfilePictureUrl()).isEqualTo(cloudFrontUrl);
        }

        @Test
        @DisplayName("should reject invalid token on confirm - AC7.4")
        void shouldRejectInvalidTokenOnConfirm() {
            // Given
            PhotoConfirmRequest request = PhotoConfirmRequest.builder()
                    .token("invalid-token")
                    .uploadId("upload-123")
                    .build();

            when(magicLinkService.validateToken("invalid-token"))
                    .thenReturn(TokenValidationResult.notFound());

            // When/Then
            assertThatThrownBy(() -> service.confirmUpload("invalid-token", request, "some/key"))
                    .isInstanceOf(InvalidTokenException.class);

            verify(speakerRepository, never()).save(any(Speaker.class));
        }

        @Test
        @DisplayName("should throw PhotoUploadNotFoundException when file not in S3 - AC7.4")
        void shouldThrowNotFoundWhenFileNotInS3() {
            // Given
            String uploadId = "missing-upload";
            String s3Key = "speaker-profiles/2026/test-speaker/photo-missing-upload.jpg";

            PhotoConfirmRequest request = PhotoConfirmRequest.builder()
                    .token(VALID_TOKEN)
                    .uploadId(uploadId)
                    .build();

            when(magicLinkService.validateToken(VALID_TOKEN)).thenReturn(validTokenResult());

            // Mock S3 headObject to throw NoSuchKeyException (file doesn't exist)
            when(s3Client.headObject(any(HeadObjectRequest.class)))
                    .thenThrow(NoSuchKeyException.builder().message("Key not found").build());

            // When/Then
            assertThatThrownBy(() -> service.confirmUpload(VALID_TOKEN, request, s3Key))
                    .isInstanceOf(PhotoUploadNotFoundException.class)
                    .hasMessageContaining(uploadId);

            verify(speakerRepository, never()).save(any(Speaker.class));
        }

        @Test
        @DisplayName("should save to Speaker entity with correct CloudFront URL - AC7.5")
        void shouldSaveToSpeakerWithCorrectUrl() {
            // Given
            String uploadId = "sync-test";
            String s3Key = "speaker-profiles/2026/test-speaker/photo-sync-test.jpg";

            PhotoConfirmRequest request = PhotoConfirmRequest.builder()
                    .token(VALID_TOKEN)
                    .uploadId(uploadId)
                    .build();

            when(magicLinkService.validateToken(VALID_TOKEN)).thenReturn(validTokenResult());

            when(s3Client.headObject(any(HeadObjectRequest.class)))
                    .thenReturn(HeadObjectResponse.builder().build());

            // Mock speaker repository
            Speaker speaker = Speaker.builder().username(TEST_USERNAME).build();
            when(speakerRepository.findByUsername(TEST_USERNAME)).thenReturn(Optional.of(speaker));

            // When
            service.confirmUpload(VALID_TOKEN, request, s3Key);

            // Then
            ArgumentCaptor<Speaker> speakerCaptor = ArgumentCaptor.forClass(Speaker.class);
            verify(speakerRepository).save(speakerCaptor.capture());

            Speaker savedSpeaker = speakerCaptor.getValue();
            assertThat(savedSpeaker.getProfilePictureUrl()).startsWith(CLOUDFRONT_DOMAIN);
            assertThat(savedSpeaker.getProfilePictureUrl()).contains(s3Key);
        }
    }
}
