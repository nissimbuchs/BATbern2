package ch.batbern.companyuser.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for company data
 * AC4: REST API response format
 * AC15: Support resource expansion (statistics, logo)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL) // Exclude null fields from JSON
@Schema(description = "Company response payload with all company details")
public class CompanyResponse {

    @Schema(description = "Unique company identifier (UUID)", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID id;

    @Schema(description = "Unique company name", example = "Swisscom AG")
    private String name;

    @Schema(description = "Display-friendly company name", example = "Swisscom")
    private String displayName;

    @Schema(description = "Swiss UID (Unternehmens-Identifikationsnummer)", example = "CHE-123.456.789")
    private String swissUID;

    @Schema(description = "Company website URL", example = "https://www.swisscom.ch")
    private String website;

    @Schema(description = "Industry sector", example = "Telecommunications")
    private String industry;

    @Schema(description = "Company description", example = "Leading telecommunications provider in Switzerland")
    private String description;

    @JsonProperty("isVerified")
    @Schema(description = "Whether the company is verified by an ORGANIZER", example = "true")
    private boolean isVerified;

    @Schema(description = "Timestamp when the company was created", example = "2024-01-15T10:30:00Z")
    private Instant createdAt;

    @Schema(description = "Timestamp when the company was last updated", example = "2024-01-15T10:30:00Z")
    private Instant updatedAt;

    @Schema(description = "User ID who created this company", example = "auth0|user_abc123")
    private String createdBy;

    // Resource expansion fields (AC15)

    @Schema(description = "Company statistics (only included when ?include=statistics)", example = "{\"totalEvents\": 5, \"totalSpeakers\": 12, \"totalPartners\": 3}")
    private CompanyStatistics statistics;

    @Schema(description = "Company logo information (only included when ?include=logo)", example = "{\"url\": \"https://cdn.batbern.ch/logos/...\", \"s3Key\": \"/logos/2024/...\"}")
    private CompanyLogo logo;
}
