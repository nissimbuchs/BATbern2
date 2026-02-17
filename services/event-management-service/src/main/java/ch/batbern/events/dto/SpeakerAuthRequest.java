package ch.batbern.events.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Story 9.3 Task 3.4: DTO for speaker authentication request sent to company-user-management-service.
 * Mirrors ch.batbern.companyuser.dto.SpeakerAuthRequest.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SpeakerAuthRequest {
    private String email;
    private String password;
}
