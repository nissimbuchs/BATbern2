package ch.batbern.events.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Response DTO containing presigned upload URL and photo metadata (phase 1 of 3-phase upload).
 * Story 10.21: Event Photos Gallery
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventPhotoUploadResponseDto {

    private UUID photoId;
    private String uploadUrl;
    private String s3Key;
    private int expiresIn;
}
