package ch.batbern.events.service;

import ch.batbern.events.domain.EventTeaserImage;
import ch.batbern.events.dto.generated.TeaserImageItem;
import ch.batbern.events.dto.generated.TeaserImageUploadUrlResponse;
import ch.batbern.events.exception.TeaserImageLimitExceededException;
import ch.batbern.events.exception.TeaserImageNotFoundException;
import ch.batbern.events.repository.EventTeaserImageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for EventTeaserImageService.
 * Story 10.22: Event Teaser Images — AC8 (TDD)
 *
 * Tests cover:
 * - AC2: generateUploadUrl (presigned PUT URL)
 * - AC2: confirmUpload (S3 verify + persist)
 * - AC3: deleteTeaserImage (DB + S3)
 * - AC6: max-limit enforcement (422 on confirm)
 */
@ExtendWith(MockitoExtension.class)
class EventTeaserImageServiceTest {

    @Mock
    private EventTeaserImageRepository teaserImageRepository;

    @Mock
    private S3Presigner s3Presigner;

    @Mock
    private S3Client s3Client;

    private EventTeaserImageService service;

    private static final String BUCKET_NAME = "test-bucket";
    private static final String CLOUDFRONT_DOMAIN = "https://cdn.batbern.ch";
    private static final String EVENT_CODE = "BATbern57";

    @BeforeEach
    void setUp() {
        service = new EventTeaserImageService(
                teaserImageRepository,
                s3Presigner,
                s3Client,
                BUCKET_NAME,
                CLOUDFRONT_DOMAIN
        );
    }

    // ── generateUploadUrl ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("generateUploadUrl")
    class GenerateUploadUrlTests {

        @Test
        @DisplayName("should call S3Presigner and return uploadUrl, s3Key, expiresIn - AC2")
        void shouldReturnPresignedUrl() throws MalformedURLException {
            // Given
            PresignedPutObjectRequest mockPresigned = mock(PresignedPutObjectRequest.class);
            when(mockPresigned.url()).thenReturn(new URL("https://s3.amazonaws.com/test-bucket/key?sig=x"));
            when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(mockPresigned);

            // When
            TeaserImageUploadUrlResponse response = service.generateUploadUrl(EVENT_CODE, "image/jpeg", "teaser.jpg");

            // Then
            verify(s3Presigner).presignPutObject(any(PutObjectPresignRequest.class));
            assertThat(response.getUploadUrl().toString()).contains("s3.amazonaws.com");
            assertThat(response.getS3Key()).startsWith("events/" + EVENT_CODE + "/teaser/");
            assertThat(response.getS3Key()).endsWith(".jpg");
            assertThat(response.getExpiresIn()).isEqualTo(900);
        }

        @Test
        @DisplayName("s3Key pattern should follow events/{eventCode}/teaser/{uuid}.{ext} - AC2")
        void s3KeyShouldFollowPattern() throws MalformedURLException {
            // Given
            PresignedPutObjectRequest mockPresigned = mock(PresignedPutObjectRequest.class);
            when(mockPresigned.url()).thenReturn(new URL("https://s3.amazonaws.com/test/key"));
            when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(mockPresigned);

            // When
            TeaserImageUploadUrlResponse response = service.generateUploadUrl(EVENT_CODE, "image/png", "banner.png");

            // Then
            assertThat(response.getS3Key()).matches("events/" + EVENT_CODE + "/teaser/[0-9a-f-]{36}\\.png");
        }
    }

    // ── confirmUpload ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("confirmUpload")
    class ConfirmUploadTests {

        @Test
        @DisplayName("should call HeadObject, persist EventTeaserImage, return TeaserImageItem - AC2")
        void shouldPersistImageAndReturnItem() {
            // Given
            String s3Key = "events/" + EVENT_CODE + "/teaser/" + UUID.randomUUID() + ".jpg";
            when(teaserImageRepository.countByEventCode(EVENT_CODE)).thenReturn(0L);
            when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(HeadObjectResponse.builder().build());
            when(teaserImageRepository.findMaxDisplayOrderByEventCode(EVENT_CODE)).thenReturn(Optional.empty());

            EventTeaserImage saved = new EventTeaserImage();
            saved.setId(UUID.randomUUID());
            saved.setEventCode(EVENT_CODE);
            saved.setS3Key(s3Key);
            saved.setImageUrl(CLOUDFRONT_DOMAIN + "/" + s3Key);
            saved.setDisplayOrder(0);
            saved.setCreatedAt(OffsetDateTime.now());
            when(teaserImageRepository.save(any(EventTeaserImage.class))).thenReturn(saved);

            // When
            TeaserImageItem result = service.confirmUpload(EVENT_CODE, s3Key);

            // Then
            verify(s3Client).headObject(any(HeadObjectRequest.class));
            verify(teaserImageRepository).save(any(EventTeaserImage.class));
            assertThat(result.getId()).isEqualTo(saved.getId());
            assertThat(result.getDisplayOrder()).isEqualTo(0);
        }

        @Test
        @DisplayName("displayOrder should be previous max + 1 on confirm - AC2")
        void shouldIncrementDisplayOrder() {
            // Given
            String s3Key = "events/" + EVENT_CODE + "/teaser/" + UUID.randomUUID() + ".jpg";
            when(teaserImageRepository.countByEventCode(EVENT_CODE)).thenReturn(2L);
            when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(HeadObjectResponse.builder().build());
            when(teaserImageRepository.findMaxDisplayOrderByEventCode(EVENT_CODE)).thenReturn(Optional.of(1));

            EventTeaserImage saved = new EventTeaserImage();
            saved.setId(UUID.randomUUID());
            saved.setEventCode(EVENT_CODE);
            saved.setS3Key(s3Key);
            saved.setImageUrl(CLOUDFRONT_DOMAIN + "/" + s3Key);
            saved.setDisplayOrder(2);
            saved.setCreatedAt(OffsetDateTime.now());
            when(teaserImageRepository.save(any(EventTeaserImage.class))).thenReturn(saved);

            // When
            TeaserImageItem result = service.confirmUpload(EVENT_CODE, s3Key);

            // Then
            assertThat(result.getDisplayOrder()).isEqualTo(2);
            verify(teaserImageRepository).save(any());
        }

        @Test
        @DisplayName("should throw TeaserImageLimitExceededException when event has 10 images - AC6")
        void shouldThrowWhenLimitReached() {
            // Given
            when(teaserImageRepository.countByEventCode(EVENT_CODE)).thenReturn(10L);
            String s3Key = "events/" + EVENT_CODE + "/teaser/" + UUID.randomUUID() + ".jpg";

            // When / Then
            assertThatThrownBy(() -> service.confirmUpload(EVENT_CODE, s3Key))
                    .isInstanceOf(TeaserImageLimitExceededException.class);

            verify(s3Client, never()).headObject(any(HeadObjectRequest.class));
            verify(teaserImageRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw when S3 object is missing - AC2")
        void shouldThrowWhenS3ObjectMissing() {
            // Given
            String s3Key = "events/" + EVENT_CODE + "/teaser/" + UUID.randomUUID() + ".jpg";
            when(teaserImageRepository.countByEventCode(EVENT_CODE)).thenReturn(0L);
            when(s3Client.headObject(any(HeadObjectRequest.class)))
                    .thenThrow(NoSuchKeyException.builder().message("Not found").build());

            // When / Then
            assertThatThrownBy(() -> service.confirmUpload(EVENT_CODE, s3Key))
                    .isInstanceOf(RuntimeException.class);

            verify(teaserImageRepository, never()).save(any());
        }
    }

    // ── deleteTeaserImage ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("deleteTeaserImage")
    class DeleteTeaserImageTests {

        @Test
        @DisplayName("should call DeleteObject on S3 and delete DB row - AC3")
        void shouldDeleteS3AndDbRow() {
            // Given
            UUID imageId = UUID.randomUUID();
            String s3Key = "events/" + EVENT_CODE + "/teaser/" + imageId + ".jpg";
            EventTeaserImage image = new EventTeaserImage();
            image.setId(imageId);
            image.setEventCode(EVENT_CODE);
            image.setS3Key(s3Key);

            when(teaserImageRepository.findByIdAndEventCode(imageId, EVENT_CODE))
                    .thenReturn(Optional.of(image));

            // When
            service.deleteTeaserImage(EVENT_CODE, imageId);

            // Then
            verify(s3Client).deleteObject(any(DeleteObjectRequest.class));
            verify(teaserImageRepository).delete(image);
        }

        @Test
        @DisplayName("should throw TeaserImageNotFoundException when imageId not found - AC3")
        void shouldThrowWhenImageNotFound() {
            // Given
            UUID imageId = UUID.randomUUID();
            when(teaserImageRepository.findByIdAndEventCode(imageId, EVENT_CODE))
                    .thenReturn(Optional.empty());

            // When / Then
            assertThatThrownBy(() -> service.deleteTeaserImage(EVENT_CODE, imageId))
                    .isInstanceOf(TeaserImageNotFoundException.class);

            verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
        }
    }
}
