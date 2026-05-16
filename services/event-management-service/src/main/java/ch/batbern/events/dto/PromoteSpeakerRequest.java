package ch.batbern.events.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /api/v1/events/{eventCode}/speakers/{speakerId}/promote}
 * (Story 11.D.1).
 *
 * <p>Drives the {@code CONTACTED → READY} workflow transition. The email becomes the
 * canonical {@code speaker_pool.email} and the lookup key for
 * {@link ch.batbern.events.client.UserApiClient#provisionUserWithRole}. {@code firstName}
 * and {@code lastName} are optional — if absent, the workflow service falls back to
 * splitting {@code speaker_pool.speakerName}.
 *
 * <p>{@code @JsonIgnoreProperties(ignoreUnknown = false)}: stale fields from pre-refactor
 * frontends are rejected with HTTP 400 (mapped by
 * {@code GlobalExceptionHandler.handleHttpMessageNotReadableException}) rather than
 * silently ignored. Matches the {@code additionalProperties: false} setting on the OpenAPI
 * schema.
 */
@JsonIgnoreProperties(ignoreUnknown = false)
public record PromoteSpeakerRequest(
        @NotBlank
        @Email
        @Size(max = 320)
        String email,

        @Size(max = 100)
        String firstName,

        @Size(max = 100)
        String lastName
) {
}
