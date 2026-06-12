package ch.batbern.events.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for attendee speaker self-nomination — Story 7.2 "I Could Speak on That".
 *
 * <p>The attendee supplies only the proposed talk ({@code sessionTitle} + {@code abstract}).
 * Their name + company are auto-filled server-side from their user profile (via
 * {@code UserApiClient}) — Resolved Decision #1 — so identity is never re-typed and cannot
 * be spoofed through the request body.
 *
 * <p>{@code @JsonIgnoreProperties(ignoreUnknown = false)} mirrors {@link AddSpeakerToPoolRequest}:
 * an unexpected field (e.g. {@code speakerName}, {@code status}) is rejected with HTTP 400
 * rather than silently accepted — the entry state is fixed at IDENTIFIED and identity is
 * profile-derived.
 */
@JsonIgnoreProperties(ignoreUnknown = false)
public class SelfNominateSpeakerRequest {

    @NotBlank(message = "sessionTitle is required")
    // Max 200 to match session_content_history.title / session_proposals.proposed_title — the
    // pitch is carried verbatim into the canonical session at promote (ADR-012), so it must fit.
    @Size(min = 5, max = 200, message = "sessionTitle must be between 5 and 200 characters")
    private String sessionTitle;

    @NotBlank(message = "abstract is required")
    @Size(min = 10, max = 5000, message = "abstract must be between 10 and 5000 characters")
    private String abstractText;

    public SelfNominateSpeakerRequest() {
    }

    public SelfNominateSpeakerRequest(String sessionTitle, String abstractText) {
        this.sessionTitle = sessionTitle;
        this.abstractText = abstractText;
    }

    public String getSessionTitle() {
        return sessionTitle;
    }

    public void setSessionTitle(String sessionTitle) {
        this.sessionTitle = sessionTitle;
    }

    /**
     * The talk abstract. Mapped to/from the JSON field {@code abstract} — {@code abstract} is a
     * Java reserved word so the property is named {@code abstractText} and bound via
     * {@code @JsonProperty} on the accessors.
     */
    @com.fasterxml.jackson.annotation.JsonProperty("abstract")
    public String getAbstractText() {
        return abstractText;
    }

    @com.fasterxml.jackson.annotation.JsonProperty("abstract")
    public void setAbstractText(String abstractText) {
        this.abstractText = abstractText;
    }
}
