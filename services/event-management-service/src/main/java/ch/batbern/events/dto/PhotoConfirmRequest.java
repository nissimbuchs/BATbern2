package ch.batbern.events.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for confirming speaker profile photo upload.
 * Story 6.2b: Speaker Profile Update Portal - AC7 (Profile Photo Upload)
 *
 * Used by POST /api/v1/speaker-portal/profile/photo/confirm
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PhotoConfirmRequest {

    /**
     * Magic link token for authentication
     */
    @NotBlank(message = "Token is required")
    private String token;

    /**
     * Upload ID from the presigned URL response.
     * Used to locate the uploaded file in S3.
     */
    @NotBlank(message = "Upload ID is required")
    private String uploadId;
}
