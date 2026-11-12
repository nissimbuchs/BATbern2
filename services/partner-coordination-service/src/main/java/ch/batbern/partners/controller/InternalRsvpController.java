package ch.batbern.partners.controller;

import ch.batbern.partners.domain.RsvpStatus;
import ch.batbern.partners.dto.RecordRsvpRequest;
import ch.batbern.partners.service.PartnerMeetingRsvpService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

/**
 * partner-coordination implements the calendar reply contract event-management owns
 * ({@code POST /internal/calendar-replies} in {@code docs/api/internal/ems-internal.openapi.yml},
 * ADR-016 decision 5, Epic 17 entry 8b): an attendee's iMIP REPLY to a partner meeting invite
 * (Story 10.27) is recorded at once as that attendee's RSVP. It replaced
 * {@code POST /internal/partner-meetings/rsvps}.
 *
 * <p>Called from the inbound mail listener, which has no user: no JWT, and none needed.
 * {@code /internal/**} is not routed by the API gateway, which also denies it. An unknown meeting
 * answers 404 (the service's {@code PartnerNotFoundException}), a PARTSTAT other than ACCEPTED,
 * DECLINED or TENTATIVE answers 400; both carry the shared {@code ErrorResponse}.
 */
@RestController
@RequiredArgsConstructor
public class InternalRsvpController {

    private final PartnerMeetingRsvpService rsvpService;

    @PostMapping("/internal/calendar-replies")
    public ResponseEntity<Void> recordCalendarReply(@Valid @RequestBody RecordRsvpRequest request) {
        rsvpService.upsertRsvp(request.getMeetingId(), request.getAttendeeEmail(), status(request.getPartStat()));
        return ResponseEntity.noContent().build();
    }

    /** ACCEPTED, DECLINED or TENTATIVE in any case; anything else is a 400 (global handler). */
    private static RsvpStatus status(String partStat) {
        try {
            return RsvpStatus.valueOf(partStat.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown partStat: " + partStat, e);
        }
    }
}
