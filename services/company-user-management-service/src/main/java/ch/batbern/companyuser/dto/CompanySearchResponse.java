package ch.batbern.companyuser.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Response DTO for company search results (autocomplete)
 * AC5: Search functionality response format
 * AC11: Advanced search results with verification status
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanySearchResponse {

    private UUID id;
    private String name;
    private String displayName;
    private String swissUID;
    private String industry;
    private boolean isVerified;
}
