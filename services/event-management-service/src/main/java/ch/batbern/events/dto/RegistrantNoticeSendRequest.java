package ch.batbern.events.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request to send a registrant-notice mail to an event's active registrants
 * (Story 7.3 hardening — Registrant Notices tab).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegistrantNoticeSendRequest {

    /** Template key — must be a {@code REGISTRANT_NOTICE}-category template (e.g. slides-online). */
    @NotBlank
    private String templateKey;
}
