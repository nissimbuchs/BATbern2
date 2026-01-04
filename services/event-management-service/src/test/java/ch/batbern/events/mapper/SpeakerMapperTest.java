package ch.batbern.events.mapper;

import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.domain.SpeakerStatusHistory;
import ch.batbern.events.dto.generated.speakers.SpeakerStatusResponse;
import ch.batbern.events.dto.generated.speakers.SpeakerWorkflowState;
import ch.batbern.events.dto.generated.speakers.StatusHistoryItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for SpeakerMapper following Pure Mapper pattern.
 *
 * Story: BAT-18 - Backend DTO Migration: Speakers API (ADR-003 + ADR-006)
 *
 * Tests follow TDD approach (Red-Green-Refactor):
 * - RED Phase: These tests are written BEFORE implementation
 * - They test the Pure Mapper pattern: field mapping only, no business logic
 */
class SpeakerMapperTest {

    private SpeakerMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new SpeakerMapper();
    }

    // ==================== SpeakerStatusResponse Mapping Tests ====================

    @Test
    void should_convertSpeakerPoolToStatusResponse_when_validEntityProvided() {
        // Given: SpeakerPool entity with speaker status data
        SpeakerPool entity = SpeakerPool.builder()
                .id(UUID.randomUUID())
                .eventId(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"))
                .username("john.doe")
                .status(ch.batbern.shared.types.SpeakerWorkflowState.CONTACTED)
                .updatedAt(Instant.parse("2026-01-04T10:00:00Z"))
                .build();

        String eventCode = "BATbern56";
        ch.batbern.shared.types.SpeakerWorkflowState previousStatus = ch.batbern.shared.types.SpeakerWorkflowState.IDENTIFIED;
        String changedByUsername = "admin.user";
        String changeReason = "Speaker confirmed availability";

        // When: Mapping to SpeakerStatusResponse DTO
        SpeakerStatusResponse dto = mapper.toSpeakerStatusResponse(
                entity,
                eventCode,
                previousStatus,
                changedByUsername,
                changeReason
        );

        // Then: All fields should be correctly mapped
        assertThat(dto).isNotNull();
        assertThat(dto.getSpeakerUsername()).isEqualTo("john.doe");
        assertThat(dto.getEventCode()).isEqualTo("BATbern56");
        assertThat(dto.getCurrentStatus()).isEqualTo(SpeakerWorkflowState.CONTACTED);
        assertThat(dto.getPreviousStatus()).isEqualTo(SpeakerWorkflowState.IDENTIFIED);
        assertThat(dto.getChangedByUsername()).isEqualTo("admin.user");
        assertThat(dto.getChangeReason()).isEqualTo("Speaker confirmed availability");
        assertThat(dto.getChangedAt()).isNotNull();
        assertThat(dto.getChangedAt().toInstant()).isEqualTo(Instant.parse("2026-01-04T10:00:00Z"));
    }

    @Test
    void should_returnNull_when_nullSpeakerPoolProvided() {
        // When: Null entity provided
        SpeakerStatusResponse dto = mapper.toSpeakerStatusResponse(
                null,
                "BATbern56",
                ch.batbern.shared.types.SpeakerWorkflowState.IDENTIFIED,
                "admin.user",
                "reason"
        );

        // Then: Should return null
        assertThat(dto).isNull();
    }

    // ==================== StatusHistoryItem Mapping Tests ====================

    @Test
    void should_convertStatusHistoryToDto_when_validEntityProvided() {
        // Given: SpeakerStatusHistory entity
        SpeakerStatusHistory entity = new SpeakerStatusHistory();
        entity.setId(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"));
        entity.setPreviousStatus(ch.batbern.shared.types.SpeakerWorkflowState.CONTACTED);
        entity.setNewStatus(ch.batbern.shared.types.SpeakerWorkflowState.READY);
        entity.setChangedByUsername("admin.user");
        entity.setChangeReason("Speaker expressed interest");
        entity.setChangedAt(Instant.parse("2026-01-04T11:30:00Z"));

        // When: Mapping to StatusHistoryItem DTO
        StatusHistoryItem dto = mapper.toStatusHistoryDto(entity);

        // Then: All fields should be correctly mapped
        assertThat(dto).isNotNull();
        assertThat(dto.getId()).isEqualTo(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"));
        assertThat(dto.getPreviousStatus()).isEqualTo(SpeakerWorkflowState.CONTACTED);
        assertThat(dto.getNewStatus()).isEqualTo(SpeakerWorkflowState.READY);
        assertThat(dto.getChangedByUsername()).isEqualTo("admin.user");
        assertThat(dto.getChangeReason()).isEqualTo("Speaker expressed interest");
        assertThat(dto.getChangedAt()).isNotNull();
        assertThat(dto.getChangedAt().toInstant()).isEqualTo(Instant.parse("2026-01-04T11:30:00Z"));
    }

    @Test
    void should_returnNull_when_nullStatusHistoryProvided() {
        // When: Null entity provided
        StatusHistoryItem dto = mapper.toStatusHistoryDto(null);

        // Then: Should return null
        assertThat(dto).isNull();
    }

    @Test
    void should_handleNullChangeReason_when_mappingStatusHistory() {
        // Given: StatusHistory with null changeReason
        SpeakerStatusHistory entity = new SpeakerStatusHistory();
        entity.setId(UUID.randomUUID());
        entity.setPreviousStatus(ch.batbern.shared.types.SpeakerWorkflowState.IDENTIFIED);
        entity.setNewStatus(ch.batbern.shared.types.SpeakerWorkflowState.CONTACTED);
        entity.setChangedByUsername("admin.user");
        entity.setChangeReason(null); // No reason provided
        entity.setChangedAt(Instant.now());

        // When: Mapping to DTO
        StatusHistoryItem dto = mapper.toStatusHistoryDto(entity);

        // Then: changeReason should be null in DTO
        assertThat(dto).isNotNull();
        assertThat(dto.getChangeReason()).isNull();
    }

    // ==================== Enum Conversion Tests ====================

    @Test
    void should_convertSharedEnumToGeneratedEnum_when_mapping() {
        // Given: Shared kernel enum value
        ch.batbern.shared.types.SpeakerWorkflowState sharedEnum = ch.batbern.shared.types.SpeakerWorkflowState.CONTENT_SUBMITTED;

        // When: Converting to generated enum
        SpeakerWorkflowState generatedEnum = mapper.toGeneratedWorkflowState(sharedEnum);

        // Then: Should map to correct generated enum value
        assertThat(generatedEnum).isEqualTo(SpeakerWorkflowState.CONTENT_SUBMITTED);
    }

    @Test
    void should_returnNull_when_nullEnumProvided() {
        // When: Null enum provided
        SpeakerWorkflowState generatedEnum = mapper.toGeneratedWorkflowState(null);

        // Then: Should return null
        assertThat(generatedEnum).isNull();
    }

    // ==================== Type Conversion Tests ====================

    @Test
    void should_convertInstantToOffsetDateTime_when_mapping() {
        // Given: Instant timestamp
        Instant instant = Instant.parse("2026-01-04T15:45:30Z");

        // When: Converting via mapper (exposed for testing)
        OffsetDateTime offsetDateTime = mapper.toOffsetDateTime(instant);

        // Then: Should convert correctly maintaining timestamp
        assertThat(offsetDateTime).isNotNull();
        assertThat(offsetDateTime.toInstant()).isEqualTo(instant);
    }

    @Test
    void should_returnNull_when_nullInstantProvided() {
        // When: Null instant provided
        OffsetDateTime offsetDateTime = mapper.toOffsetDateTime(null);

        // Then: Should return null
        assertThat(offsetDateTime).isNull();
    }

    // ==================== Pure Mapper Pattern Validation ====================

    @Test
    void should_notHaveRepositoryDependencies_when_mapperInstantiated() {
        // When: Mapper is instantiated
        SpeakerMapper newMapper = new SpeakerMapper();

        // Then: Should be successfully created (no repository dependencies required)
        assertThat(newMapper).isNotNull();

        // Pure Mapper pattern verification: Mapper should have no dependencies
        // This test verifies the pattern is followed by ensuring instantiation works without injection
    }
}
