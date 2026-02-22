package ch.batbern.partners.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO for a topic suggestion with vote counts — Story 8.2.
 * Returned by all topic endpoints (GET list, POST suggest, PATCH status).
 */
public record TopicDTO(
        UUID id,
        String title,
        String description,
        String suggestedByCompany,
        int voteCount,
        boolean currentPartnerHasVoted,
        String status,
        String plannedEvent,
        Instant createdAt
) {}
