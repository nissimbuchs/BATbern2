package ch.batbern.events.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Request DTO for associating uploaded materials with a session
 * Story 5.9: Session Materials Upload
 *
 * Used in POST /sessions/{sessionSlug}/materials
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionMaterialAssociationRequest {

    /**
     * Upload IDs from GenericLogoService (ADR-002)
     * Parallel array with materialTypes
     */
    @NotNull(message = "Upload IDs are required")
    @Size(min = 1, max = 10, message = "Must provide 1-10 upload IDs")
    private List<String> uploadIds;

    /**
     * Material types for each upload
     * Valid values: PRESENTATION, DOCUMENT, VIDEO, OTHER
     * Parallel array with uploadIds
     */
    @NotNull(message = "Material types are required")
    @Size(min = 1, max = 10, message = "Must provide 1-10 material types")
    private List<String> materialTypes;
}
