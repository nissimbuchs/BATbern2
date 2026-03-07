package ch.batbern.events.dto.export;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Legacy attendee DTO for the attendees[] list in the export envelope.
 * Story 10.20: AC1
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LegacyAttendeeDto {

    private String eventCode;
    private String username;
    private String status;
    private Instant registeredAt;
}
