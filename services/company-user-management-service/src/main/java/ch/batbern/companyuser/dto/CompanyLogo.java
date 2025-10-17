package ch.batbern.companyuser.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Logo DTO for company resource expansion
 * AC15: Support ?include=logo parameter
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Company logo information including CDN URL and S3 storage keys")
public class CompanyLogo {

    @Schema(description = "CloudFront CDN URL for the logo", example = "https://cdn.batbern.ch/logos/2024/company-789/logo-f3e8d1a4.png")
    private String url;

    @Schema(description = "S3 storage key for the logo", example = "/logos/2024/company-789/logo-f3e8d1a4.png")
    private String s3Key;

    @Schema(description = "File identifier for the logo", example = "f3e8d1a4-5678-9abc-def0-123456789abc")
    private String fileId;
}
