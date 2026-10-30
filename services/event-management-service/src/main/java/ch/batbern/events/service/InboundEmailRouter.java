package ch.batbern.events.service;

import ch.batbern.events.client.PartnerMeetingRsvpClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Routes a parsed inbound email to the appropriate action (Story 10.17 — AC3, AC4, AC5, AC6).
 *
 * <ul>
 *   <li>Rate limit check: {@link InboundEmailRateLimiter}</li>
 *   <li>Unsubscribe keywords → {@link NewsletterSubscriberService#unsubscribeByEmail}</li>
 *   <li>Cancel keywords + event code → deregistration-link mail via
 *       {@link DeregistrationService#requestDeregistrationLinkByReply}: the attendee confirms with one
 *       click; nothing is cancelled here (Story 16.0 AC11, #1162)</li>
 *   <li>Accept keywords + event code → {@link RegistrationService#confirmByEmail}</li>
 *   <li>Keywords act only when the first line is essentially the keyword alone, at most
 *       {@value #MAX_KEYWORD_WORDS} words (Story 16.0 AC12/AC13). A longer sentence that merely
 *       contains one is a human reply.</li>
 *   <li>Anything it cannot act on → {@link RouteResult#UNRECOGNIZED}: the caller forwards the
 *       mail to the organizers (Story 16.0, #1160). It used to be discarded.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InboundEmailRouter {

    private static final Pattern EVENT_CODE_PATTERN = Pattern.compile("BATbern\\d+");

    /** Story 16.0 AC12: "cancel", "Bitte absagen", "cancel please" act; a sentence does not. */
    static final int MAX_KEYWORD_WORDS = 3;

    private final NewsletterSubscriberService newsletterSubscriberService;
    private final RegistrationService registrationService;
    private final InboundEmailConfirmationEmailService confirmationEmailService;
    private final InboundEmailRateLimiter rateLimiter;
    private final PartnerMeetingRsvpClient partnerMeetingRsvpClient;
    private final DeregistrationService deregistrationService;

    /**
     * Immutable DTO representing a parsed inbound email.
     *
     * @param senderEmail    the From: address
     * @param subject        the Subject: header
     * @param bodyFirstLine  first non-quoted plain-text line from the body
     */
    public record ParsedEmail(String senderEmail, String subject, String bodyFirstLine) {}

    /** What {@link #route} did with a mail. */
    public enum RouteResult {
        /** A keyword action ran. */
        HANDLED,
        /** The sender exceeded the rate limit; nothing ran and nothing is forwarded. */
        RATE_LIMITED,
        /** No action could run; a person has to read it (Story 16.0 AC3). */
        UNRECOGNIZED
    }

    /**
     * Immutable DTO representing a parsed iCal REPLY — Story 10.27 (AC3, AC4).
     *
     * @param meetingUid     raw UID value (e.g. {@code 11111111-...@batbern.ch})
     * @param attendeeEmail  email extracted from ATTENDEE mailto: value
     * @param partStat       PARTSTAT value: ACCEPTED, DECLINED, or TENTATIVE
     */
    public record IcsReply(String meetingUid, String attendeeEmail, String partStat) {}

    /**
     * Route the parsed email to the appropriate action.
     *
     * @param email parsed inbound email
     * @return what was done; {@link RouteResult#UNRECOGNIZED} means the caller must forward it
     */
    public RouteResult route(ParsedEmail email) {
        // 1. Rate limit check
        if (!rateLimiter.isAllowed(email.senderEmail())) {
            return RouteResult.RATE_LIMITED; // warn already logged in rateLimiter
        }

        // 2. Normalize body (lowercase, strip special chars except accented letters incl. German umlauts)
        String body = email.bodyFirstLine()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9äöüéàèêëîïôùûü\\s]", " ")
                .strip();

        String senderPrefix = email.senderEmail().substring(0, Math.min(5, email.senderEmail().length()));

        // Story 16.0 AC12/AC13: a keyword inside a longer sentence ("Ich muss leider absagen, kann
        // ich den Platz weitergeben?") is a question for a person, not a command.
        if (body.isEmpty() || body.split("\\s+").length > MAX_KEYWORD_WORDS) {
            log.info("Inbound email body from: {}*** is not a short keyword — forwarding to organizers",
                    senderPrefix);
            return RouteResult.UNRECOGNIZED;
        }

        // 3. Unsubscribe variants: unsubscribe, abmelden, désinscription
        if (body.contains("unsubscribe") || body.contains("abmelden") || body.contains("désinscription")) {
            String locale = body.contains("abmelden") ? "de"
                    : body.contains("désinscription") ? "fr" : "en";
            newsletterSubscriberService.unsubscribeByEmail(email.senderEmail());
            confirmationEmailService.sendUnsubscribeConfirmation(email.senderEmail(), locale);
            log.info("Processed unsubscribe reply from: {}***", senderPrefix);
            return RouteResult.HANDLED;
        }

        // 4. Deregistration variants: cancel, deregister, absagen
        //    Note: "abmelden" matches both unsubscribe and deregistration — unsubscribe takes priority
        if (body.contains("cancel") || body.contains("deregister") || body.contains("absagen")) {
            String eventCode = extractEventCode(email.subject());
            if (eventCode != null) {
                // Story 16.0 AC11 (#1162): ask before acting. The deregistration-link mail lets the
                // attendee confirm; a misread reply can no longer cost anyone their seat.
                DeregistrationService.DeregistrationLinkResult result =
                        deregistrationService.requestDeregistrationLinkByReply(email.senderEmail(), eventCode);
                log.info("Processed deregistration reply from: {}*** for event: {} → {}", senderPrefix, eventCode,
                        result);
                return RouteResult.HANDLED;
            }
            log.warn("Deregistration keyword but no event code in subject from: {}*** — forwarding", senderPrefix);
            return RouteResult.UNRECOGNIZED;
        }

        // 5. Acceptance variants: accept, bestätigen, confirmer, bevestigen
        if (body.contains("accept") || body.contains("bestätigen")
                || body.contains("confirmer") || body.contains("bevestigen")) {
            String eventCode = extractEventCode(email.subject());
            if (eventCode != null) {
                registrationService.confirmByEmail(email.senderEmail(), eventCode);
                log.info("Processed acceptance reply from: {}*** for event: {}", senderPrefix, eventCode);
                return RouteResult.HANDLED;
            }
            log.warn("Acceptance keyword but no event code in subject from: {}*** — forwarding", senderPrefix);
            return RouteResult.UNRECOGNIZED;
        }

        // 6. Unrecognized — a person has to read it; the caller forwards it to the organizers
        log.info("Unrecognized inbound email body from: {}*** — forwarding to organizers", senderPrefix);
        return RouteResult.UNRECOGNIZED;
    }

    /**
     * Route an iCal REPLY to the partner meeting RSVP service — Story 10.27 (AC4).
     *
     * <ol>
     *   <li>Rate limit check on attendee email</li>
     *   <li>Validate UID ends with {@code @batbern.ch}</li>
     *   <li>Parse UUID from UID prefix</li>
     *   <li>Call {@link PartnerMeetingRsvpClient#recordRsvp}</li>
     * </ol>
     *
     * @param reply parsed iCal REPLY data
     */
    public void routeIcsReply(IcsReply reply) {
        // 1. Rate limit check
        if (!rateLimiter.isAllowed(reply.attendeeEmail())) {
            return;
        }

        // 2. Validate UID domain
        String uid = reply.meetingUid();
        if (uid == null || !uid.endsWith("@batbern.ch")) {
            log.warn("ICS REPLY UID does not match @batbern.ch pattern: {} — discarding", uid);
            return;
        }

        // 3. Parse UUID from UID prefix (before "@batbern.ch")
        String uuidPart = uid.substring(0, uid.length() - "@batbern.ch".length());
        UUID meetingId;
        try {
            meetingId = UUID.fromString(uuidPart);
        } catch (IllegalArgumentException e) {
            log.warn("ICS REPLY UID prefix is not a valid UUID: {} — discarding", uuidPart);
            return;
        }

        // 4. Record RSVP
        partnerMeetingRsvpClient.recordRsvp(meetingId, reply.attendeeEmail(), reply.partStat());
        log.info("Recorded iCal RSVP: meetingId={}, emailPrefix={}, status={}",
                meetingId, reply.attendeeEmail().substring(0, Math.min(5, reply.attendeeEmail().length())),
                reply.partStat());
    }

    /**
     * Extracts the first BATbernXX event code from the email subject line.
     *
     * @param subject email subject
     * @return matched event code (e.g. "BATbern42") or null if not found
     */
    private String extractEventCode(String subject) {
        if (subject == null) {
            return null;
        }
        Matcher matcher = EVENT_CODE_PATTERN.matcher(subject);
        return matcher.find() ? matcher.group() : null;
    }
}
