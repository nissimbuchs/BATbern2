package ch.batbern.events.dto.export;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Legacy event DTO matching the BATspa data model.
 * Story 10.20: AC1
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LegacyEventDto {

    /** Legacy BAT event number (e.g. 57 for BATbern57) */
    private Integer bat;

    private String eventCode;
    private String title;
    private Instant date;
    private String venueName;
    private String venueAddress;
    private List<LegacySessionDto> sessions;
}
