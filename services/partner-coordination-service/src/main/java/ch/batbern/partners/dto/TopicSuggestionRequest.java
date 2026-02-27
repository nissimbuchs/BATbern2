package ch.batbern.partners.dto;

/**
 * Request body for suggesting a new topic — Story 8.2 AC3.
 *
 * {@code companyName} is an organizer-only field used when an organizer records a topic
 * suggestion on behalf of a specific partner company (e.g. during a partner meeting).
 * Partners must not supply this field — it is ignored and the company is resolved from
 * their JWT instead.
 */
public record TopicSuggestionRequest(
        String title,
        String description,
        String companyName
) {}
