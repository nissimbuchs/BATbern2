package ch.batbern.companyuser.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for company search results (autocomplete)
 * AC5: Search functionality response format
 * AC11: Advanced search results with verification status
 * ADR-003: No UUID exposure - uses name as meaningful identifier
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanySearchResponse {

    private String name;
    private String displayName;
    private String swissUID;
    private String industry;
    private boolean isVerified;
}
