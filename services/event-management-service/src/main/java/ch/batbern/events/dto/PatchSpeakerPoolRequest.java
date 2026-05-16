package ch.batbern.events.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Partial update request for speaker pool entries.
 *
 * <p>All fields are optional — only non-null fields are applied.
 *
 * <p>Story 11.D.1 (AR23): {@code @JsonIgnoreProperties(ignoreUnknown = false)} +
 * {@code email} removed. Per FR12 + ADR-009 §0.2, the only path that attaches/updates a
 * speaker's email is the new {@code POST /speakers/{speakerId}/promote} endpoint, which
 * also drives the {@code CONTACTED → READY} transition and provisions a User row.
 * Sending {@code email} (or any other unknown field) here returns HTTP 400.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = false)
public class PatchSpeakerPoolRequest {
    private String assignedOrganizerId;
    private String notes;
}
