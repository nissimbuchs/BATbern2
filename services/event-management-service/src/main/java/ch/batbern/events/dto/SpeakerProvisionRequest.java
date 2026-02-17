package ch.batbern.events.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Story 9.2: Request DTO for provisioning a speaker Cognito account.
 * Sent by SpeakerAccountCreationService to company-user-management-service.
 */
@Data
@Builder
public class SpeakerProvisionRequest {
    private String email;
    private String firstName;
    private String lastName;
}
