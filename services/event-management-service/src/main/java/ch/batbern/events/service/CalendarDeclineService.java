package ch.batbern.events.service;

import ch.batbern.events.domain.Registration;
import ch.batbern.events.forwarding.CalendarReply;
import ch.batbern.events.repository.RegistrationRepository;
import ch.batbern.shared.utils.LoggingUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Story 16.0 Part B (#1161): a registrant who clicks "Decline" on the registration calendar invite
 * gets the existing deregistration-link mail. Nothing is cancelled here; the seat (and the waitlist
 * promotion behind it) is released only when they click the link. A second decline within 7 days
 * sends no second mail ({@link DeregistrationService#sendDeregistrationLinkOnce}).
 *
 * <p>Matching, in order (AC7):
 * <ol>
 *   <li>the invite's UID {@code reg-{calendarUid}@batbern.ch} ({@link CalendarInviteUid});</li>
 *   <li>for invites sent before that UID existed: the sender's active registration for an event
 *       dated within ±1 day of the reply's DTSTART, only if there is exactly one.</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CalendarDeclineService {

    static final Duration FALLBACK_WINDOW = Duration.ofDays(1);

    /** What a decline led to; counted in the forwarding metrics, never told to the sender. */
    public enum Outcome {
        LINK_SENT,
        /** A link went out within the last 7 days; nothing sent now. */
        ALREADY_SENT,
        /** Matched, but the registration is already cancelled. */
        NOT_ACTIVE,
        /** No registration, or more than one candidate. */
        UNMATCHED
    }

    private final RegistrationRepository registrationRepository;
    private final DeregistrationService deregistrationService;

    @Transactional
    public Outcome handleDecline(String senderEmail, CalendarReply reply) {
        Optional<Registration> match = CalendarInviteUid.parse(reply.uid())
                .flatMap(registrationRepository::findByCalendarUid)
                .or(() -> matchBySenderAndDate(senderEmail, reply));
        if (match.isEmpty()) {
            log.info("Calendar decline from {} matches no single registration", LoggingUtils.maskEmail(senderEmail));
            return Outcome.UNMATCHED;
        }
        return switch (deregistrationService.sendDeregistrationLinkOnce(match.get())) {
            case SENT -> Outcome.LINK_SENT;
            case ALREADY_SENT -> Outcome.ALREADY_SENT;
            case NOT_FOUND -> Outcome.NOT_ACTIVE;
        };
    }

    private Optional<Registration> matchBySenderAndDate(String senderEmail, CalendarReply reply) {
        if (senderEmail == null || reply.start() == null) {
            return Optional.empty();
        }
        List<Registration> candidates = registrationRepository.findActiveByEmailAndEventDateBetween(senderEmail,
                reply.start().minus(FALLBACK_WINDOW), reply.start().plus(FALLBACK_WINDOW));
        return candidates.size() == 1 ? Optional.of(candidates.get(0)) : Optional.empty();
    }
}
