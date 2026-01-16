package ch.batbern.events.dto;

import ch.batbern.events.domain.SpeakerAvailability;
import ch.batbern.shared.types.SpeakerWorkflowState;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Response DTO for Speaker entity - Story 6.0.
 *
 * Combines Speaker domain data with User data from HTTP enrichment (ADR-004).
 * User fields are populated via UserApiClient HTTP call.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpeakerResponse {

    // --- User fields (from User Service HTTP enrichment) ---

    /**
     * Username as public identifier (ADR-003).
     */
    private String username;

    /**
     * User's email address (from User Service).
     */
    private String email;

    /**
     * User's first name (from User Service).
     */
    private String firstName;

    /**
     * User's last name (from User Service).
     */
    private String lastName;

    /**
     * User's biography (from User Service - single source of truth).
     */
    private String bio;

    /**
     * Profile picture URL (from User Service).
     */
    private String profilePictureUrl;

    /**
     * Company name/ID (from User Service).
     */
    private String company;

    // --- Speaker domain fields (from Speaker entity) ---

    /**
     * Speaker availability status.
     */
    private SpeakerAvailability availability;

    /**
     * Speaker workflow state.
     */
    private SpeakerWorkflowState workflowState;

    /**
     * Areas of technical expertise.
     */
    private List<String> expertiseAreas;

    /**
     * Topics the speaker can present on.
     */
    private List<String> speakingTopics;

    /**
     * LinkedIn profile URL.
     */
    private String linkedInUrl;

    /**
     * Twitter/X handle.
     */
    private String twitterHandle;

    /**
     * Professional certifications.
     */
    private List<String> certifications;

    /**
     * Languages speaker can present in.
     */
    private List<String> languages;

    /**
     * Record creation timestamp.
     */
    private Instant createdAt;

    /**
     * Record last update timestamp.
     */
    private Instant updatedAt;
}
