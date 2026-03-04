package ch.batbern.events.dto.export;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Legacy session DTO matching the BATspa sessions.json schema.
 * Story 10.20: AC1
 *
 * Note: Speaker list uses the legacy field name "referenten".
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LegacySessionDto {

    private String sessionSlug;
    private String title;
    private String description;
    private String sessionType;

    /** Legacy PDF filename for session materials */
    private String pdf;

    /** Session speakers — legacy field name preserved for interoperability */
    private List<LegacySpeakerDto> referenten;
}
