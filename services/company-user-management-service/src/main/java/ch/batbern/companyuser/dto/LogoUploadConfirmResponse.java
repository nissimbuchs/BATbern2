package ch.batbern.companyuser.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for logo upload confirmation
 * Returns the CloudFront URL of the uploaded logo
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Logo upload confirmation response with CDN URL")
public class LogoUploadConfirmResponse {

    @Schema(
            description = "CloudFront CDN URL for the uploaded logo",
            example = "https://cdn.batbern.ch/logos/2024/company-789/logo-f3e8d1a4.png",
            required = true
    )
    private String logoUrl;
}
