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
 *   <li>Cancel keywords + event code → {@link RegistrationService#cancelByEmail}</li>
 *   <li>Accept keywords + event code → {@link RegistrationService#confirmByEmail}</li>
 *   <li>Unknown body → silent discard (WARN log)</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InboundEmailRouter {

    private static final Pattern EVENT_CODE_PATTERN = Pattern.compile("BATbern\\d+");

    private final NewsletterSubscriberService newsletterSubscriberService;
    private final RegistrationService registrationService;
    private final InboundEmailConfirmationEmailService confirmationEmailService;
    private final InboundEmailRateLimiter rateLimiter;
    private final PartnerMeetingRsvpClient partnerMeetingRsvpClient;

    /**
     * Immutable DTO representing a parsed inbound email.
     *
     * @param senderEmail    the From: address
     * @param subject        the Subject: header
     * @param bodyFirstLine  first non-quoted plain-text line from the body
     */
    public record ParsedEmail(String senderEmail, String subject, String bodyFirstLine) {}

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
     */
    public void route(ParsedEmail email) {
        // 1. Rate limit check
        if (!rateLimiter.isAllowed(email.senderEmail())) {
            return; // warn already logged in rateLimiter
        }

        // 2. Normalize body (lowercase, strip special chars except accented letters incl. German umlauts)
        String body = email.bodyFirstLine()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9äöüéàèêëîïôùûü\\s]", " ")
                .strip();

        String senderPrefix = email.senderEmail().substring(0, Math.min(5, email.senderEmail().length()));

        // 3. Unsubscribe variants: unsubscribe, abmelden, désinscription
        if (body.contains("unsubscribe") || body.contains("abmelden") || body.contains("désinscription")) {
            String locale = body.contains("abmelden") ? "de"
                    : body.contains("désinscription") ? "fr" : "en";
            newsletterSubscriberService.unsubscribeByEmail(email.senderEmail());
            confirmationEmailService.sendUnsubscribeConfirmation(email.senderEmail(), locale);
            log.info("Processed unsubscribe reply from: {}***", senderPrefix);
            return;
        }

        // 4. Deregistration variants: cancel, deregister, absagen
        //    Note: "abmelden" matches both unsubscribe and deregistration — unsubscribe takes priority
        if (body.contains("cancel") || body.contains("deregister") || body.contains("absagen")) {
            String locale = body.contains("absagen") ? "de" : "en";
            String eventCode = extractEventCode(email.subject());
            if (eventCode != null) {
                registrationService.cancelByEmail(email.senderEmail(), eventCode);
                confirmationEmailService.sendCancelConfirmation(email.senderEmail(), eventCode, locale);
                log.info("Processed deregistration reply from: {}*** for event: {}", senderPrefix, eventCode);
            } else {
                log.warn("Deregistration keyword but no event code in subject from: {}*** subject: {}",
                        senderPrefix, email.subject());
            }
            return;
        }

        // 5. Acceptance variants: accept, bestätigen, confirmer, bevestigen
        if (body.contains("accept") || body.contains("bestätigen")
                || body.contains("confirmer") || body.contains("bevestigen")) {
            String eventCode = extractEventCode(email.subject());
            if (eventCode != null) {
                registrationService.confirmByEmail(email.senderEmail(), eventCode);
                log.info("Processed acceptance reply from: {}*** for event: {}", senderPrefix, eventCode);
            } else {
                log.warn("Acceptance keyword but no event code in subject from: {}*** subject: {}",
                        senderPrefix, email.subject());
            }
            return;
        }

        // 6. Unrecognized — silent discard
        log.warn("Unrecognized inbound email body from: {}*** — discarding", senderPrefix);
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
