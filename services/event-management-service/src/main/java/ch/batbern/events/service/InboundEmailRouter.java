package ch.batbern.events.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Routes a parsed inbound email to the appropriate action (Story 10.17 — AC3, AC4, AC5, AC6).
 *
 * <ul>
 *   <li>Rate limit check: {@link InboundEmailRateLimiter}</li>
 *   <li>Unsubscribe keywords → {@link NewsletterSubscriberService#unsubscribeByEmail}</li>
 *   <li>Cancel keywords + event code → {@link DeregistrationService#deregisterByEmail}</li>
 *   <li>Accept keywords + event code → {@link InboundEmailConfirmationEmailService#sendAcceptConfirmation}</li>
 *   <li>Unknown body → silent discard (WARN log)</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InboundEmailRouter {

    private static final Pattern EVENT_CODE_PATTERN = Pattern.compile("BATbern\\d+");

    private final NewsletterSubscriberService newsletterSubscriberService;
    private final DeregistrationService deregistrationService;
    private final InboundEmailConfirmationEmailService confirmationEmailService;
    private final InboundEmailRateLimiter rateLimiter;

    /**
     * Immutable DTO representing a parsed inbound email.
     *
     * @param senderEmail    the From: address
     * @param subject        the Subject: header
     * @param bodyFirstLine  first non-quoted plain-text line from the body
     */
    public record ParsedEmail(String senderEmail, String subject, String bodyFirstLine) {}

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

        // 2. Normalize body (lowercase, strip special chars except accented letters)
        String body = email.bodyFirstLine()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9éàèêëîïôùûüéàèêëîïôùûü\\s]", " ")
                .strip();

        String senderPrefix = email.senderEmail().substring(0, Math.min(5, email.senderEmail().length()));

        // 3. Unsubscribe variants: unsubscribe, abmelden, désinscription
        if (body.contains("unsubscribe") || body.contains("abmelden") || body.contains("d sinscription")
                || body.contains("désinscription")) {
            newsletterSubscriberService.unsubscribeByEmail(email.senderEmail());
            confirmationEmailService.sendUnsubscribeConfirmation(email.senderEmail());
            log.info("Processed unsubscribe reply from: {}***", senderPrefix);
            return;
        }

        // 4. Deregistration variants: cancel, deregister, absagen
        //    Note: "abmelden" matches both unsubscribe and deregistration — unsubscribe takes priority
        if (body.contains("cancel") || body.contains("deregister") || body.contains("absagen")) {
            String eventCode = extractEventCode(email.subject());
            if (eventCode != null) {
                deregistrationService.deregisterByEmail(email.senderEmail(), eventCode);
                confirmationEmailService.sendCancelConfirmation(email.senderEmail(), eventCode);
                log.info("Processed deregistration reply from: {}*** for event: {}", senderPrefix, eventCode);
            } else {
                log.warn("Deregistration keyword but no event code in subject from: {}*** subject: {}",
                        senderPrefix, email.subject());
            }
            return;
        }

        // 5. Acceptance variants: accept, bestätigen, confirmer, bevestigen
        if (body.contains("accept") || body.contains("best tigen") || body.contains("bestätigen")
                || body.contains("confirmer") || body.contains("bevestigen")) {
            String eventCode = extractEventCode(email.subject());
            if (eventCode != null) {
                confirmationEmailService.sendAcceptConfirmation(email.senderEmail(), eventCode);
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
