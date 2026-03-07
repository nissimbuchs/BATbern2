package ch.batbern.events.dto.export;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Legacy company DTO matching the BATspa companies.json schema.
 * Story 10.20: AC1
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LegacyCompanyDto {

    /** Company name / unique identifier */
    private String id;

    private String displayName;

    /** Logo filename or URL */
    private String logo;

    /** Company website URL */
    private String url;
}
