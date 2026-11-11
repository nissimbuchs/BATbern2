package ch.batbern.events.forwarding;

import java.util.UUID;

/**
 * The calendar reply contract EMS owns (ADR-016 decision 4, Epic 17 entry 8b):
 * {@code POST /internal/calendar-replies} in {@code docs/api/internal/ems-internal.openapi.yml}.
 * Every implementer configured under {@code batbern.contracts.calendar-replies.implementers}
 * records an attendee's answer to a calendar invite it sent (an iMIP REPLY, Story 10.27). EMS names
 * no implementing service.
 */
public interface CalendarReplyContract {

    /** The configuration name of the contract. */
    String NAME = "calendar-replies";

    /**
     * Hands the reply to every implementer. Never throws: inbound processing must not fail because
     * of this call. A refusal (unknown meeting 404, unknown PARTSTAT 400) is logged as a WARN, any
     * other failure as an ERROR and counted.
     */
    void replied(Reply reply);

    /**
     * @param meetingId     the invite's UID without {@code @batbern.ch}
     * @param attendeeEmail the ATTENDEE address of the reply
     * @param partStat      iCal PARTSTAT, upper-case (ACCEPTED, DECLINED, TENTATIVE are recorded)
     */
    record Reply(UUID meetingId, String attendeeEmail, String partStat) {
    }
}
