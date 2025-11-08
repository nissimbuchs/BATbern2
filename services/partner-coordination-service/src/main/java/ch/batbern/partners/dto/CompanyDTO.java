package ch.batbern.partners.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for Company data from Company Service API.
 *
 * Used to validate company existence and enrich partner data.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyDTO {
    private String companyName;
    private String legalName;
    private String industry;
    private String size;
    private String city;
}
