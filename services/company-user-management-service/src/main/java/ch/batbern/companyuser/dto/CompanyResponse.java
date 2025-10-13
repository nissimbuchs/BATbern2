package ch.batbern.companyuser.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for company data
 * AC4: REST API response format
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyResponse {

    private UUID id;
    private String name;
    private String displayName;
    private String swissUID;
    private String website;
    private String industry;
    private String description;

    @JsonProperty("isVerified")
    private boolean isVerified;

    private Instant createdAt;
    private Instant updatedAt;
}
