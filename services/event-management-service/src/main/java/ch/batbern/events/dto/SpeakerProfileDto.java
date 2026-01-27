package ch.batbern.events.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Combined speaker profile DTO containing User + Speaker data.
 * Story 6.2b: Speaker Profile Update Portal (AC1)
 *
 * User fields (from Company Service via ADR-004):
 * - username, email, firstName, lastName, bio, profilePictureUrl
 *
 * Speaker fields (from Event Service):
 * - expertiseAreas, speakingTopics, linkedInUrl, languages
 *
 * Computed fields:
 * - profileCompleteness (0-100%)
 * - missingFields (list of fields needed for 100%)
 */
@Data
@Builder
public class SpeakerProfileDto {

    // User fields (from Company Service)
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private String bio;
    private String profilePictureUrl;

    // Speaker fields (from Event Service)
    private List<String> expertiseAreas;
    private List<String> speakingTopics;
    private String linkedInUrl;
    private List<String> languages;

    // Computed fields
    private Integer profileCompleteness;
    private List<String> missingFields;

    // Navigation context (Story 6.3 AC10)
    private Boolean hasSessionAssigned;
    private String sessionTitle;
    private String eventCode;
}
