package ch.batbern.events.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /api/v1/events/{eventCode}/speakers/{speakerId}/promote}
 * (Story 11.D.1; tightened by Story 11.E.4 AC4).
 *
 * <p>Drives the {@code CONTACTED → READY} workflow transition. The email becomes the
 * canonical {@code speaker_pool.email} and the lookup key for
 * {@link ch.batbern.events.client.UserApiClient#provisionUserWithRole}.
 *
 * <p>{@code firstName} and {@code lastName} are <strong>required</strong> (tightened from
 * optional by Story 11.E.4 AC4, PM decision 2026-05-18). They populate the Cognito user's
 * {@code given_name} / {@code family_name} attributes on the {@code CONTACTED → READY}
 * transition; the previous fallback to splitting {@code speaker_pool.speakerName} (with
 * literal placeholders "Speaker" / "Unknown" when the name was blank) is removed. Callers
 * MUST collect these fields from the organizer before invoking the promote endpoint.
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

        @NotBlank
        @Size(max = 100)
        String firstName,

        @NotBlank
        @Size(max = 100)
        String lastName
) {
}
