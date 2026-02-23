package ch.batbern.partners.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for partially updating a partner note — Story 8.4 (AC4).
 * All fields optional — only provided non-null fields are applied.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateNoteRequest {

    @Size(max = 500, message = "title must not exceed 500 characters")
    private String title;

    private String content;
}
