package ch.batbern.events.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for confirming or declining speaker participation
 * Story 1.15a.1b: Session-User Many-to-Many Relationship
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpeakerConfirmationRequest {

    private String declineReason; // Required when declining, optional when confirming
}
