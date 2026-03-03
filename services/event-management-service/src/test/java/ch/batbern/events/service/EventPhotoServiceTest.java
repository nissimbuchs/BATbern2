package ch.batbern.events.service;

import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.EventPhoto;
import ch.batbern.events.dto.EventPhotoConfirmRequestDto;
import ch.batbern.events.dto.EventPhotoResponseDto;
import ch.batbern.events.dto.EventPhotoUploadRequestDto;
import ch.batbern.events.dto.EventPhotoUploadResponseDto;
import ch.batbern.events.exception.EventPhotoNotFoundException;
import ch.batbern.events.exception.EventNotFoundException;
import ch.batbern.events.exception.InvalidFileTypeException;
import ch.batbern.events.exception.PhotoUploadNotFoundException;
import ch.batbern.events.repository.EventPhotoRepository;
import ch.batbern.events.repository.EventRepository;
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
import java.time.Instant;
import java.util.ArrayList;
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
 * Unit tests for EventPhotoService.
 * Story 10.21: Event Photos Gallery — AC9 (TDD)
 *
 * Tests cover:
 * - AC2: 3-phase presigned upload URL (requestUploadUrl)
 * - AC3: Delete photo (DB + S3)
 * - AC4: List photos for event (listPhotos)
 * - AC5: Recent photos from last N events (getRecentPhotos)
 */
@ExtendWith(MockitoExtension.class)
class EventPhotoServiceTest {

    @Mock
    private EventPhotoRepository photoRepository;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private S3Presigner s3Presigner;

    @Mock
    private S3Client s3Client;

    private EventPhotoService service;

    private static final String BUCKET_NAME = "test-bucket";
    private static final String CLOUDFRONT_DOMAIN = "https://cdn.batbern.ch";
    private static final String EVENT_CODE = "BATbern42";
    private static final String UPLOADER = "organizer@batbern.ch";

    @BeforeEach
    void setUp() {
        service = new EventPhotoService(
                photoRepository,
                eventRepository,
                s3Presigner,
                s3Client,
                BUCKET_NAME,
                CLOUDFRONT_DOMAIN
        );
    }

    // ── requestUploadUrl ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("requestUploadUrl")
    class RequestUploadUrlTests {

        @Test
        @DisplayName("should return uploadUrl, photoId, s3Key with .jpg extension for JPEG - AC2")
        void shouldReturnUploadUrlForJpeg() throws MalformedURLException {
            // Given
            EventPhotoUploadRequestDto request = EventPhotoUploadRequestDto.builder()
                    .filename("event-photo.jpg")
                    .contentType("image/jpeg")
                    .fileSize(2L * 1024 * 1024)
                    .build();

            when(eventRepository.findByEventCode(EVENT_CODE))
                    .thenReturn(Optional.of(new Event()));

            PresignedPutObjectRequest mockPresigned = mock(PresignedPutObjectRequest.class);
            when(mockPresigned.url()).thenReturn(new URL("https://s3.amazonaws.com/test-bucket/key?sig=x"));
            when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(mockPresigned);

            // When
            EventPhotoUploadResponseDto response = service.requestUploadUrl(EVENT_CODE, request, UPLOADER);

            // Then
            assertThat(response.getPhotoId()).isNotNull();
            assertThat(response.getUploadUrl()).contains("s3.amazonaws.com");
            assertThat(response.getS3Key()).startsWith("events/" + EVENT_CODE + "/photos/");
            assertThat(response.getS3Key()).endsWith(".jpg");
            assertThat(response.getExpiresIn()).isEqualTo(900);
        }

        @Test
        @DisplayName("should produce s3Key ending in .png for image/png content type - AC2")
        void shouldProducePngExtensionForPng() throws MalformedURLException {
            // Given
            EventPhotoUploadRequestDto request = EventPhotoUploadRequestDto.builder()
                    .filename("photo.png")
                    .contentType("image/png")
                    .fileSize(1024L)
                    .build();

            when(eventRepository.findByEventCode(EVENT_CODE))
                    .thenReturn(Optional.of(new Event()));

            PresignedPutObjectRequest mockPresigned = mock(PresignedPutObjectRequest.class);
            when(mockPresigned.url()).thenReturn(new URL("https://s3.amazonaws.com/test/key"));
            when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(mockPresigned);

            // When
            EventPhotoUploadResponseDto response = service.requestUploadUrl(EVENT_CODE, request, UPLOADER);

            // Then
            assertThat(response.getS3Key()).endsWith(".png");
        }

        @Test
        @DisplayName("should throw EventNotFoundException when event does not exist - AC2")
        void shouldThrowWhenEventNotFound() {
            // Given
            when(eventRepository.findByEventCode(EVENT_CODE)).thenReturn(Optional.empty());

            EventPhotoUploadRequestDto request = EventPhotoUploadRequestDto.builder()
                    .filename("photo.jpg")
                    .contentType("image/jpeg")
                    .fileSize(1024L)
                    .build();

            // When / Then
            assertThatThrownBy(() -> service.requestUploadUrl(EVENT_CODE, request, UPLOADER))
                    .isInstanceOf(EventNotFoundException.class);
        }

        @Test
        @DisplayName("should throw InvalidFileTypeException for disallowed content type - AC2")
        void shouldThrowForDisallowedContentType() {
            // Given
            when(eventRepository.findByEventCode(EVENT_CODE))
                    .thenReturn(Optional.of(new Event()));

            EventPhotoUploadRequestDto request = EventPhotoUploadRequestDto.builder()
                    .filename("file.gif")
                    .contentType("image/gif")
                    .fileSize(1024L)
                    .build();

            // When / Then
            assertThatThrownBy(() -> service.requestUploadUrl(EVENT_CODE, request, UPLOADER))
                    .isInstanceOf(InvalidFileTypeException.class);
        }

        @Test
        @DisplayName("should throw InvalidFileTypeException for null content type - C2/H3")
        void shouldThrowForNullContentType() {
            // Given
            when(eventRepository.findByEventCode(EVENT_CODE))
                    .thenReturn(Optional.of(new Event()));

            EventPhotoUploadRequestDto request = EventPhotoUploadRequestDto.builder()
                    .filename("file.jpg")
                    .contentType(null)
                    .fileSize(1024L)
                    .build();

            // When / Then
            assertThatThrownBy(() -> service.requestUploadUrl(EVENT_CODE, request, UPLOADER))
                    .isInstanceOf(InvalidFileTypeException.class);
        }

        @Test
        @DisplayName("should throw InvalidFileTypeException when file exceeds 10 MB limit - C2")
        void shouldThrowWhenFileSizeExceedsLimit() {
            // Given
            when(eventRepository.findByEventCode(EVENT_CODE))
                    .thenReturn(Optional.of(new Event()));

            long oversizedFile = 11L * 1024 * 1024; // 11 MB
            EventPhotoUploadRequestDto request = EventPhotoUploadRequestDto.builder()
                    .filename("big-photo.jpg")
                    .contentType("image/jpeg")
                    .fileSize(oversizedFile)
                    .build();

            // When / Then
            assertThatThrownBy(() -> service.requestUploadUrl(EVENT_CODE, request, UPLOADER))
                    .isInstanceOf(InvalidFileTypeException.class)
                    .hasMessageContaining("10 MB");
        }
    }

    // ── confirmUpload ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("confirmUpload")
    class ConfirmUploadTests {

        @Test
        @DisplayName("should verify S3, save EventPhoto with CloudFront displayUrl - AC2")
        void shouldSavePhotoWithCloudFrontUrl() {
            // Given
            UUID photoId = UUID.randomUUID();
            String s3Key = "events/" + EVENT_CODE + "/photos/" + photoId + ".jpg";

            EventPhotoConfirmRequestDto request = EventPhotoConfirmRequestDto.builder()
                    .photoId(photoId)
                    .s3Key(s3Key)
                    .build();

            when(s3Client.headObject(any(HeadObjectRequest.class)))
                    .thenReturn(HeadObjectResponse.builder().build());

            EventPhoto savedPhoto = EventPhoto.builder()
                    .id(photoId)
                    .eventCode(EVENT_CODE)
                    .s3Key(s3Key)
                    .displayUrl(CLOUDFRONT_DOMAIN + "/" + s3Key)
                    .uploadedAt(Instant.now())
                    .uploadedBy(UPLOADER)
                    .sortOrder(0)
                    .build();
            when(photoRepository.save(any(EventPhoto.class))).thenReturn(savedPhoto);

            // When
            EventPhotoResponseDto result = service.confirmUpload(EVENT_CODE, request, UPLOADER);

            // Then
            verify(s3Client).headObject(any(HeadObjectRequest.class));
            verify(photoRepository).save(any(EventPhoto.class));
            assertThat(result.getDisplayUrl()).startsWith(CLOUDFRONT_DOMAIN);
            assertThat(result.getEventCode()).isEqualTo(EVENT_CODE);
        }

        @Test
        @DisplayName("should throw InvalidFileTypeException when s3Key does not belong to eventCode - H1")
        void shouldThrowWhenS3KeyDoesNotBelongToEvent() {
            // Given
            UUID photoId = UUID.randomUUID();
            String foreignS3Key = "events/OTHER_EVENT/photos/" + photoId + ".jpg";

            EventPhotoConfirmRequestDto request = EventPhotoConfirmRequestDto.builder()
                    .photoId(photoId)
                    .s3Key(foreignS3Key)
                    .build();

            // When / Then
            assertThatThrownBy(() -> service.confirmUpload(EVENT_CODE, request, UPLOADER))
                    .isInstanceOf(InvalidFileTypeException.class);

            verify(s3Client, never()).headObject(any(HeadObjectRequest.class));
            verify(photoRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw PhotoUploadNotFoundException when file not in S3 - AC2")
        void shouldThrowWhenFileNotInS3() {
            // Given
            UUID photoId = UUID.randomUUID();
            String s3Key = "events/" + EVENT_CODE + "/photos/" + photoId + ".jpg";

            EventPhotoConfirmRequestDto request = EventPhotoConfirmRequestDto.builder()
                    .photoId(photoId)
                    .s3Key(s3Key)
                    .build();

            when(s3Client.headObject(any(HeadObjectRequest.class)))
                    .thenThrow(NoSuchKeyException.builder().message("Not found").build());

            // When / Then
            assertThatThrownBy(() -> service.confirmUpload(EVENT_CODE, request, UPLOADER))
                    .isInstanceOf(PhotoUploadNotFoundException.class);

            verify(photoRepository, never()).save(any());
        }
    }

    // ── deletePhoto ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("deletePhoto")
    class DeletePhotoTests {

        @Test
        @DisplayName("should delete from S3 and DB when photo exists - AC3")
        void shouldDeleteFromS3AndDb() {
            // Given
            UUID photoId = UUID.randomUUID();
            String s3Key = "events/" + EVENT_CODE + "/photos/" + photoId + ".jpg";

            EventPhoto photo = EventPhoto.builder()
                    .id(photoId)
                    .eventCode(EVENT_CODE)
                    .s3Key(s3Key)
                    .build();

            when(photoRepository.findByIdAndEventCode(photoId, EVENT_CODE))
                    .thenReturn(Optional.of(photo));

            // When
            service.deletePhoto(EVENT_CODE, photoId);

            // Then
            verify(s3Client).deleteObject(any(DeleteObjectRequest.class));
            verify(photoRepository).delete(photo);
        }

        @Test
        @DisplayName("should throw EventPhotoNotFoundException when photo does not belong to event - AC3")
        void shouldThrowWhenPhotoNotBelongingToEvent() {
            // Given
            UUID photoId = UUID.randomUUID();

            when(photoRepository.findByIdAndEventCode(photoId, EVENT_CODE))
                    .thenReturn(Optional.empty());

            // When / Then
            assertThatThrownBy(() -> service.deletePhoto(EVENT_CODE, photoId))
                    .isInstanceOf(EventPhotoNotFoundException.class);

            verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
        }
    }

    // ── getRecentPhotos ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getRecentPhotos")
    class GetRecentPhotosTests {

        @Test
        @DisplayName("should return at most 'limit' photos from last N events - AC5")
        void shouldReturnAtMostLimitPhotos() {
            // Given: 5 recent events each with 5 photos = 25 total, limit=20
            List<Event> recentEvents = buildEvents(5);
            List<EventPhoto> allPhotos = buildPhotos(recentEvents, 5);

            when(eventRepository.findTopByOrderByDateDesc(any())).thenReturn(recentEvents);
            when(photoRepository.findByEventCodeIn(any())).thenReturn(allPhotos);

            // When
            List<EventPhotoResponseDto> result = service.getRecentPhotos(20, 5);

            // Then
            assertThat(result).hasSize(20);
        }

        @Test
        @DisplayName("should return all photos when total < limit - AC5")
        void shouldReturnAllWhenFewerThanLimit() {
            // Given: 3 photos total, limit=20
            List<Event> recentEvents = buildEvents(1);
            List<EventPhoto> photos = buildPhotos(recentEvents, 3);

            when(eventRepository.findTopByOrderByDateDesc(any())).thenReturn(recentEvents);
            when(photoRepository.findByEventCodeIn(any())).thenReturn(photos);

            // When
            List<EventPhotoResponseDto> result = service.getRecentPhotos(20, 5);

            // Then
            assertThat(result).hasSize(3);
        }

        @Test
        @DisplayName("should return empty list when no recent events - AC5")
        void shouldReturnEmptyWhenNoEvents() {
            // Given
            when(eventRepository.findTopByOrderByDateDesc(any())).thenReturn(List.of());

            // When
            List<EventPhotoResponseDto> result = service.getRecentPhotos(20, 5);

            // Then
            assertThat(result).isEmpty();
        }

        private List<Event> buildEvents(int count) {
            List<Event> events = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                Event e = new Event();
                e.setEventCode("BATbern" + (40 + i));
                events.add(e);
            }
            return events;
        }

        private List<EventPhoto> buildPhotos(List<Event> events, int perEvent) {
            List<EventPhoto> photos = new ArrayList<>();
            for (Event event : events) {
                for (int i = 0; i < perEvent; i++) {
                    photos.add(EventPhoto.builder()
                            .id(UUID.randomUUID())
                            .eventCode(event.getEventCode())
                            .s3Key("events/" + event.getEventCode() + "/photos/photo" + i + ".jpg")
                            .displayUrl(CLOUDFRONT_DOMAIN + "/photo" + i + ".jpg")
                            .uploadedAt(Instant.now())
                            .sortOrder(0)
                            .build());
                }
            }
            return photos;
        }
    }
}
