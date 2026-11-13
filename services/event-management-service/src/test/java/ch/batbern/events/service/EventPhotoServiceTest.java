package ch.batbern.events.service;

import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.EventPhoto;
import ch.batbern.events.media.dto.generated.EventPhotoResponse;
import ch.batbern.events.exception.EventPhotoNotFoundException;
import ch.batbern.events.repository.EventManagementUploadRepository;
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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for EventPhotoService.
 * Story 10.21: Event Photos Gallery — AC9 (TDD)
 *
 * Tests cover (the upload is EventPhotoUploadIntegrationTest, Story 17.11b):
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
    private EventManagementUploadRepository uploadRepository;

    @Mock
    private S3Client s3Client;

    private EventPhotoService service;

    private static final String BUCKET_NAME = "test-bucket";
    private static final String CLOUDFRONT_DOMAIN = "https://cdn.batbern.ch";
    private static final String EVENT_CODE = "BATbern42";

    @BeforeEach
    void setUp() {
        service = new EventPhotoService(
                photoRepository,
                eventRepository,
                uploadRepository,
                s3Client,
                BUCKET_NAME
        );
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

            when(eventRepository.findByDateBeforeOrderByDateDesc(any(), any())).thenReturn(recentEvents);
            when(photoRepository.findByEventCodeIn(any())).thenReturn(allPhotos);

            // When
            List<EventPhotoResponse> result = service.getRecentPhotos(20, 5);

            // Then
            assertThat(result).hasSize(20);
        }

        @Test
        @DisplayName("should return all photos when total < limit - AC5")
        void shouldReturnAllWhenFewerThanLimit() {
            // Given: 3 photos total, limit=20
            List<Event> recentEvents = buildEvents(1);
            List<EventPhoto> photos = buildPhotos(recentEvents, 3);

            when(eventRepository.findByDateBeforeOrderByDateDesc(any(), any())).thenReturn(recentEvents);
            when(photoRepository.findByEventCodeIn(any())).thenReturn(photos);

            // When
            List<EventPhotoResponse> result = service.getRecentPhotos(20, 5);

            // Then
            assertThat(result).hasSize(3);
        }

        @Test
        @DisplayName("should return empty list when no recent events - AC5")
        void shouldReturnEmptyWhenNoEvents() {
            // Given
            when(eventRepository.findByDateBeforeOrderByDateDesc(any(), any())).thenReturn(List.of());

            // When
            List<EventPhotoResponse> result = service.getRecentPhotos(20, 5);

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
