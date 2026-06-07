package ch.batbern.partners.dto;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;

/**
 * Single RSVP entry in the list response — Story 10.27 (AC7).
 */
@Value
@Builder
public class RsvpDTO {
    String attendeeEmail;
    String status;
    Instant respondedAt;
}
