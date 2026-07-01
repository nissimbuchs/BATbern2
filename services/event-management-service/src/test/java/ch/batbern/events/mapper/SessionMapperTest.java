package ch.batbern.events.mapper;

import ch.batbern.events.domain.Session;
import ch.batbern.events.sessions.dto.generated.SessionResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.OffsetDateTime;
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
            assertThat(result.getStartTime()).isEqualTo(OffsetDateTime.parse("2025-06-15T14:00:00Z"));
            assertThat(result.getEndTime()).isEqualTo(OffsetDateTime.parse("2025-06-15T15:30:00Z"));
            assertThat(result.getRoom()).isEqualTo("Main Hall");
            assertThat(result.getCapacity()).isEqualTo(500);
            assertThat(result.getLanguage()).isEqualTo("de");
            assertThat(result.getCreatedAt()).isEqualTo(OffsetDateTime.parse("2025-06-01T10:00:00Z"));
            assertThat(result.getUpdatedAt()).isEqualTo(OffsetDateTime.parse("2025-06-10T15:30:00Z"));
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
