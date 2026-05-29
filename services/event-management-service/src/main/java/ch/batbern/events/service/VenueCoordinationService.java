package ch.batbern.events.service;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.domain.EmailTemplate;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.dto.generated.users.UserResponse;
import ch.batbern.events.dto.venuecoordination.VenueCoordinationConfig;
import ch.batbern.events.dto.venuecoordination.VenueCoordinationConfig.Contact;
import ch.batbern.events.dto.venuecoordination.VenueCoordinationConfig.Role;
import ch.batbern.events.dto.venuecoordination.VenueCoordinationPreviewResponse;
import ch.batbern.events.dto.venuecoordination.VenueCoordinationSendResponse;
import ch.batbern.events.exception.EventNotFoundException;
import ch.batbern.events.repository.EmailTemplateRepository;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
import ch.batbern.shared.service.EmailService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Orchestrates the Event-Detail Venue tab's preview + send flow:
 *
 *   1. Reads the {@link VenueCoordinationConfig} singleton (admin-managed JSON
 *      under {@code app_settings.venue.coordination.config}).
 *   2. Renders a {@link EmailTemplate} (category {@code VENUE_COORDINATION}) with
 *      event/agenda/recipient variables and wraps it in the default BATbern layout.
 *   3. Sends one or more SES mails via {@link EmailService}, applying the coordinator
 *      organizer's email as Reply-To so replies land in their inbox (not the shared
 *      noreply mailbox).
 *
 * <p>Intentionally simple — venue mail is 1–2 recipients per send, so no audit
 * table or async job queue. Send failure surfaces as HTTP 500 to the organizer.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VenueCoordinationService {

    private static final String LAYOUT_KEY = "batbern-default";
    private static final ZoneId ZURICH = ZoneId.of("Europe/Zurich");
    private static final DateTimeFormatter DATE_DE = DateTimeFormatter.ofPattern("dd.MM.yyyy")
            .withZone(ZURICH);
    private static final DateTimeFormatter DATE_EN = DateTimeFormatter.ofPattern("d MMM yyyy")
            .withLocale(Locale.ENGLISH).withZone(ZURICH);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH.mm")
            .withZone(ZURICH);
    private static final String DEFAULT_LOCALE = "de";

    private final AdminSettingsService adminSettingsService;
    private final EventRepository eventRepository;
    private final EmailTemplateRepository emailTemplateRepository;
    private final EmailTemplateService emailTemplateService;
    private final EmailService emailService;
    private final SessionRepository sessionRepository;
    private final SpeakerPoolRepository speakerPoolRepository;
    private final UserApiClient userApiClient;
    private final ObjectMapper objectMapper;

    @Value("${app.email.configuration-set:}")
    private String configurationSetName;

    // ── Preview ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public VenueCoordinationPreviewResponse preview(String eventCode, String templateKey,
                                                     Role recipientRole, String locale,
                                                     String notes) {
        VenueCoordinationConfig config = loadConfigOrThrow();
        Contact contact = config.contactFor(recipientRole);
        requireConfiguredContact(contact, recipientRole);

        Event event = loadEventOrThrow(eventCode);
        String effectiveLocale = normaliseLocale(locale);
        EmailTemplate template = loadTemplateOrThrow(templateKey, effectiveLocale);

        CoordinatorIdentity coordinator = resolveCoordinator(config.getCoordinatorUsername());
        Map<String, String> vars = buildVariables(event, contact, effectiveLocale, notes, coordinator);

        String contentHtml = emailService.replaceVariables(template.getHtmlBody(), vars);
        String mergedHtml = emailService.replaceVariables(
                emailTemplateService.mergeWithLayout(contentHtml, LAYOUT_KEY, effectiveLocale), vars);
        String subject = emailService.replaceVariables(
                Optional.ofNullable(template.getSubject()).orElse(""), vars);

        return VenueCoordinationPreviewResponse.builder()
                .subject(subject)
                .htmlBody(mergedHtml)
                .toName(contact.getName())
                .toEmail(contact.getEmail())
                .replyToEmail(coordinator.email())
                .build();
    }

    // ── Send ──────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public VenueCoordinationSendResponse send(String eventCode, String templateKey,
                                               List<Role> recipients, String locale,
                                               String notes, String sentByUsername) {
        if (recipients == null || recipients.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "At least one recipient role is required");
        }

        VenueCoordinationConfig config = loadConfigOrThrow();
        Event event = loadEventOrThrow(eventCode);
        String effectiveLocale = normaliseLocale(locale);
        EmailTemplate template = loadTemplateOrThrow(templateKey, effectiveLocale);
        CoordinatorIdentity coordinator = resolveCoordinator(config.getCoordinatorUsername());

        // Dedupe + preserve order while iterating recipients.
        List<Role> uniqueRoles = recipients.stream().distinct().toList();

        for (Role role : uniqueRoles) {
            Contact contact = config.contactFor(role);
            requireConfiguredContact(contact, role);

            Map<String, String> vars = buildVariables(event, contact, effectiveLocale, notes, coordinator);
            String contentHtml = emailService.replaceVariables(template.getHtmlBody(), vars);
            String mergedHtml = emailService.replaceVariables(
                    emailTemplateService.mergeWithLayout(contentHtml, LAYOUT_KEY, effectiveLocale), vars);
            String subject = emailService.replaceVariables(
                    Optional.ofNullable(template.getSubject()).orElse(""), vars);

            log.info("Venue-coordination send: event={}, template={}, role={}, recipient={}, replyTo={}, by={}",
                    eventCode, templateKey, role, mask(contact.getEmail()),
                    mask(coordinator.email()), sentByUsername);

            emailService.sendHtmlEmailSync(
                    contact.getEmail(),
                    List.of(),
                    subject,
                    mergedHtml,
                    configurationSetName,
                    coordinator.email()
            );
        }

        return VenueCoordinationSendResponse.builder().sentTo(uniqueRoles).build();
    }

    // ── Variable rendering ────────────────────────────────────────────────────

    private Map<String, String> buildVariables(Event event, Contact contact, String locale,
                                                String notes, CoordinatorIdentity coordinator) {
        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("eventDate", formatEventDate(event, locale));
        vars.put("eventTitle", safe(event.getTitle()));
        vars.put("topicTitle", resolveTopicTitle(event));
        vars.put("venueName", safe(event.getVenueName()));
        vars.put("salutation", safe(formatSalutation(contact)));
        vars.put("agenda", renderAgenda(event));
        vars.put("notes", notes == null ? "" : notes.trim());
        vars.put("organizerSignature", coordinator.displayName());
        return vars;
    }

    private String formatSalutation(Contact contact) {
        // Renders "Frau Senn" / "Herr Oppliger" from the admin form's "Frau" + "Gabriela Senn" → "Frau Senn".
        // Falls back to the bare name when no salutation is configured.
        String salutation = contact.getSalutation();
        String name = contact.getName();
        if (salutation == null || salutation.isBlank()) {
            return safe(name);
        }
        String lastName = lastNameOf(name);
        return (salutation.trim() + " " + lastName).trim();
    }

    private String lastNameOf(String fullName) {
        if (fullName == null || fullName.isBlank()) return "";
        String[] parts = fullName.trim().split("\\s+");
        return parts[parts.length - 1];
    }

    private String formatEventDate(Event event, String locale) {
        if (event.getDate() == null) return "";
        DateTimeFormatter fmt = "en".equals(locale) ? DATE_EN : DATE_DE;
        return fmt.format(event.getDate());
    }

    private String resolveTopicTitle(Event event) {
        // Topic data lives on Event via a separate lookup; the email-template fallback
        // is just the event title if no topic resolves. Keep this conservative — we
        // don't want a missing topic to throw.
        return safe(event.getTitle());
    }

    /**
     * Renders the event agenda as an HTML &lt;ul&gt; — one item per timed session.
     * Sessions without start times are skipped (organizer can preview again after
     * slot-assignment). Structural slots (break/lunch/moderation) appear too so the
     * receiver sees the same shape as the sample emails (Pause Vormittag, etc.).
     */
    private String renderAgenda(Event event) {
        List<Session> sessions = sessionRepository.findByEventIdAndStartTimeIsNotNull(event.getId());
        if (sessions.isEmpty()) {
            return "<p><em>Agenda noch nicht definiert.</em></p>";
        }
        // Pre-resolve speaker pool entries for sessions that reference one.
        List<UUID> poolIds = sessions.stream()
                .map(Session::getSpeakerPoolId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<UUID, SpeakerPool> poolById = poolIds.isEmpty() ? Map.of()
                : speakerPoolRepository.findAllById(poolIds).stream()
                    .collect(Collectors.toMap(SpeakerPool::getId, p -> p, (a, b) -> a));

        String items = sessions.stream()
                .sorted((a, b) -> a.getStartTime().compareTo(b.getStartTime()))
                .map(s -> renderAgendaLine(s, poolById))
                .collect(Collectors.joining("\n"));
        return "<ul>\n" + items + "\n</ul>";
    }

    private String renderAgendaLine(Session session, Map<UUID, SpeakerPool> poolById) {
        String start = TIME.format(session.getStartTime());
        String end = session.getEndTime() != null ? TIME.format(session.getEndTime()) : "";
        String title = safe(session.getTitle());
        String headerLabel;
        if (session.getSpeakerPoolId() != null) {
            SpeakerPool pool = poolById.get(session.getSpeakerPoolId());
            if (pool != null) {
                String company = (pool.getCompany() != null && !pool.getCompany().isBlank())
                        ? pool.getCompany()
                        : pool.getSpeakerName();
                headerLabel = escapeHtml(company) + ": " + escapeHtml(title);
            } else {
                headerLabel = escapeHtml(title);
            }
        } else {
            // Structural slot (break/lunch/moderation) — title alone, plain prefix.
            headerLabel = escapeHtml(title);
        }
        String time = end.isEmpty() ? start : start + " - " + end;
        return "<li>" + escapeHtml(time) + " " + headerLabel + "</li>";
    }

    // ── Config + lookups ──────────────────────────────────────────────────────

    private VenueCoordinationConfig loadConfigOrThrow() {
        String raw = adminSettingsService.getSetting(VenueCoordinationConfig.SETTING_KEY).orElse(null);
        if (raw == null || raw.isBlank()) {
            throw new ResponseStatusException(HttpStatus.PRECONDITION_FAILED,
                    "Venue coordination config not set — configure it in Administration → Venue & Catering");
        }
        try {
            return objectMapper.readValue(raw, VenueCoordinationConfig.class);
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Stored venue coordination config is malformed JSON: " + e.getMessage());
        }
    }

    private void requireConfiguredContact(Contact contact, Role role) {
        if (contact == null || !contact.isComplete()) {
            throw new ResponseStatusException(HttpStatus.PRECONDITION_FAILED,
                    role + " contact is not configured — set it in Administration → Venue & Catering");
        }
    }

    private Event loadEventOrThrow(String eventCode) {
        return eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new EventNotFoundException("Event not found: " + eventCode));
    }

    private EmailTemplate loadTemplateOrThrow(String templateKey, String locale) {
        return emailTemplateRepository.findByTemplateKeyAndLocale(templateKey, locale)
                .or(() -> emailTemplateRepository.findByTemplateKeyAndLocale(templateKey, DEFAULT_LOCALE))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Email template not found: " + templateKey));
    }

    private CoordinatorIdentity resolveCoordinator(String username) {
        if (username == null || username.isBlank()) {
            throw new ResponseStatusException(HttpStatus.PRECONDITION_FAILED,
                    "Venue coordinator not set — pick an organizer in Administration → Venue & Catering");
        }
        try {
            UserResponse user = userApiClient.getUserByUsername(username);
            if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
                throw new ResponseStatusException(HttpStatus.PRECONDITION_FAILED,
                        "Coordinator user '" + username + "' has no email — cannot apply Reply-To");
            }
            String display = (safe(user.getFirstName()) + " " + safe(user.getLastName())).trim();
            if (display.isBlank()) display = username;
            return new CoordinatorIdentity(display, user.getEmail());
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to resolve coordinator '{}': {}", username, e.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Could not resolve coordinator user '" + username + "': " + e.getMessage());
        }
    }

    private record CoordinatorIdentity(String displayName, String email) {}

    private static String normaliseLocale(String locale) {
        if (locale == null) return DEFAULT_LOCALE;
        String trimmed = locale.trim().toLowerCase(Locale.ROOT);
        return trimmed.startsWith("en") ? "en" : DEFAULT_LOCALE;
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static String mask(String email) {
        if (email == null || email.isBlank()) return "";
        int at = email.indexOf('@');
        if (at <= 1) return "***";
        return email.charAt(0) + "***" + email.substring(at);
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
