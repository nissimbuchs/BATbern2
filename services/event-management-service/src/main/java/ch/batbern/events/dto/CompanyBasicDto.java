package ch.batbern.events.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The part of a CUMS company EMS reads: {@code UserApiClient.getCompanyDisplayName} deserializes
 * the {@code CompanyResponse} of CUMS {@code GET /internal/companies/{name}} into it (Epic 17
 * entry 6; unknown properties are ignored).
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CompanyBasicDto {

    /** Company name (unique identifier) */
    private String name;

    private String displayName;
    private String website;
}
