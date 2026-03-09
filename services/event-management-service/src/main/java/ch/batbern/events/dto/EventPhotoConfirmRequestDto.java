package ch.batbern.events.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Request DTO for confirming a completed photo upload (phase 3 of 3-phase upload).
 * Story 10.21: Event Photos Gallery
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventPhotoConfirmRequestDto {

    @NotNull
    private UUID photoId;

    @NotBlank
    private String s3Key;
}
