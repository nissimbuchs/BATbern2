package ch.batbern.companyuser.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for the get-or-create company endpoint.
 *
 * <p>Takes a human-readable display name and idempotently resolves it to an
 * existing company (matched by generated slug) or creates a new one. ADR-003:
 * the slug becomes {@code company.name}. See {@code CompanyService#getOrCreateCompany}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request payload for getting or creating a company by display name")
public class GetOrCreateCompanyRequest {

    @NotBlank(message = "Display name is required")
    @Size(min = 2, max = 255, message = "Display name must be between 2 and 255 characters")
    @Schema(
            description = "Full company display name (converted to a slug for company.name)",
            example = "Test Co",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    private String displayName;
}
