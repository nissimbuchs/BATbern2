package ch.batbern.events.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for speaker profile photo upload (presigned URL request).
 * Story 6.2b: Speaker Profile Update Portal - AC7 (Profile Photo Upload)
 *
 * Used by POST /api/v1/speaker-portal/profile/photo/presigned-url
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PhotoUploadRequest {

    /**
     * Magic link token for authentication
     */
    @NotBlank(message = "Token is required")
    private String token;

    /**
     * Original filename with extension (e.g., "profile.jpg")
     */
    @NotBlank(message = "File name is required")
    private String fileName;

    /**
     * File size in bytes (max 5MB = 5,242,880 bytes)
     */
    @Positive(message = "File size must be positive")
    private long fileSize;

    /**
     * MIME type of the file.
     * Allowed: image/jpeg, image/png, image/webp
     */
    @NotBlank(message = "Content type is required")
    private String contentType;
}
