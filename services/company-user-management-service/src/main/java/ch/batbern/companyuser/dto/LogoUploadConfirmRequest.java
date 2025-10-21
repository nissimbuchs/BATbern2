package ch.batbern.companyuser.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for confirming logo upload completion
 * AC5: Confirm upload and store logo URL in company entity
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogoUploadConfirmRequest {

    /**
     * File ID returned from presigned URL request
     */
    @NotBlank(message = "File ID is required")
    private String fileId;

    /**
     * File extension (e.g., "png", "jpg", "svg")
     * Required for reconstructing S3 key
     */
    @NotBlank(message = "File extension is required")
    private String fileExtension;

    /**
     * Optional SHA-256 checksum for integrity verification
     */
    private String checksum;
}
