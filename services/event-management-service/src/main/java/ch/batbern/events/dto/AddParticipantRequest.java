package ch.batbern.events.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Organizer "Add participant" request (POST /events/{eventCode}/participants).
 *
 * <p>Adds an EXISTING BATbern user (picked via the user autocomplete) onto an event directly
 * as a {@code confirmed} participant — no self-registration / email-confirmation step.
 */
@Data
public class AddParticipantRequest {

    /** The chosen existing user's username (= {@code UserResponse.id}). */
    @NotBlank
    private String username;

    /** When the event is full, {@code true} adds over capacity; {@code false} → 409. Default false. */
    private boolean force = false;

    /** Send the attendee a "you have a confirmed spot" email. Default true. */
    private boolean notify = true;
}
