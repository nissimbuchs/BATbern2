package ch.batbern.events.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Request DTO for updating speaker profile.
 * Story 6.2b: Speaker Profile Update Portal (AC2-6)
 *
 * All fields are optional - only provided fields are updated.
 *
 * User fields (synced to Company Service):
 * - firstName, lastName, bio
 *
 * Speaker fields (stored in Event Service):
 * - expertiseAreas, speakingTopics, linkedInUrl, languages
 *
 * Validation rules:
 * - bio: max 500 characters
 * - expertiseAreas: max 10 items
 * - speakingTopics: max 10 items
 * - linkedInUrl: must be valid LinkedIn URL or empty
 */
@Data
@Builder
public class ProfileUpdateRequest {

    // User fields (synced to Company Service)
    private String firstName;
    private String lastName;
    private String bio;

    // Speaker fields (stored in Event Service)
    private List<String> expertiseAreas;
    private List<String> speakingTopics;
    private String linkedInUrl;
    private List<String> languages;
}
