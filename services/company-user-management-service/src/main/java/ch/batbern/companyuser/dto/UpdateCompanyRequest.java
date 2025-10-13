package ch.batbern.companyuser.dto;

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
public class UpdateCompanyRequest {

    @Size(min = 2, max = 255, message = "Company name must be between 2 and 255 characters")
    private String name;

    @Size(max = 255, message = "Display name must not exceed 255 characters")
    private String displayName;

    @Pattern(regexp = "^CHE-\\d{3}\\.\\d{3}\\.\\d{3}$", message = "Invalid Swiss UID format. Expected: CHE-XXX.XXX.XXX")
    private String swissUID;

    @Size(max = 500, message = "Website URL must not exceed 500 characters")
    private String website;

    @Size(max = 100, message = "Industry must not exceed 100 characters")
    private String industry;

    @Size(max = 2000, message = "Description must not exceed 2000 characters")
    private String description;
}
