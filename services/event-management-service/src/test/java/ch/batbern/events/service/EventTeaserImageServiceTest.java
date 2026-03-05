package ch.batbern.events.service;

import ch.batbern.events.domain.EventTeaserImage;
import ch.batbern.events.dto.generated.TeaserImageItem;
import ch.batbern.events.dto.generated.TeaserImagePresentationPosition;
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
import java.util.List;
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

        @Test
        @DisplayName("s3Key should use .svg extension for SVG uploads - SVG support")
        void s3KeyShouldUseSvgExtension() throws MalformedURLException {
            // Given
            PresignedPutObjectRequest mockPresigned = mock(PresignedPutObjectRequest.class);
            when(mockPresigned.url()).thenReturn(new URL("https://s3.amazonaws.com/test/key"));
            when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(mockPresigned);

            // When
            TeaserImageUploadUrlResponse response = service.generateUploadUrl(EVENT_CODE, "image/svg+xml", "diagram.svg");

            // Then
            assertThat(response.getS3Key()).endsWith(".svg");
        }

        @Test
        @DisplayName("should throw IllegalArgumentException for disallowed content type - H1")
        void shouldThrowForDisallowedContentType() {
            // When / Then
            assertThatThrownBy(() -> service.generateUploadUrl(EVENT_CODE, "application/pdf", "doc.pdf"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unsupported content type");

            verify(s3Presigner, never()).presignPutObject(any(PutObjectPresignRequest.class));
        }

        @Test
        @DisplayName("should throw IllegalArgumentException for null content type - H1")
        void shouldThrowForNullContentType() {
            assertThatThrownBy(() -> service.generateUploadUrl(EVENT_CODE, null, "file.jpg"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unsupported content type");
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
            when(teaserImageRepository.findByEventCodeForUpdate(EVENT_CODE)).thenReturn(List.of());
            when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(HeadObjectResponse.builder().build());

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
            assertThat(result.getPresentationPosition()).isEqualTo(TeaserImagePresentationPosition.TOPIC_REVEAL);
        }

        @Test
        @DisplayName("displayOrder should be previous max + 1 on confirm - AC2")
        void shouldIncrementDisplayOrder() {
            // Given: two existing images with displayOrder 0 and 1
            String s3Key = "events/" + EVENT_CODE + "/teaser/" + UUID.randomUUID() + ".jpg";
            EventTeaserImage img0 = existingImage(0);
            EventTeaserImage img1 = existingImage(1);
            when(teaserImageRepository.findByEventCodeForUpdate(EVENT_CODE)).thenReturn(List.of(img0, img1));
            when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(HeadObjectResponse.builder().build());

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
            // Given: 10 existing images
            List<EventTeaserImage> tenImages = new java.util.ArrayList<>();
            for (int i = 0; i < 10; i++) {
                tenImages.add(existingImage(i));
            }
            when(teaserImageRepository.findByEventCodeForUpdate(EVENT_CODE)).thenReturn(tenImages);
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
            when(teaserImageRepository.findByEventCodeForUpdate(EVENT_CODE)).thenReturn(List.of());
            when(s3Client.headObject(any(HeadObjectRequest.class)))
                    .thenThrow(NoSuchKeyException.builder().message("Not found").build());

            // When / Then
            assertThatThrownBy(() -> service.confirmUpload(EVENT_CODE, s3Key))
                    .isInstanceOf(RuntimeException.class);

            verify(teaserImageRepository, never()).save(any());
        }

        private EventTeaserImage existingImage(int displayOrder) {
            EventTeaserImage img = new EventTeaserImage();
            img.setId(UUID.randomUUID());
            img.setEventCode(EVENT_CODE);
            img.setS3Key("events/" + EVENT_CODE + "/teaser/img-" + displayOrder + ".jpg");
            img.setImageUrl(CLOUDFRONT_DOMAIN + "/events/" + EVENT_CODE + "/teaser/img-" + displayOrder + ".jpg");
            img.setDisplayOrder(displayOrder);
            img.setCreatedAt(OffsetDateTime.now());
            return img;
        }
    }

    // ── updatePresentationPosition ───────────────────────────────────────────────

    @Nested
    @DisplayName("updatePresentationPosition")
    class UpdatePresentationPositionTests {

        @Test
        @DisplayName("should update position and return updated TeaserImageItem")
        void should_updatePresentationPosition_returnsUpdatedItem() {
            // Given
            UUID imageId = UUID.randomUUID();
            EventTeaserImage existing = new EventTeaserImage();
            existing.setId(imageId);
            existing.setEventCode(EVENT_CODE);
            existing.setS3Key("events/" + EVENT_CODE + "/teaser/img.jpg");
            existing.setImageUrl(CLOUDFRONT_DOMAIN + "/events/" + EVENT_CODE + "/teaser/img.jpg");
            existing.setDisplayOrder(0);
            existing.setPresentationPosition("AFTER_TOPIC_REVEAL");

            EventTeaserImage updated = new EventTeaserImage();
            updated.setId(imageId);
            updated.setEventCode(EVENT_CODE);
            updated.setS3Key(existing.getS3Key());
            updated.setImageUrl(existing.getImageUrl());
            updated.setDisplayOrder(0);
            updated.setPresentationPosition("AFTER_WELCOME");

            when(teaserImageRepository.findByIdAndEventCode(imageId, EVENT_CODE))
                    .thenReturn(Optional.of(existing));
            when(teaserImageRepository.save(any(EventTeaserImage.class))).thenReturn(updated);

            // When
            TeaserImageItem result = service.updatePresentationPosition(EVENT_CODE, imageId,
                    TeaserImagePresentationPosition.WELCOME);

            // Then
            verify(teaserImageRepository).save(any(EventTeaserImage.class));
            assertThat(result.getPresentationPosition()).isEqualTo(TeaserImagePresentationPosition.WELCOME);
        }

        @Test
        @DisplayName("should throw TeaserImageNotFoundException when image not found")
        void should_throw_TeaserImageNotFoundException_whenImageNotFound() {
            // Given
            UUID imageId = UUID.randomUUID();
            when(teaserImageRepository.findByIdAndEventCode(imageId, EVENT_CODE))
                    .thenReturn(Optional.empty());

            // When / Then
            assertThatThrownBy(() -> service.updatePresentationPosition(EVENT_CODE, imageId,
                    TeaserImagePresentationPosition.WELCOME))
                    .isInstanceOf(TeaserImageNotFoundException.class);

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
