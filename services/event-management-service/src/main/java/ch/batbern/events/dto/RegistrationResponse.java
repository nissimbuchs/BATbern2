package ch.batbern.events.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for event registrations (enriched with User data)
 * <p>
 * Story 2.2a: Anonymous Event Registration (ADR-005)
 * <p>
 * Combines data from Registration entity and User entity:
 * - Registration fields: registrationCode, eventCode, status, registrationDate
 * - User fields: username, firstName, lastName, email, company (enriched via UserApiClient)
 * <p>
 * ADR-004: Factor User Fields from Domain Entities
 * - Registration entity stores only attendeeUsername (cross-service reference)
 * - User details (name, email, company) are enriched at API response time
 * - Prevents data duplication and ensures data consistency
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegistrationResponse {

    // From Registration entity
    private String registrationCode; // ADR-003: Public meaningful identifier
    private String eventCode;
    private String status; // pending, confirmed, cancelled, waitlist
    private String registrationDate; // ISO-8601 format
    private String createdAt; // ISO-8601 format
    private String updatedAt; // ISO-8601 format

    // From Event entity (enriched from Event - Story BAT-15)
    private String eventTitle;
    private String eventDate; // ISO-8601 format

    // From User entity (enriched via UserApiClient - ADR-004)
    private String attendeeUsername;
    private String attendeeFirstName;
    private String attendeeLastName;
    private String attendeeEmail;
    private String attendeeCompany; // Optional
}
