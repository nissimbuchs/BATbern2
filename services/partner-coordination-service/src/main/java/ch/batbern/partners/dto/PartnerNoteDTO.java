package ch.batbern.partners.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO for partner note responses — Story 8.4.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PartnerNoteDTO {

    private UUID id;
    private String title;
    private String content;
    private String authorUsername;
    private Instant createdAt;
    private Instant updatedAt;
}
