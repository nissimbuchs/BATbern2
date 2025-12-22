package ch.batbern.events.mapper;

import ch.batbern.events.domain.Topic;
import ch.batbern.events.domain.TopicUsageHistory;
import ch.batbern.events.dto.generated.topics.CreateTopicRequest;
import ch.batbern.events.dto.generated.topics.SimilarityScore;
import ch.batbern.events.service.TopicService;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Pure mapper for converting between Topic entities and generated DTOs.
 *
 * Pattern: Pure Mapper (follows UserResponseMapper pattern)
 * - Field mapping only
 * - Type conversions (LocalDateTime → OffsetDateTime)
 * - Delegates business logic to TopicService
 * - NO repository dependencies
 * - NO complex data enrichment
 *
 * Business logic (colorZone, status calculation) is delegated to TopicService.
 * Similarity score UUID→topicCode conversion is handled by TopicService.
 *
 * @see ch.batbern.events.dto.generated.topics.Topic (generated DTO)
 * @see Topic (JPA entity)
 * @see TopicService#calculateColorZone(Integer)
 * @see TopicService#calculateStatus(Integer)
 * @see TopicService#convertSimilarityScoresToDtos(List)
 */
@Component
public class TopicMapper {

    /**
     * Convert Topic entity to Topic DTO (generated from OpenAPI).
     *
     * @param entity the Topic entity
     * @return the generated Topic DTO
     */
    public ch.batbern.events.dto.generated.topics.Topic toDto(Topic entity) {
        if (entity == null) {
            return null;
        }

        ch.batbern.events.dto.generated.topics.Topic dto = new ch.batbern.events.dto.generated.topics.Topic();
        dto.setTopicCode(entity.getTopicCode());
        dto.setTitle(entity.getTitle());
        dto.setDescription(entity.getDescription());
        dto.setCategory(entity.getCategory());
        dto.setCreatedDate(toOffsetDateTime(entity.getCreatedDate()));
        dto.setLastUsedDate(toOffsetDateTime(entity.getLastUsedDate()));
        dto.setUsageCount(entity.getUsageCount());
        dto.setStalenessScore(entity.getStalenessScore());

        // Delegate business logic to TopicService (static utility methods)
        dto.setColorZone(TopicService.calculateColorZone(entity.getStalenessScore()));
        dto.setStatus(TopicService.calculateStatus(entity.getStalenessScore()));

        // Note: Similarity scores must be pre-converted by caller using TopicService.convertSimilarityScoresToDtos()
        // This keeps the mapper pure without repository dependencies
        dto.setSimilarityScores(List.of()); // Default to empty list

        dto.setActive(entity.getActive());
        dto.setCreatedAt(toOffsetDateTime(entity.getCreatedAt()));
        dto.setUpdatedAt(toOffsetDateTime(entity.getUpdatedAt()));

        return dto;
    }

    /**
     * Convert Topic entity to Topic DTO with similarity scores.
     * Similarity scores must be pre-converted by TopicService.convertSimilarityScoresToDtos().
     *
     * @param entity the Topic entity
     * @param similarityScoreDtos pre-converted similarity score DTOs (with topicCode, not UUID)
     * @return the generated Topic DTO with similarity scores
     */
    public ch.batbern.events.dto.generated.topics.Topic toDtoWithSimilarityScores(
            Topic entity,
            List<SimilarityScore> similarityScoreDtos) {
        ch.batbern.events.dto.generated.topics.Topic dto = toDto(entity);
        if (dto != null && similarityScoreDtos != null) {
            dto.setSimilarityScores(similarityScoreDtos);
        }
        return dto;
    }

    /**
     * Convert Topic entity to Topic DTO with usage history.
     * Used when the DTO includes usage history data.
     *
     * @param entity the Topic entity
     * @param usageHistoryDtos the usage history DTOs
     * @return the generated Topic DTO with usage history
     */
    public ch.batbern.events.dto.generated.topics.Topic toDtoWithUsageHistory(
            Topic entity,
            List<ch.batbern.events.dto.generated.topics.TopicUsageHistory> usageHistoryDtos) {
        ch.batbern.events.dto.generated.topics.Topic dto = toDto(entity);
        if (dto != null && usageHistoryDtos != null) {
            dto.setUsageHistory(usageHistoryDtos);
        }
        return dto;
    }

    /**
     * Convert CreateTopicRequest to Topic entity.
     *
     * @param request the CreateTopicRequest DTO
     * @return a new Topic entity
     */
    public Topic toEntity(CreateTopicRequest request) {
        if (request == null) {
            return null;
        }

        Topic entity = new Topic();
        entity.setTitle(request.getTitle());
        entity.setDescription(request.getDescription());
        entity.setCategory(request.getCategory());
        // topicCode is auto-generated in @PrePersist

        return entity;
    }

    /**
     * Map usage history entity to generated DTO.
     *
     * @param entity the TopicUsageHistory entity
     * @param eventNumber the event number
     * @param eventCode the event code
     * @param eventDate the event date
     * @return the generated TopicUsageHistory DTO
     */
    public ch.batbern.events.dto.generated.topics.TopicUsageHistory mapUsageHistoryToDto(
            TopicUsageHistory entity,
            Integer eventNumber,
            String eventCode,
            java.time.Instant eventDate) {
        if (entity == null) {
            return null;
        }

        ch.batbern.events.dto.generated.topics.TopicUsageHistory dto =
                new ch.batbern.events.dto.generated.topics.TopicUsageHistory();
        dto.setEventNumber(eventNumber);
        dto.setEventCode(eventCode);
        dto.setEventDate(eventDate != null ? LocalDate.ofInstant(eventDate, ZoneId.systemDefault()) : null);
        dto.setUsedDate(toOffsetDateTime(entity.getUsedDate()));
        dto.setAttendance(entity.getAttendeeCount());
        dto.setEngagementScore(entity.getEngagementScore() != null ? entity.getEngagementScore().floatValue() : null);

        return dto;
    }

    // ==================== Private Helper Methods ====================

    /**
     * Convert LocalDateTime to OffsetDateTime (OpenAPI uses OffsetDateTime).
     */
    private java.time.OffsetDateTime toOffsetDateTime(java.time.LocalDateTime localDateTime) {
        if (localDateTime == null) {
            return null;
        }
        return localDateTime.atZone(ZoneId.systemDefault()).toOffsetDateTime();
    }
}
