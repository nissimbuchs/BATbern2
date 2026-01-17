package ch.batbern.events.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request DTO for associating uploaded materials with a session
 * Story 5.9: Session Materials Upload
 *
 * Used in POST /api/v1/events/{eventCode}/sessions/{sessionSlug}/materials
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionMaterialAssociationRequest {

    /**
     * List of material uploads with complete metadata
     * Each item contains uploadId, materialType, fileName, fileExtension, fileSize, mimeType
     */
    @NotNull(message = "Materials are required")
    @Size(min = 1, max = 10, message = "Must provide 1-10 materials")
    @Valid
    private List<MaterialUploadItem> materials;
}
