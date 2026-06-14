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

    @Value("${app.base-url:https://batbern.ch}")
    private String baseUrl;

    // ── Preview ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public VenueCoordinationPreviewResponse preview(String eventCode, String templateKey,
                                                     List<Role> recipients, String locale,
                                                     String notes) {
        List<Contact> contacts = resolveContactsOrThrow(recipients);
        Event event = loadEventOrThrow(eventCode);
        String effectiveLocale = normaliseLocale(locale);
        EmailTemplate template = loadTemplateOrThrow(templateKey, effectiveLocale);
        CoordinatorIdentity coordinator = resolveCoordinator(loadConfigOrThrow().getCoordinatorUsername());

        Map<String, String> vars = buildVariables(event, contacts, effectiveLocale, notes, coordinator);
        Rendered rendered = render(template, vars, effectiveLocale);

        String to = contacts.get(0).getEmail();
        List<String> cc = contacts.size() > 1
                ? contacts.subList(1, contacts.size()).stream().map(Contact::getEmail).toList()
                : List.of();

        return VenueCoordinationPreviewResponse.builder()
                .subject(rendered.subject())
                .htmlBody(rendered.body())
                .toEmail(to)
                .ccEmails(cc)
                .replyToEmail(coordinator.email())
                .build();
    }

    // ── Send ──────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public VenueCoordinationSendResponse send(String eventCode, String templateKey,
                                               List<Role> recipients, String locale,
                                               String notes, String sentByUsername) {
        List<Contact> contacts = resolveContactsOrThrow(recipients);
        Event event = loadEventOrThrow(eventCode);
        String effectiveLocale = normaliseLocale(locale);
        EmailTemplate template = loadTemplateOrThrow(templateKey, effectiveLocale);
        CoordinatorIdentity coordinator = resolveCoordinator(loadConfigOrThrow().getCoordinatorUsername());

        List<Role> uniqueRoles = recipients.stream().distinct().toList();

        Map<String, String> vars = buildVariables(event, contacts, effectiveLocale, notes, coordinator);
        Rendered rendered = render(template, vars, effectiveLocale);

        // Single SES send: first recipient → To, rest → Cc. Matches the user's expectation
        // that the email arrives as one thread with everyone addressed together.
        String to = contacts.get(0).getEmail();
        List<String> cc = contacts.size() > 1
                ? contacts.subList(1, contacts.size()).stream().map(Contact::getEmail).toList()
                : List.of();

        log.info("Venue-coordination send: event={}, template={}, roles={}, to={}, ccCount={}, replyTo={}, by={}",
                eventCode, templateKey, uniqueRoles, mask(to), cc.size(),
                mask(coordinator.email()), sentByUsername);

        emailService.sendHtmlEmailSync(
                to,
                cc,
                rendered.subject(),
                rendered.body(),
                configurationSetName,
                coordinator.email()
        );

        return VenueCoordinationSendResponse.builder().sentTo(uniqueRoles).build();
    }

    private Rendered render(EmailTemplate template, Map<String, String> vars, String locale) {
        String contentHtml = emailService.replaceVariables(template.getHtmlBody(), vars);
        String mergedHtml = emailService.replaceVariables(
                emailTemplateService.mergeWithLayout(contentHtml, LAYOUT_KEY, locale), vars);
        String subject = emailService.replaceVariables(
                Optional.ofNullable(template.getSubject()).orElse(""), vars);
        return new Rendered(subject, mergedHtml);
    }

    private record Rendered(String subject, String body) {}

    private List<Contact> resolveContactsOrThrow(List<Role> recipients) {
        if (recipients == null || recipients.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "At least one recipient role is required");
        }
        VenueCoordinationConfig config = loadConfigOrThrow();
        List<Contact> contacts = new java.util.ArrayList<>();
        for (Role role : recipients.stream().distinct().toList()) {
            Contact contact = config.contactFor(role);
            requireConfiguredContact(contact, role);
            contacts.add(contact);
        }
        return contacts;
    }

    // ── Variable rendering ────────────────────────────────────────────────────

    private Map<String, String> buildVariables(Event event, List<Contact> contacts, String locale,
                                                String notes, CoordinatorIdentity coordinator) {
        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("eventDate", formatEventDate(event, locale));
        vars.put("eventTitle", safe(event.getTitle()));
        vars.put("topicTitle", resolveTopicTitle(event));
        vars.put("venueName", safe(event.getVenueName()));
        vars.put("salutation", formatCombinedSalutation(contacts, locale));
        vars.put("agenda", renderAgenda(event));
        // Notes is a plain-text textarea on the frontend; preserve user line breaks by
        // turning them into <br>. HTML-escape special chars first so notes can't inject
        // markup (the field is organizer-authored but defence-in-depth is cheap here).
        vars.put("notes", renderNotesHtml(notes));
        vars.put("organizerSignature", coordinator.displayName());
        // Required by the shared batbern-default layout footer ("© {{currentYear}} BATbern").
        // Same pattern as NewsletterEmailService / SpeakerInvitationEmailService.
        vars.put("currentYear", String.valueOf(java.time.Year.now().getValue()));
        // Required by the shared batbern-default layout header <img src="{{logoUrl}}">.
        // Without this the literal {{logoUrl}} placeholder leaks into the sent email.
        // Same asset + pattern as RegistrationEmailService / SpeakerInvitationEmailService.
        vars.put("logoUrl", baseUrl + "/BATbern_white_logo.png");
        return vars;
    }

    /**
     * Combines per-contact salutations into one greeting. Examples:
     *   single:    "Frau Senn"
     *   two (de):  "Frau Senn und Herr Oppliger"
     *   two (en):  "Mrs. Senn and Mr. Oppliger"
     *   three (de): "Frau A, Herr B und Frau C"
     */
    private String formatCombinedSalutation(List<Contact> contacts, String locale) {
        List<String> parts = contacts.stream()
                .map(this::formatSalutation)
                .filter(s -> !s.isBlank())
                .toList();
        if (parts.isEmpty()) {
            return "";
        }
        if (parts.size() == 1) {
            return parts.get(0);
        }
        String connector = "en".equals(locale) ? " and " : " und ";
        return String.join(", ", parts.subList(0, parts.size() - 1))
                + connector + parts.get(parts.size() - 1);
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

    /**
     * Turns the textarea content into HTML-safe inline content with line breaks preserved.
     * Empty/blank input returns an empty string so the {{#notes}}...{{/notes}} conditional
     * collapses the wrapping paragraph in the template.
     *
     * <p>Security: {@link #escapeHtml} runs FIRST (converts {@code <}, {@code >}, {@code &},
     * {@code "} to their HTML entities), so any HTML the organizer typed is neutralised before
     * the newline-to-{@code <br>} replacement runs. The resulting string is inert HTML and safe
     * to embed verbatim in the email body.
     */
    private static String renderNotesHtml(String notes) {
        if (notes == null) {
            return "";
        }
        String trimmed = notes.strip();
        if (trimmed.isEmpty()) {
            return "";
        }
        return escapeHtml(trimmed)          // ← HTML-escape first; <br> insertion is safe after
                .replace("\r\n", "\n")
                .replace("\r", "\n")
                .replace("\n", "<br>\n");
    }

    private String lastNameOf(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return "";
        }
        String[] parts = fullName.trim().split("\\s+");
        return parts[parts.length - 1];
    }

    private String formatEventDate(Event event, String locale) {
        if (event.getDate() == null) {
            return "";
        }
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
            if (display.isBlank()) {
                display = username;
            }
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
        if (locale == null) {
            return DEFAULT_LOCALE;
        }
        String trimmed = locale.trim().toLowerCase(Locale.ROOT);
        return trimmed.startsWith("en") ? "en" : DEFAULT_LOCALE;
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static String mask(String email) {
        if (email == null || email.isBlank()) {
            return "";
        }
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }

    private static String escapeHtml(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
