package ch.batbern.companyuser.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for updating an existing company
 * AC4: REST API with request validation
 * All fields are optional for partial updates
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request payload for updating an existing company (all fields optional)")
public class UpdateCompanyRequest {

    @Size(min = 2, max = 255, message = "Company name must be between 2 and 255 characters")
    @Schema(description = "Unique company name (used as identifier)", example = "Swisscom AG")
    private String name;

    @Size(max = 255, message = "Display name must not exceed 255 characters")
    @Schema(description = "Display-friendly company name", example = "Swisscom")
    private String displayName;

    @Pattern(regexp = "^CHE-\\d{3}\\.\\d{3}\\.\\d{3}$", message = "Invalid Swiss UID format. Expected: CHE-XXX.XXX.XXX")
    @Schema(description = "Swiss UID (Unternehmens-Identifikationsnummer)", example = "CHE-123.456.789", pattern = "CHE-XXX.XXX.XXX")
    private String swissUID;

    @Size(max = 500, message = "Website URL must not exceed 500 characters")
    @Schema(description = "Company website URL", example = "https://www.swisscom.ch")
    private String website;

    @Size(max = 100, message = "Industry must not exceed 100 characters")
    @Schema(description = "Industry sector", example = "Telecommunications")
    private String industry;

    @Size(max = 2000, message = "Description must not exceed 2000 characters")
    @Schema(description = "Company description", example = "Leading telecommunications provider in Switzerland")
    private String description;
}
