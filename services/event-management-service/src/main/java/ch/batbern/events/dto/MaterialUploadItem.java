package ch.batbern.events.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a single material upload with all metadata
 * Story 5.9: Session Materials Upload
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MaterialUploadItem {

    /**
     * Upload ID from MaterialsUploadService presigned URL generation
     */
    @NotBlank(message = "Upload ID is required")
    private String uploadId;

    /**
     * Material type: PRESENTATION, DOCUMENT, VIDEO, ARCHIVE, OTHER
     */
    @NotBlank(message = "Material type is required")
    private String materialType;

    /**
     * Original filename (e.g., "presentation.pdf")
     */
    @NotBlank(message = "File name is required")
    private String fileName;

    /**
     * File extension without dot (e.g., "pdf", "pptx")
     */
    @NotBlank(message = "File extension is required")
    private String fileExtension;

    /**
     * File size in bytes
     */
    @NotNull(message = "File size is required")
    @Positive(message = "File size must be positive")
    private Long fileSize;

    /**
     * MIME type (e.g., "application/pdf")
     */
    @NotBlank(message = "MIME type is required")
    private String mimeType;
}
