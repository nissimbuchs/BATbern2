package ch.batbern.events.mapper;

import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.domain.SpeakerStatusHistory;
import ch.batbern.events.dto.generated.speakers.SpeakerStatusResponse;
import ch.batbern.events.dto.generated.speakers.StatusHistoryItem;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * Pure mapper for converting between Speaker entities and generated DTOs.
 *
 * Pattern: Pure Mapper (follows TopicMapper pattern)
 * - Field mapping only
 * - Type conversions (Instant → OffsetDateTime, shared enum → generated enum)
 * - NO repository dependencies
 * - NO complex data enrichment
 * - NO business logic
 *
 * Story: BAT-18 - Backend DTO Migration: Speakers API (ADR-003 + ADR-006)
 *
 * @see ch.batbern.events.dto.generated.speakers.SpeakerStatusResponse (generated DTO)
 * @see ch.batbern.events.dto.generated.speakers.StatusHistoryItem (generated DTO)
 * @see SpeakerPool (JPA entity)
 * @see SpeakerStatusHistory (JPA entity)
 */
@Component
public class SpeakerMapper {

    /**
     * Convert SpeakerPool entity to SpeakerStatusResponse DTO.
     *
     * @param entity the SpeakerPool entity
     * @param eventCode the event code (meaningful identifier from Event entity)
     * @param previousStatus the previous workflow state
     * @param changedByUsername the username who changed the status
     * @param changeReason the reason for the status change
     * @return the generated SpeakerStatusResponse DTO
     */
    public SpeakerStatusResponse toSpeakerStatusResponse(
            SpeakerPool entity,
            String eventCode,
            ch.batbern.shared.types.SpeakerWorkflowState previousStatus,
            String changedByUsername,
            String changeReason) {

        if (entity == null) {
            return null;
        }

        SpeakerStatusResponse dto = new SpeakerStatusResponse();
        dto.setSpeakerUsername(entity.getUsername());
        dto.setEventCode(eventCode);
        dto.setCurrentStatus(toGeneratedWorkflowState(entity.getStatus()));
        dto.setPreviousStatus(toGeneratedWorkflowState(previousStatus));
        dto.setChangedByUsername(changedByUsername);
        dto.setChangeReason(changeReason);
        dto.setChangedAt(toOffsetDateTime(entity.getUpdatedAt()));

        return dto;
    }

    /**
     * Convert SpeakerStatusHistory entity to StatusHistoryItem DTO.
     *
     * @param entity the SpeakerStatusHistory entity
     * @return the generated StatusHistoryItem DTO
     */
    public StatusHistoryItem toStatusHistoryDto(SpeakerStatusHistory entity) {
        if (entity == null) {
            return null;
        }

        StatusHistoryItem dto = new StatusHistoryItem();
        dto.setId(entity.getId()); // UUID field (internal identifier)
        dto.setPreviousStatus(toGeneratedWorkflowState(entity.getPreviousStatus()));
        dto.setNewStatus(toGeneratedWorkflowState(entity.getNewStatus()));
        dto.setChangedByUsername(entity.getChangedByUsername());
        dto.setChangeReason(entity.getChangeReason());
        dto.setChangedAt(toOffsetDateTime(entity.getChangedAt()));

        return dto;
    }

    /**
     * Convert shared-kernel SpeakerWorkflowState enum to generated SpeakerWorkflowState enum.
     *
     * The OpenAPI generator creates its own enum types in the generated DTOs.
     * This method converts between the shared-kernel enum and the generated enum.
     *
     * @param sharedEnum the shared-kernel SpeakerWorkflowState enum
     * @return the generated SpeakerWorkflowState enum
     */
    public ch.batbern.events.dto.generated.speakers.SpeakerWorkflowState toGeneratedWorkflowState(
            ch.batbern.shared.types.SpeakerWorkflowState sharedEnum) {

        if (sharedEnum == null) {
            return null;
        }

        // Map enum values by name (both enums have same value names)
        return ch.batbern.events.dto.generated.speakers.SpeakerWorkflowState.valueOf(sharedEnum.name());
    }

    /**
     * Convert Instant to OffsetDateTime (OpenAPI uses OffsetDateTime).
     * This is a public method to allow testing of type conversion logic.
     *
     * @param instant the Instant timestamp
     * @return OffsetDateTime in system default timezone
     */
    public OffsetDateTime toOffsetDateTime(Instant instant) {
        if (instant == null) {
            return null;
        }
        return instant.atZone(ZoneId.systemDefault()).toOffsetDateTime();
    }
}
