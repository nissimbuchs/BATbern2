package ch.batbern.events.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for event photo upload URL generation (phase 1 of 3-phase upload).
 * Story 10.21: Event Photos Gallery
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventPhotoUploadRequestDto {

    @NotBlank
    private String filename;

    @NotBlank
    private String contentType;

    @Positive
    private long fileSize;
}
