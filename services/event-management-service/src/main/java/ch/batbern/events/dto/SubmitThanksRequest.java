package ch.batbern.events.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for "Thank the Organizers" — Story 7.4.
 *
 * <p>The only field is an OPTIONAL short {@code note}. Identity (logged-in username) is taken
 * from the JWT server-side, never from the body, so there is nothing else to send. A one-click
 * thank-you sends an empty body.
 *
 * <p>{@code @JsonIgnoreProperties(ignoreUnknown = false)} rejects unexpected fields with 400
 * (mirrors {@link SelfNominateSpeakerRequest}) so the body cannot be used to spoof identity.
 */
@JsonIgnoreProperties(ignoreUnknown = false)
public class SubmitThanksRequest {

    @Size(max = 500, message = "note must be at most 500 characters")
    private String note;

    public SubmitThanksRequest() {
    }

    public SubmitThanksRequest(String note) {
        this.note = note;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
