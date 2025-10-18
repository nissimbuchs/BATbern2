package ch.batbern.companyuser.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for requesting a presigned URL for logo upload
 * AC5: Logo upload with file validation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogoUploadRequest {

    /**
     * Original filename with extension (e.g., "company-logo.png")
     */
    @NotBlank(message = "Filename is required")
    private String fileName;

    /**
     * File size in bytes (max 5 MB = 5,242,880 bytes)
     */
    @NotNull(message = "File size is required")
    @Positive(message = "File size must be positive")
    private Long fileSize;

    /**
     * MIME type (e.g., "image/png", "image/jpeg", "image/svg+xml")
     */
    @NotBlank(message = "MIME type is required")
    private String mimeType;
}
