package ch.batbern.events.mapper;

import ch.batbern.events.domain.Session;
import ch.batbern.events.dto.CreateSessionRequest;
import ch.batbern.events.dto.SessionResponse;
import ch.batbern.events.dto.UpdateSessionRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for SessionMapper.
 * Story BAT-90 Phase 2: Service Layer Migration
 */
class SessionMapperTest {

    private SessionMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new SessionMapper();
    }

    @Nested
    @DisplayName("toDto")
    class ToDtoTests {

        @Test
        @DisplayName("should return null when entity is null")
        void shouldReturnNullWhenEntityIsNull() {
            SessionResponse result = mapper.toDto(null);
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should map all fields from entity to DTO")
        void shouldMapAllFieldsFromEntityToDto() {
            // Given
            Instant startTime = Instant.parse("2025-06-15T14:00:00Z");
            Instant endTime = Instant.parse("2025-06-15T15:30:00Z");
            Instant createdAt = Instant.parse("2025-06-01T10:00:00Z");
            Instant updatedAt = Instant.parse("2025-06-10T15:30:00Z");

            Session entity = createTestSession(startTime, endTime, createdAt, updatedAt);

            // When
            SessionResponse result = mapper.toDto(entity);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getSessionSlug()).isEqualTo("keynote-opening");
            assertThat(result.getEventCode()).isEqualTo("BAT-25");
            assertThat(result.getTitle()).isEqualTo("Opening Keynote");
            assertThat(result.getDescription()).isEqualTo("The main opening keynote presentation");
            assertThat(result.getSessionType()).isEqualTo("keynote");
            assertThat(result.getStartTime()).isEqualTo("2025-06-15T14:00:00Z");
            assertThat(result.getEndTime()).isEqualTo("2025-06-15T15:30:00Z");
            assertThat(result.getRoom()).isEqualTo("Main Hall");
            assertThat(result.getCapacity()).isEqualTo(500);
            assertThat(result.getLanguage()).isEqualTo("de");
            assertThat(result.getCreatedAt()).isEqualTo("2025-06-01T10:00:00Z");
            assertThat(result.getUpdatedAt()).isEqualTo("2025-06-10T15:30:00Z");
            assertThat(result.getMaterialsCount()).isEqualTo(3);
            assertThat(result.getMaterialsStatus()).isEqualTo("COMPLETE");
        }

        @Test
        @DisplayName("should handle null optional fields")
        void shouldHandleNullOptionalFields() {
            // Given
            Session entity = Session.builder()
                    .sessionSlug("minimal-session")
                    .eventCode("BAT-26")
                    .title("Minimal Session")
                    .sessionType("presentation")
                    .startTime(null)
                    .endTime(null)
                    .room(null)
                    .capacity(null)
                    .language(null)
                    .createdAt(null)
                    .updatedAt(null)
                    .materialsCount(0)
                    .materialsStatus(null)
                    .build();

            // When
            SessionResponse result = mapper.toDto(entity);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getSessionSlug()).isEqualTo("minimal-session");
            assertThat(result.getTitle()).isEqualTo("Minimal Session");
            assertThat(result.getStartTime()).isNull();
            assertThat(result.getEndTime()).isNull();
            assertThat(result.getRoom()).isNull();
            assertThat(result.getCapacity()).isNull();
            assertThat(result.getLanguage()).isNull();
            assertThat(result.getCreatedAt()).isNull();
            assertThat(result.getUpdatedAt()).isNull();
        }

        @Test
        @DisplayName("should not populate speakers or materials (service layer responsibility)")
        void shouldNotPopulateSpeakersOrMaterials() {
            // Given
            Session entity = Session.builder()
                    .sessionSlug("test-session")
                    .eventCode("BAT-27")
                    .title("Test Session")
                    .sessionType("workshop")
                    .build();

            // When
            SessionResponse result = mapper.toDto(entity);

            // Then
            assertThat(result.getSpeakers()).isNull();
            assertThat(result.getMaterials()).isNull();
        }
    }

    @Nested
    @DisplayName("toEntity")
    class ToEntityTests {

        @Test
        @DisplayName("should return null when request is null")
        void shouldReturnNullWhenRequestIsNull() {
            Session result = mapper.toEntity(null);
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should map all fields from CreateSessionRequest to entity")
        void shouldMapAllFieldsFromCreateSessionRequest() {
            // Given
            CreateSessionRequest request = CreateSessionRequest.builder()
                    .title("Workshop: Microservices")
                    .description("Hands-on microservices workshop")
                    .sessionType("workshop")
                    .startTime("2025-06-15T16:00:00Z")
                    .endTime("2025-06-15T18:00:00Z")
                    .room("Workshop Room A")
                    .capacity(30)
                    .language("en")
                    .build();

            // When
            Session result = mapper.toEntity(request);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getTitle()).isEqualTo("Workshop: Microservices");
            assertThat(result.getDescription()).isEqualTo("Hands-on microservices workshop");
            assertThat(result.getSessionType()).isEqualTo("workshop");
            assertThat(result.getStartTime()).isEqualTo(Instant.parse("2025-06-15T16:00:00Z"));
            assertThat(result.getEndTime()).isEqualTo(Instant.parse("2025-06-15T18:00:00Z"));
            assertThat(result.getRoom()).isEqualTo("Workshop Room A");
            assertThat(result.getCapacity()).isEqualTo(30);
            assertThat(result.getLanguage()).isEqualTo("en");
        }

        @Test
        @DisplayName("should handle null optional fields")
        void shouldHandleNullOptionalFields() {
            // Given
            CreateSessionRequest request = CreateSessionRequest.builder()
                    .title("Minimal Session")
                    .sessionType("presentation")
                    .startTime("2025-06-15T14:00:00Z")
                    .endTime("2025-06-15T15:00:00Z")
                    .description(null)
                    .room(null)
                    .capacity(null)
                    .language(null)
                    .build();

            // When
            Session result = mapper.toEntity(request);

            // Then
            assertThat(result.getDescription()).isNull();
            assertThat(result.getRoom()).isNull();
            assertThat(result.getCapacity()).isNull();
            assertThat(result.getLanguage()).isNull();
        }
    }

    @Nested
    @DisplayName("applyUpdateRequest")
    class ApplyUpdateRequestTests {

        @Test
        @DisplayName("should do nothing when entity is null")
        void shouldDoNothingWhenEntityIsNull() {
            UpdateSessionRequest request = UpdateSessionRequest.builder()
                    .title("Updated Title")
                    .sessionType("presentation")
                    .startTime("2025-07-01T14:00:00Z")
                    .endTime("2025-07-01T15:00:00Z")
                    .build();

            // Should not throw
            mapper.applyUpdateRequest(null, request);
        }

        @Test
        @DisplayName("should do nothing when request is null")
        void shouldDoNothingWhenRequestIsNull() {
            Session entity = Session.builder()
                    .title("Original Title")
                    .build();

            mapper.applyUpdateRequest(entity, null);

            assertThat(entity.getTitle()).isEqualTo("Original Title");
        }

        @Test
        @DisplayName("should update all fields from UpdateSessionRequest")
        void shouldUpdateAllFieldsFromRequest() {
            // Given
            Session entity = Session.builder()
                    .title("Old Title")
                    .sessionType("keynote")
                    .startTime(Instant.parse("2025-06-01T10:00:00Z"))
                    .endTime(Instant.parse("2025-06-01T11:00:00Z"))
                    .room("Old Room")
                    .capacity(100)
                    .language("de")
                    .build();

            UpdateSessionRequest request = UpdateSessionRequest.builder()
                    .title("New Title")
                    .description("New description")
                    .sessionType("workshop")
                    .startTime("2025-07-15T14:00:00Z")
                    .endTime("2025-07-15T16:00:00Z")
                    .room("New Room")
                    .capacity(50)
                    .language("en")
                    .build();

            // When
            mapper.applyUpdateRequest(entity, request);

            // Then
            assertThat(entity.getTitle()).isEqualTo("New Title");
            assertThat(entity.getDescription()).isEqualTo("New description");
            assertThat(entity.getSessionType()).isEqualTo("workshop");
            assertThat(entity.getStartTime()).isEqualTo(Instant.parse("2025-07-15T14:00:00Z"));
            assertThat(entity.getEndTime()).isEqualTo(Instant.parse("2025-07-15T16:00:00Z"));
            assertThat(entity.getRoom()).isEqualTo("New Room");
            assertThat(entity.getCapacity()).isEqualTo(50);
            assertThat(entity.getLanguage()).isEqualTo("en");
        }

        @Test
        @DisplayName("should handle null values in update request")
        void shouldHandleNullValuesInUpdateRequest() {
            // Given
            Session entity = Session.builder()
                    .title("Existing Title")
                    .description("Existing description")
                    .sessionType("presentation")
                    .startTime(Instant.parse("2025-06-01T10:00:00Z"))
                    .endTime(Instant.parse("2025-06-01T11:00:00Z"))
                    .room("Existing Room")
                    .capacity(100)
                    .language("de")
                    .build();

            UpdateSessionRequest request = UpdateSessionRequest.builder()
                    .title("Updated Title")
                    .description(null) // Clear description
                    .sessionType("workshop")
                    .startTime("2025-07-01T14:00:00Z")
                    .endTime("2025-07-01T15:00:00Z")
                    .room(null) // Clear room
                    .capacity(null) // Clear capacity
                    .language(null) // Clear language
                    .build();

            // When
            mapper.applyUpdateRequest(entity, request);

            // Then - all fields are set (even to null since it's full replacement)
            assertThat(entity.getTitle()).isEqualTo("Updated Title");
            assertThat(entity.getDescription()).isNull();
            assertThat(entity.getSessionType()).isEqualTo("workshop");
            assertThat(entity.getRoom()).isNull();
            assertThat(entity.getCapacity()).isNull();
            assertThat(entity.getLanguage()).isNull();
        }
    }

    // ==================== Test Helper Methods ====================

    private Session createTestSession(Instant startTime, Instant endTime, Instant createdAt, Instant updatedAt) {
        return Session.builder()
                .id(UUID.randomUUID())
                .sessionSlug("keynote-opening")
                .eventId(UUID.randomUUID())
                .eventCode("BAT-25")
                .title("Opening Keynote")
                .description("The main opening keynote presentation")
                .sessionType("keynote")
                .startTime(startTime)
                .endTime(endTime)
                .room("Main Hall")
                .capacity(500)
                .language("de")
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .materialsCount(3)
                .hasPresentation(true)
                .materialsStatus("COMPLETE")
                .build();
    }
}
