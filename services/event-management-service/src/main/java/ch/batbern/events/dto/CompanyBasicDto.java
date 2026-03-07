package ch.batbern.events.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Minimal company representation for cross-service calls.
 * Story 10.20: Used by UserApiClient.getAllCompanies() to deserialize
 * the company-user-management-service GET /api/v1/companies response.
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
