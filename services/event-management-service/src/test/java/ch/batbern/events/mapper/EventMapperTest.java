package ch.batbern.events.mapper;

import ch.batbern.events.domain.Event;
import ch.batbern.events.dto.CreateEventRequest;
import ch.batbern.events.dto.EventResponse;
import ch.batbern.events.dto.PatchEventRequest;
import ch.batbern.events.dto.UpdateEventRequest;
import ch.batbern.events.dto.generated.EventType;
import ch.batbern.shared.types.EventWorkflowState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for EventMapper.
 * Story BAT-90 Phase 2: Service Layer Migration
 */
class EventMapperTest {

    private EventMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new EventMapper();
    }

    @Nested
    @DisplayName("toDto")
    class ToDtoTests {

        @Test
        @DisplayName("should return null when entity is null")
        void shouldReturnNullWhenEntityIsNull() {
            EventResponse result = mapper.toDto(null);
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should map all fields from entity to DTO")
        void shouldMapAllFieldsFromEntityToDto() {
            // Given
            Instant now = Instant.now();
            Event entity = createTestEvent(now);

            // When
            EventResponse result = mapper.toDto(entity);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getEventCode()).isEqualTo("BAT-25");
            assertThat(result.getTitle()).isEqualTo("Test Event");
            assertThat(result.getEventNumber()).isEqualTo(25);
            assertThat(result.getDate()).isEqualTo(now);
            assertThat(result.getRegistrationDeadline()).isEqualTo(now.minusSeconds(86400));
            assertThat(result.getVenueName()).isEqualTo("Test Venue");
            assertThat(result.getVenueAddress()).isEqualTo("123 Test Street");
            assertThat(result.getVenueCapacity()).isEqualTo(100);
            assertThat(result.getOrganizerUsername()).isEqualTo("organizer@test.com");
            assertThat(result.getCurrentAttendeeCount()).isEqualTo(50);
            assertThat(result.getPublishedAt()).isEqualTo(now.minusSeconds(3600));
            assertThat(result.getMetadata()).isEqualTo("{\"key\":\"value\"}");
            assertThat(result.getDescription()).isEqualTo("Test Description");
            assertThat(result.getCreatedAt()).isEqualTo(now.minusSeconds(7200));
            assertThat(result.getUpdatedAt()).isEqualTo(now.minusSeconds(1800));
            assertThat(result.getCreatedBy()).isEqualTo("admin@test.com");
            assertThat(result.getUpdatedBy()).isEqualTo("editor@test.com");
            assertThat(result.getThemeImageUrl()).isEqualTo("https://example.com/image.jpg");
            assertThat(result.getThemeImageUploadId()).isEqualTo("upload-123");
            assertThat(result.getEventType()).isEqualTo("AFTERNOON");
            assertThat(result.getTopicCode()).isEqualTo("TOPIC-001");
            assertThat(result.getWorkflowState()).isEqualTo("AGENDA_PUBLISHED");
            assertThat(result.getCurrentPublishedPhase()).isEqualTo("SPEAKERS");
        }

        @Test
        @DisplayName("should handle null enum values gracefully")
        void shouldHandleNullEnumValues() {
            // Given
            Event entity = new Event();
            entity.setEventCode("BAT-26");
            entity.setTitle("Minimal Event");
            entity.setEventType(null);
            entity.setWorkflowState(null);
            entity.setCurrentPublishedPhase(null);

            // When
            EventResponse result = mapper.toDto(entity);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getEventType()).isNull();
            assertThat(result.getWorkflowState()).isNull();
            assertThat(result.getCurrentPublishedPhase()).isNull();
        }
    }

    @Nested
    @DisplayName("toDto with registration count")
    class ToDtoWithCountTests {

        @Test
        @DisplayName("should return null when entity is null")
        void shouldReturnNullWhenEntityIsNull() {
            EventResponse result = mapper.toDto(null, 100);
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should use provided registration count instead of entity value")
        void shouldUseProvidedRegistrationCount() {
            // Given
            Event entity = new Event();
            entity.setEventCode("BAT-27");
            entity.setTitle("Test Event");
            entity.setCurrentAttendeeCount(10); // Entity has 10

            // When
            EventResponse result = mapper.toDto(entity, 75); // Override with 75

            // Then
            assertThat(result.getCurrentAttendeeCount()).isEqualTo(75);
        }
    }

    @Nested
    @DisplayName("toEntity")
    class ToEntityTests {

        @Test
        @DisplayName("should return null when request is null")
        void shouldReturnNullWhenRequestIsNull() {
            Event result = mapper.toEntity(null);
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should map all fields from CreateEventRequest to entity")
        void shouldMapAllFieldsFromCreateEventRequest() {
            // Given
            String dateStr = "2025-06-15T14:00:00Z";
            String deadlineStr = "2025-06-01T23:59:59Z";
            String publishedStr = "2025-05-01T10:00:00Z";

            CreateEventRequest request = CreateEventRequest.builder()
                    .title("New Event")
                    .eventNumber(30)
                    .date(dateStr)
                    .registrationDeadline(deadlineStr)
                    .venueName("Conference Center")
                    .venueAddress("456 Main Street")
                    .venueCapacity(200)
                    .organizerUsername("organizer@example.com")
                    .currentAttendeeCount(0)
                    .publishedAt(publishedStr)
                    .metadata("{\"theme\":\"tech\"}")
                    .description("A new tech event")
                    .eventType(EventType.FULL_DAY)
                    .themeImageUploadId("upload-456")
                    .workflowState(EventWorkflowState.TOPIC_SELECTION)
                    .build();

            // When
            Event result = mapper.toEntity(request);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getTitle()).isEqualTo("New Event");
            assertThat(result.getEventNumber()).isEqualTo(30);
            assertThat(result.getDate()).isEqualTo(Instant.parse(dateStr));
            assertThat(result.getRegistrationDeadline()).isEqualTo(Instant.parse(deadlineStr));
            assertThat(result.getVenueName()).isEqualTo("Conference Center");
            assertThat(result.getVenueAddress()).isEqualTo("456 Main Street");
            assertThat(result.getVenueCapacity()).isEqualTo(200);
            assertThat(result.getOrganizerUsername()).isEqualTo("organizer@example.com");
            assertThat(result.getCurrentAttendeeCount()).isEqualTo(0);
            assertThat(result.getPublishedAt()).isEqualTo(Instant.parse(publishedStr));
            assertThat(result.getMetadata()).isEqualTo("{\"theme\":\"tech\"}");
            assertThat(result.getDescription()).isEqualTo("A new tech event");
            assertThat(result.getEventType()).isEqualTo(EventType.FULL_DAY);
            assertThat(result.getThemeImageUploadId()).isEqualTo("upload-456");
            assertThat(result.getWorkflowState()).isEqualTo(EventWorkflowState.TOPIC_SELECTION);
        }

        @Test
        @DisplayName("should default to CREATED workflow state when not provided")
        void shouldDefaultToCreatedWorkflowState() {
            // Given
            CreateEventRequest request = CreateEventRequest.builder()
                    .title("Event Without State")
                    .date("2025-06-15T14:00:00Z")
                    .eventType(EventType.AFTERNOON)
                    .workflowState(null)
                    .build();

            // When
            Event result = mapper.toEntity(request);

            // Then
            assertThat(result.getWorkflowState()).isEqualTo(EventWorkflowState.CREATED);
        }

        @Test
        @DisplayName("should handle null date strings")
        void shouldHandleNullDateStrings() {
            // Given
            CreateEventRequest request = CreateEventRequest.builder()
                    .title("Minimal Event")
                    .date("2025-06-15T14:00:00Z")
                    .eventType(EventType.EVENING)
                    .registrationDeadline(null)
                    .publishedAt(null)
                    .build();

            // When
            Event result = mapper.toEntity(request);

            // Then
            assertThat(result.getRegistrationDeadline()).isNull();
            assertThat(result.getPublishedAt()).isNull();
        }
    }

    @Nested
    @DisplayName("applyUpdateRequest")
    class ApplyUpdateRequestTests {

        @Test
        @DisplayName("should do nothing when entity is null")
        void shouldDoNothingWhenEntityIsNull() {
            UpdateEventRequest request = UpdateEventRequest.builder()
                    .title("Updated Title")
                    .date("2025-07-01T14:00:00Z")
                    .build();

            // Should not throw
            mapper.applyUpdateRequest(null, request);
        }

        @Test
        @DisplayName("should do nothing when request is null")
        void shouldDoNothingWhenRequestIsNull() {
            Event entity = new Event();
            entity.setTitle("Original Title");

            mapper.applyUpdateRequest(entity, null);

            assertThat(entity.getTitle()).isEqualTo("Original Title");
        }

        @Test
        @DisplayName("should update all fields from UpdateEventRequest")
        void shouldUpdateAllFieldsFromRequest() {
            // Given
            Event entity = new Event();
            entity.setTitle("Old Title");
            entity.setEventType(EventType.AFTERNOON);
            entity.setWorkflowState(EventWorkflowState.CREATED);

            UpdateEventRequest request = UpdateEventRequest.builder()
                    .title("New Title")
                    .eventNumber(35)
                    .date("2025-07-15T14:00:00Z")
                    .registrationDeadline("2025-07-01T23:59:59Z")
                    .venueName("Updated Venue")
                    .venueAddress("789 New Street")
                    .venueCapacity(150)
                    .organizerUsername("new-organizer@example.com")
                    .currentAttendeeCount(25)
                    .publishedAt("2025-06-01T10:00:00Z")
                    .metadata("{\"updated\":true}")
                    .description("Updated description")
                    .themeImageUploadId("upload-789")
                    .eventType("FULL_DAY")
                    .workflowState(EventWorkflowState.AGENDA_PUBLISHED)
                    .build();

            // When
            mapper.applyUpdateRequest(entity, request);

            // Then
            assertThat(entity.getTitle()).isEqualTo("New Title");
            assertThat(entity.getEventNumber()).isEqualTo(35);
            assertThat(entity.getDate()).isEqualTo(Instant.parse("2025-07-15T14:00:00Z"));
            assertThat(entity.getRegistrationDeadline()).isEqualTo(Instant.parse("2025-07-01T23:59:59Z"));
            assertThat(entity.getVenueName()).isEqualTo("Updated Venue");
            assertThat(entity.getVenueAddress()).isEqualTo("789 New Street");
            assertThat(entity.getVenueCapacity()).isEqualTo(150);
            assertThat(entity.getOrganizerUsername()).isEqualTo("new-organizer@example.com");
            assertThat(entity.getCurrentAttendeeCount()).isEqualTo(25);
            assertThat(entity.getPublishedAt()).isEqualTo(Instant.parse("2025-06-01T10:00:00Z"));
            assertThat(entity.getMetadata()).isEqualTo("{\"updated\":true}");
            assertThat(entity.getDescription()).isEqualTo("Updated description");
            assertThat(entity.getThemeImageUploadId()).isEqualTo("upload-789");
            assertThat(entity.getEventType()).isEqualTo(EventType.FULL_DAY);
            assertThat(entity.getWorkflowState()).isEqualTo(EventWorkflowState.AGENDA_PUBLISHED);
        }
    }

    @Nested
    @DisplayName("applyPatchRequest")
    class ApplyPatchRequestTests {

        @Test
        @DisplayName("should do nothing when entity is null")
        void shouldDoNothingWhenEntityIsNull() {
            PatchEventRequest request = PatchEventRequest.builder()
                    .title("Patched Title")
                    .build();

            // Should not throw
            mapper.applyPatchRequest(null, request);
        }

        @Test
        @DisplayName("should do nothing when request is null")
        void shouldDoNothingWhenRequestIsNull() {
            Event entity = new Event();
            entity.setTitle("Original Title");

            mapper.applyPatchRequest(entity, null);

            assertThat(entity.getTitle()).isEqualTo("Original Title");
        }

        @Test
        @DisplayName("should only update non-null fields")
        void shouldOnlyUpdateNonNullFields() {
            // Given
            Instant originalDate = Instant.parse("2025-06-15T14:00:00Z");
            Event entity = new Event();
            entity.setTitle("Original Title");
            entity.setEventNumber(20);
            entity.setDate(originalDate);
            entity.setVenueName("Original Venue");
            entity.setDescription("Original Description");

            // Only patch title and description
            PatchEventRequest request = PatchEventRequest.builder()
                    .title("Patched Title")
                    .description("Patched Description")
                    .build();

            // When
            mapper.applyPatchRequest(entity, request);

            // Then - patched fields are updated
            assertThat(entity.getTitle()).isEqualTo("Patched Title");
            assertThat(entity.getDescription()).isEqualTo("Patched Description");

            // Then - non-patched fields remain unchanged
            assertThat(entity.getEventNumber()).isEqualTo(20);
            assertThat(entity.getDate()).isEqualTo(originalDate);
            assertThat(entity.getVenueName()).isEqualTo("Original Venue");
        }

        @Test
        @DisplayName("should handle all patchable fields")
        void shouldHandleAllPatchableFields() {
            // Given
            Event entity = new Event();
            entity.setEventType(EventType.AFTERNOON);
            entity.setWorkflowState(EventWorkflowState.CREATED);

            PatchEventRequest request = PatchEventRequest.builder()
                    .title("Full Patch Title")
                    .eventNumber(40)
                    .date("2025-08-01T14:00:00Z")
                    .registrationDeadline("2025-07-15T23:59:59Z")
                    .venueName("Patch Venue")
                    .venueAddress("999 Patch Road")
                    .venueCapacity(300)
                    .organizerUsername("patch-org@example.com")
                    .currentAttendeeCount(100)
                    .publishedAt("2025-07-01T10:00:00Z")
                    .metadata("{\"patched\":true}")
                    .description("Fully patched")
                    .themeImageUploadId("patch-upload")
                    .eventType("EVENING")
                    .workflowState(EventWorkflowState.SLOT_ASSIGNMENT)
                    .build();

            // When
            mapper.applyPatchRequest(entity, request);

            // Then
            assertThat(entity.getTitle()).isEqualTo("Full Patch Title");
            assertThat(entity.getEventNumber()).isEqualTo(40);
            assertThat(entity.getDate()).isEqualTo(Instant.parse("2025-08-01T14:00:00Z"));
            assertThat(entity.getRegistrationDeadline()).isEqualTo(Instant.parse("2025-07-15T23:59:59Z"));
            assertThat(entity.getVenueName()).isEqualTo("Patch Venue");
            assertThat(entity.getVenueAddress()).isEqualTo("999 Patch Road");
            assertThat(entity.getVenueCapacity()).isEqualTo(300);
            assertThat(entity.getOrganizerUsername()).isEqualTo("patch-org@example.com");
            assertThat(entity.getCurrentAttendeeCount()).isEqualTo(100);
            assertThat(entity.getPublishedAt()).isEqualTo(Instant.parse("2025-07-01T10:00:00Z"));
            assertThat(entity.getMetadata()).isEqualTo("{\"patched\":true}");
            assertThat(entity.getDescription()).isEqualTo("Fully patched");
            assertThat(entity.getThemeImageUploadId()).isEqualTo("patch-upload");
            assertThat(entity.getEventType()).isEqualTo(EventType.EVENING);
            assertThat(entity.getWorkflowState()).isEqualTo(EventWorkflowState.SLOT_ASSIGNMENT);
        }
    }

    // ==================== Test Helper Methods ====================

    private Event createTestEvent(Instant now) {
        Event entity = new Event();
        entity.setId(UUID.randomUUID());
        entity.setEventCode("BAT-25");
        entity.setTitle("Test Event");
        entity.setEventNumber(25);
        entity.setDate(now);
        entity.setRegistrationDeadline(now.minusSeconds(86400)); // 1 day before
        entity.setVenueName("Test Venue");
        entity.setVenueAddress("123 Test Street");
        entity.setVenueCapacity(100);
        entity.setOrganizerUsername("organizer@test.com");
        entity.setCurrentAttendeeCount(50);
        entity.setPublishedAt(now.minusSeconds(3600)); // 1 hour before
        entity.setMetadata("{\"key\":\"value\"}");
        entity.setDescription("Test Description");
        entity.setCreatedAt(now.minusSeconds(7200)); // 2 hours before
        entity.setUpdatedAt(now.minusSeconds(1800)); // 30 minutes before
        entity.setCreatedBy("admin@test.com");
        entity.setUpdatedBy("editor@test.com");
        entity.setThemeImageUrl("https://example.com/image.jpg");
        entity.setThemeImageUploadId("upload-123");
        entity.setEventType(EventType.AFTERNOON);
        entity.setTopicCode("TOPIC-001");
        entity.setWorkflowState(EventWorkflowState.AGENDA_PUBLISHED);
        entity.setCurrentPublishedPhase("speakers");
        return entity;
    }
}
