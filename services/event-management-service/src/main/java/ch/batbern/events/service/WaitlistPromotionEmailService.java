package ch.batbern.events.service;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.domain.EmailTemplate;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Registration;
import ch.batbern.events.dto.generated.users.UserResponse;
import ch.batbern.events.exception.UserNotFoundException;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.shared.service.EmailService;
import ch.batbern.shared.utils.LoggingUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
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
    private final EmailTemplateService emailTemplateService;
    private final UserApiClient userApiClient;
    private final EventRepository eventRepository;
    private final ConfirmationTokenService confirmationTokenService;

    @Value("${app.base-url:https://batbern.ch}")
    private String baseUrl;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final ZoneId SWISS_ZONE = ZoneId.of("Europe/Zurich");

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

            String contentHtml = loadHtmlContent("waitlist-promotion", localeStr,
                    "email-templates/waitlist-promotion-" + localeStr + ".html");

            Map<String, String> variables = buildCommonVariables(registration, attendee, event);
            String html = emailService.replaceVariables(contentHtml, variables);

            String subject = emailTemplateService.resolveSubject("waitlist-promotion", localeStr)
                    .map(s -> emailService.replaceVariables(s, variables))
                    .orElseGet(() -> localeStr.equals("de")
                            ? "Sie wurden von der Warteliste registriert – " + event.getTitle()
                            : "You've been promoted from the waitlist – " + event.getTitle());

            emailService.sendHtmlEmail(attendee.getEmail(), subject, html);
            log.info("Waitlist-promotion email sent to: {}", LoggingUtils.maskEmail(attendee.getEmail()));

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

            String contentHtml = loadHtmlContent("registration-waitlist-confirmation", localeStr,
                    "email-templates/registration-waitlist-confirmation-" + localeStr + ".html");

            Map<String, String> baseVariables = buildCommonVariables(registration, attendee, event);
            // Add waitlist-specific variable
            final Map<String, String> variables;
            if (registration.getWaitlistPosition() != null) {
                java.util.HashMap<String, String> extended = new java.util.HashMap<>(baseVariables);
                extended.put("waitlistPosition", String.valueOf(registration.getWaitlistPosition()));
                variables = extended;
            } else {
                variables = baseVariables;
            }

            String html = emailService.replaceVariables(contentHtml, variables);

            String subject = emailTemplateService.resolveSubject("registration-waitlist-confirmation", localeStr)
                    .map(s -> emailService.replaceVariables(s, variables))
                    .orElseGet(() -> localeStr.equals("de")
                            ? "Sie stehen auf der Warteliste – " + event.getTitle()
                            : "You're on the waitlist – " + event.getTitle());

            emailService.sendHtmlEmail(attendee.getEmail(), subject, html);
            log.info("Waitlist-confirmation email sent to: {}", LoggingUtils.maskEmail(attendee.getEmail()));

        } catch (Exception e) {
            log.error("Failed to send waitlist-confirmation email for registration: {}",
                    registration.getRegistrationCode(), e);
            // Don't re-throw — email failure must not block registration
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Map<String, String> buildCommonVariables(Registration registration, UserResponse attendee, Event event) {
        String recipientName = (attendee.getFirstName() != null && !attendee.getFirstName().isBlank())
                ? attendee.getFirstName()
                : attendee.getId();
        String formattedDate = event.getDate() != null
                ? event.getDate().atZone(SWISS_ZONE).format(DATE_FORMATTER) : "";

        // Story 10.12 (AC7-equiv): Generate confirmation token so promoted attendees can confirm
        // attendance, and expose deregistrationUrl so they can cancel via the self-service page.
        String confirmationToken = confirmationTokenService.generateConfirmationToken(
                registration.getId(), event.getEventCode());
        String confirmationUrl = baseUrl + "/events/" + event.getEventCode()
                + "/confirm-registration?token=" + confirmationToken;
        String deregistrationUrl = registration.getDeregistrationToken() != null
                ? baseUrl + "/deregister?token=" + registration.getDeregistrationToken()
                : "";

        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("recipientName", recipientName);
        vars.put("eventTitle", event.getTitle());
        vars.put("eventCode", event.getEventCode());
        vars.put("eventDate", formattedDate);
        vars.put("venueAddress", event.getVenueAddress() != null ? event.getVenueAddress() : "");
        vars.put("registrationCode", registration.getRegistrationCode());
        vars.put("eventUrl", baseUrl + "/events/" + event.getEventCode());
        vars.put("currentYear", String.valueOf(java.time.Year.now().getValue()));
        vars.put("confirmationUrl", confirmationUrl);
        vars.put("deregistrationUrl", deregistrationUrl);
        return vars;
    }

    private UserResponse resolveUser(String username) {
        try {
            return userApiClient.getUserByUsername(username);
        } catch (UserNotFoundException e) {
            log.warn("Cannot send waitlist email — user not found: {}", username);
            return null;
        }
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

    private String loadHtmlContent(String templateKey, String localeStr, String classpathFallback) {
        Optional<EmailTemplate> dbTemplate = emailTemplateService.findByKeyAndLocale(templateKey, localeStr);
        if (dbTemplate.isPresent()) {
            String contentHtml = dbTemplate.get().getHtmlBody();
            String layoutKey = dbTemplate.get().getLayoutKey();
            if (layoutKey != null) {
                return emailTemplateService.mergeWithLayout(contentHtml, layoutKey, localeStr);
            }
            return contentHtml;
        }
        try {
            ClassPathResource resource = new ClassPathResource(classpathFallback);
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Email template not found in DB or classpath: {}/{}", templateKey, localeStr);
            return "";
        }
    }
}
