package ch.batbern.events.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Partial update request for speaker pool entries.
 * All fields are optional — only non-null fields are applied.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PatchSpeakerPoolRequest {
    private String assignedOrganizerId;
    private String notes;
    private String email;
}
