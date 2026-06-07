package ch.batbern.partners.dto;

import lombok.Builder;
import lombok.Value;

/**
 * RSVP count summary — Story 10.27 (AC7).
 */
@Value
@Builder
public class RsvpSummary {
    int accepted;
    int declined;
    int tentative;
}
