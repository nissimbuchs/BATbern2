package ch.batbern.partners.dto;

/**
 * Request body for suggesting a new topic — Story 8.2 AC3.
 */
public record TopicSuggestionRequest(
        String title,
        String description
) {}
