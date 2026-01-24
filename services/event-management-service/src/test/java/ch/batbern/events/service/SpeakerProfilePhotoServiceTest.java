package ch.batbern.events.service;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.dto.PhotoConfirmRequest;
import ch.batbern.events.dto.PhotoUploadRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for SpeakerProfilePhotoService.
 * Story 6.2b: Speaker Profile Update Portal - AC7 (Profile Photo Upload)
 *
 * Note: This service is currently in RED phase (skeleton only).
 * Tests verify the skeleton behavior and will be expanded in GREEN phase.
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
    private UserApiClient userApiClient;

    private SpeakerProfilePhotoService service;

    private static final String BUCKET_NAME = "test-bucket";
    private static final String CLOUDFRONT_DOMAIN = "https://cdn.test.com";

    @BeforeEach
    void setUp() {
        service = new SpeakerProfilePhotoService(
                s3Presigner,
                s3Client,
                magicLinkService,
                userApiClient,
                BUCKET_NAME,
                CLOUDFRONT_DOMAIN
        );
    }

    @Nested
    @DisplayName("generatePresignedUrl")
    class GeneratePresignedUrlTests {

        @Test
        @DisplayName("should be not yet implemented (RED phase)")
        void shouldBeNotYetImplemented() {
            // Given
            PhotoUploadRequest request = PhotoUploadRequest.builder()
                    .fileName("profile.jpg")
                    .contentType("image/jpeg")
                    .fileSize(1024L)
                    .build();

            // When/Then - RED phase skeleton throws UnsupportedOperationException
            assertThatThrownBy(() -> service.generatePresignedUrl("valid-token", request))
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessageContaining("Not yet implemented");
        }
    }

    @Nested
    @DisplayName("confirmUpload")
    class ConfirmUploadTests {

        @Test
        @DisplayName("should be not yet implemented (RED phase)")
        void shouldBeNotYetImplemented() {
            // Given
            PhotoConfirmRequest request = PhotoConfirmRequest.builder()
                    .uploadId("upload-123")
                    .build();

            // When/Then - RED phase skeleton throws UnsupportedOperationException
            assertThatThrownBy(() -> service.confirmUpload("valid-token", request, "photos/test.jpg"))
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessageContaining("Not yet implemented");
        }
    }
}
