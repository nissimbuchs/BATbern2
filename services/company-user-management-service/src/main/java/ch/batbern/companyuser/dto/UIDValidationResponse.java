package ch.batbern.companyuser.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for Swiss UID validation
 * AC12: Swiss UID validation endpoint
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Swiss UID validation result")
public class UIDValidationResponse {

    @Schema(description = "Whether the UID format is valid", example = "true")
    private boolean valid;

    @Schema(description = "The UID that was validated", example = "CHE-123.456.789")
    private String uid;

    @Schema(description = "Validation message", example = "Valid Swiss UID format")
    private String message;
}
