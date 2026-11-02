package ch.batbern.events.service;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Registration;
import ch.batbern.events.dto.generated.users.UserResponse;
import ch.batbern.events.exception.UserNotFoundException;
import ch.batbern.events.mailcenter.render.MailPipeline;
import ch.batbern.events.mailcenter.render.RenderedMail;
import ch.batbern.events.mailcenter.variables.MailVariable;
import ch.batbern.events.mailcenter.variables.MailVariables;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.shared.mail.MailContext;
import ch.batbern.shared.service.EmailService;
import ch.batbern.shared.utils.LoggingUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Service for sending waitlist-related emails.
 * Story 10.11: Venue Capacity Enforcement & Waitlist Management (AC3, AC9)
 *
 * Sends:
 * - waitlist-promotion email: when a waitlisted attendee is promoted to registered
 * - registration-waitlist-confirmation email: when an attendee is placed on the waitlist
 *
 * Follows the same DB-first, classpath-fallback pattern as TaskReminderEmailService.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WaitlistPromotionEmailService {

    private final EmailService emailService;
    private final UserApiClient userApiClient;
    private final MailPipeline mailPipeline;
    private final EventRepository eventRepository;
    private final ConfirmationTokenService confirmationTokenService;

    @Value("${app.base-url:https://batbern.ch}")
    private String baseUrl;


    /**
     * Send waitlist-promotion email when a registration is promoted from waitlist to registered.
     * AC3: Promotion email sent after WaitlistPromotionService promotes a registration.
     */
    @Async
    public void sendPromotionEmail(Registration registration) {
        try {
            UserResponse attendee = resolveUser(registration.getAttendeeUsername());
            if (attendee == null) {
                return;
            }

            Event event = resolveEvent(registration.getEventId());
            if (event == null) {
                return;
            }

            log.info("Sending waitlist-promotion email to: {} for event: {}",
                    LoggingUtils.maskEmail(attendee.getEmail()), event.getEventCode());

            Locale locale = resolveLocale(attendee);
            String localeStr = locale.getLanguage();

            Map<String, String> variables = buildCommonVariables(registration, attendee, event, localeStr).asMap();
            RenderedMail mail = mailPipeline.render(mailPipeline.template("waitlist-promotion", localeStr),
                    variables);
            String html = mail.html();
            String subject = mail.subject() != null ? mail.subject()
                    : localeStr.equals("de")
                            ? "Sie wurden von der Warteliste registriert – " + event.getTitle()
                            : "You've been promoted from the waitlist – " + event.getTitle();

            // Story 10.32: CC attendee's additional emails (empty list = unchanged behaviour)
            java.util.List<String> cc = additionalEmailsFor(attendee);
            MailContext context = MailContext.individual(event.getEventCode(), MailContext.Audience.REGISTRANTS,
                    "waitlist-promotion").withSecrets(registration.getRegistrationCode());
            emailService.sendHtmlEmail(context, attendee.getEmail(), cc, subject, html, null);
            log.info("Waitlist-promotion email sent to: {} (ccCount={})",
                    LoggingUtils.maskEmail(attendee.getEmail()), cc.size());

        } catch (Exception e) {
            log.error("Failed to send waitlist-promotion email for registration: {}",
                    registration.getRegistrationCode(), e);
            // Don't re-throw — email failure must not block the promotion
        }
    }

    /**
     * Send waitlist-confirmation email when a registration is placed on the waitlist.
     * AC9: Sent instead of normal confirmation when status becomes "waitlist".
     */
    @Async
    public void sendWaitlistConfirmationEmail(Registration registration) {
        try {
            UserResponse attendee = resolveUser(registration.getAttendeeUsername());
            if (attendee == null) {
                return;
            }

            Event event = resolveEvent(registration.getEventId());
            if (event == null) {
                return;
            }

            log.info("Sending waitlist-confirmation email to: {} for event: {}",
                    LoggingUtils.maskEmail(attendee.getEmail()), event.getEventCode());

            Locale locale = resolveLocale(attendee);
            String localeStr = locale.getLanguage();

            MailVariables vars = buildCommonVariables(registration, attendee, event, localeStr);
            if (registration.getWaitlistPosition() != null) {
                vars.put(MailVariable.WAITLIST_POSITION, registration.getWaitlistPosition());
            }
            RenderedMail mail = mailPipeline.render(
                    mailPipeline.template("registration-waitlist-confirmation", localeStr), vars.asMap());
            String html = mail.html();
            String subject = mail.subject() != null ? mail.subject()
                    : localeStr.equals("de")
                            ? "Sie stehen auf der Warteliste – " + event.getTitle()
                            : "You're on the waitlist – " + event.getTitle();

            // Story 10.32: CC attendee's additional emails (empty list = unchanged behaviour)
            java.util.List<String> cc = additionalEmailsFor(attendee);
            MailContext context = MailContext.individual(event.getEventCode(), MailContext.Audience.REGISTRANTS,
                    "registration-waitlist-confirmation").withSecrets(registration.getRegistrationCode());
            emailService.sendHtmlEmail(context, attendee.getEmail(), cc, subject, html, null);
            log.info("Waitlist-confirmation email sent to: {} (ccCount={})",
                    LoggingUtils.maskEmail(attendee.getEmail()), cc.size());

        } catch (Exception e) {
            log.error("Failed to send waitlist-confirmation email for registration: {}",
                    registration.getRegistrationCode(), e);
            // Don't re-throw — email failure must not block registration
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private MailVariables buildCommonVariables(Registration registration, UserResponse attendee, Event event,
            String locale) {
        String recipientName = (attendee.getFirstName() != null && !attendee.getFirstName().isBlank())
                ? attendee.getFirstName()
                : attendee.getId();

        // Story 10.12 (AC7-equiv): Generate confirmation token so promoted attendees can confirm
        // attendance, and expose deregistrationUrl so they can cancel via the self-service page.
        String confirmationToken = confirmationTokenService.generateConfirmationToken(
                registration.getId(), event.getEventCode());
        String confirmationUrl = baseUrl + "/events/" + event.getEventCode()
                + "/confirm-registration?token=" + confirmationToken;
        String deregistrationUrl = registration.getDeregistrationToken() != null
                ? baseUrl + "/deregister?token=" + registration.getDeregistrationToken()
                : "";

        return mailPipeline.variables().event(event, locale)
                .put(MailVariable.RECIPIENT_NAME, recipientName)
                // Empty, not "TBA", when the venue is unknown: open unification decision (16.11)
                .put(MailVariable.VENUE_ADDRESS, event.getVenueAddress() != null ? event.getVenueAddress() : "")
                .put(MailVariable.REGISTRATION_CODE, registration.getRegistrationCode())
                .put(MailVariable.CONFIRMATION_URL, confirmationUrl)
                .put(MailVariable.DEREGISTRATION_URL, deregistrationUrl);
    }

    private UserResponse resolveUser(String username) {
        try {
            return userApiClient.getUserByUsername(username);
        } catch (UserNotFoundException e) {
            log.warn("Cannot send waitlist email — user not found: {}", username);
            return null;
        }
    }

    /**
     * Story 10.32 — flatten the attendee's additional emails into a list of plain
     * strings for CC routing. Returns an empty list when the field is absent (CUMS
     * predates Story 10.32) or the user has none.
     */
    private static java.util.List<String> additionalEmailsFor(UserResponse attendee) {
        if (attendee == null) {
            return java.util.List.of();
        }
        java.util.List<ch.batbern.events.dto.generated.users.AdditionalEmail> raw =
                attendee.getAdditionalEmails();
        if (raw == null || raw.isEmpty()) {
            return java.util.List.of();
        }
        return raw.stream()
                .map(ch.batbern.events.dto.generated.users.AdditionalEmail::getEmail)
                .filter(java.util.Objects::nonNull)
                .filter(s -> !s.isBlank())
                .toList();
    }

    private Event resolveEvent(java.util.UUID eventId) {
        Optional<Event> opt = eventRepository.findById(eventId);
        if (opt.isEmpty()) {
            log.warn("Cannot send waitlist email — event not found: {}", eventId);
            return null;
        }
        return opt.get();
    }

    private Locale resolveLocale(UserResponse attendee) {
        // ADR-004: User fields not duplicated in Registration entity.
        // preferredLanguage will be available when Story 10.15 adds it to UserResponse.
        // Default to German (BATbern primary language) until then.
        return Locale.GERMAN;
    }
}
