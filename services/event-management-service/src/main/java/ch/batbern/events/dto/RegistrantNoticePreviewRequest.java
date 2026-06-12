package ch.batbern.events.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request to preview a registrant-notice mail (Story 7.3 hardening — Registrant Notices tab).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegistrantNoticePreviewRequest {

    /** Template key — must be a {@code REGISTRANT_NOTICE}-category template (e.g. slides-online). */
    @NotBlank
    private String templateKey;

    /** Preview language (de/en). Defaults to German when null/unknown. */
    private String locale;
}
