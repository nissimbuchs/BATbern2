package ch.batbern.events.dto.export;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Top-level envelope for a legacy BAT JSON export.
 * Story 10.20: Legacy BAT Format Data Export & Import (AC1)
 *
 * Mirrors the legacy BATspa data model for interoperability:
 *   { version, exportedAt, events[], companies[], speakers[], attendees[] }
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LegacyExportEnvelope {

    private String version;
    private Instant exportedAt;
    private List<LegacyEventDto> events;
    private List<LegacyCompanyDto> companies;
    private List<LegacySpeakerDto> speakers;
    private List<LegacyAttendeeDto> attendees;
}
