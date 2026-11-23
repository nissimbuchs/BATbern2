package ch.batbern.events.event;

import java.util.UUID;

/**
 * Published by {@code SpeakerPoolService.selfNominate} when an attendee proposed a talk ("I could
 * speak on that"). The Mail Center confirms it to the person and opens a conversation for the
 * organizers once the proposal is committed. A plain in-service application event.
 *
 * @param proposalId  the {@code session_proposals} row
 * @param eventCode   the event the talk is proposed for
 * @param username    who proposed it
 * @param speakerName the name from the person's profile, or the username
 * @param title       the proposed title
 * @param abstractText the proposed abstract
 */
public record SpeakerProposalSubmittedEvent(UUID proposalId, String eventCode, String username,
        String speakerName, String title, String abstractText) {
}
