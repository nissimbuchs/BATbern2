package ch.batbern.events.mailcenter.compose;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.domain.EmailTemplate;
import ch.batbern.events.domain.Event;
import ch.batbern.events.dto.generated.users.UserResponse;
import ch.batbern.events.forwarding.EmailForwardingProperties;
import ch.batbern.events.service.EmailTemplateService;
import ch.batbern.shared.mail.MailContext;
import ch.batbern.shared.mail.MailTrigger;
import ch.batbern.shared.service.EmailService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Year;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Plain mail to all partners (Story 16.9 Part B). ONE mail with every PARTNER-role user on the To:
 * line, sorted by company then name, the same shape as the partner meeting invite (owner decision
 * #1163, confirmed for this mail 2026-11-01). So the audience is one recipient entry and the mail
 * is not personalised: event variables only, no salutation per person. Organizers are not added;
 * they get the test send and see the mail in "What went out".
 *
 * <p>Reply-To {@code partner@} (the organizer alias, {@code ForwardingAlias}). The record splits the
 * To: line into one recipient row per address ({@code JpaOutboundMailRecorder}), so delivery status
 * stays per partner.
 */
@Component
public class PartnerAudienceAdapter implements AudienceAdapter {

    static final String CATEGORY = "PARTNER";
    /** SES accepts at most 50 recipients per message (To + Cc + Bcc). */
    static final int MAX_ADDRESSES_PER_MAIL = 50;

    private final UserApiClient userApiClient;
    private final EmailTemplateService templates;
    private final EmailService emailService;
    private final EmailForwardingProperties forwarding;

    @Value("${app.base-url:https://batbern.ch}")
    private String baseUrl = "https://batbern.ch";

    @Value("${batbern.ses.configuration-set-name:#{null}}")
    private String configurationSetName;

    public PartnerAudienceAdapter(UserApiClient userApiClient, EmailTemplateService templates,
            EmailService emailService, EmailForwardingProperties forwarding) {
        this.userApiClient = userApiClient;
        this.templates = templates;
        this.emailService = emailService;
        this.forwarding = forwarding;
    }

    @Override
    public MailContext.Audience audience() {
        return MailContext.Audience.PARTNERS;
    }

    @Override
    public boolean personalized() {
        return false;
    }

    @Override
    public Set<String> allowedCategories() {
        return Set.of(CATEGORY);
    }

    @Override
    public boolean allowsTemplate(String templateKey) {
        return TemplateCategories.has(templates, templateKey, CATEGORY);
    }

    @Override
    public Set<String> allowedVariables() {
        return Set.of("eventTitle", "eventDate", "eventNumber", "currentYear", "logoUrl");
    }

    @Override
    public Set<String> credentialVariables() {
        return Set.of();
    }

    @Override
    public void validate(ComposeRequest request) {
        if (request.templateKey() == null) {
            throw new IllegalArgumentException("templateKey is required for partners");
        }
    }

    /** One recipient entry: every partner with an address on To:, company then name order. */
    @Override
    public Recipients resolveRecipients(ComposeContext context) {
        List<UserResponse> partners = new ArrayList<>();
        for (String username : userApiClient.getPartnerUsernames()) {
            partners.add(userApiClient.getUserByUsername(username));
        }
        Comparator<String> text = Comparator.nullsLast(Comparator.naturalOrder());
        partners.sort(Comparator.comparing((UserResponse u) -> lower(u.getCompanyId()), text)
                .thenComparing(u -> lower(u.getLastName()), text)
                .thenComparing(u -> lower(u.getFirstName()), text));

        Set<String> seen = new LinkedHashSet<>();
        List<String> addresses = new ArrayList<>();
        List<String> names = new ArrayList<>();
        List<SkippedRecipient> skipped = new ArrayList<>();
        for (UserResponse partner : partners) {
            String name = displayName(partner);
            String email = partner.getEmail();
            if (email == null || email.isBlank()) {
                skipped.add(new SkippedRecipient(name, SkippedRecipient.NO_EMAIL));
            } else if (!seen.add(email.trim().toLowerCase(Locale.ROOT))) {
                skipped.add(new SkippedRecipient(name, SkippedRecipient.DUPLICATE));
            } else {
                addresses.add(email.trim());
                names.add(name);
            }
        }
        if (addresses.size() > MAX_ADDRESSES_PER_MAIL) {
            throw new IllegalArgumentException(addresses.size() + " partners have an address, but one mail reaches"
                    + " at most " + MAX_ADDRESSES_PER_MAIL + " recipients");
        }
        if (addresses.isEmpty()) {
            return new Recipients(List.of(), skipped);
        }
        return new Recipients(List.of(new ComposeRecipient(String.join(", ", names), String.join(",", addresses),
                List.of(), null)), skipped);
    }

    /** Partner mail carries no credential, so a test rendering equals the real one. */
    @Override
    public Rendering render(ComposeContext context, ComposeRecipient recipient, boolean test) {
        String templateKey = context.request().templateKey();
        String locale = locale(context.request().locale());
        EmailTemplate template = templates.findByKeyAndLocale(templateKey, locale)
                .or(() -> templates.findByKeyAndLocale(templateKey, "de"))
                .orElseThrow(() -> new IllegalArgumentException("Template not found: " + templateKey));
        Map<String, String> vars = variables(context.event(), locale);
        String subject = emailService.replaceVariables(Objects.requireNonNullElse(template.getSubject(), ""), vars);
        String html = MailRenderer.body(emailService, templates, template.getHtmlBody(), template.getLocale(), vars);
        return new Rendering(subject, html, templateKey, template.getLocale(), replyTo(), List.of(), List.of());
    }

    /** One synchronous send to the whole To: line; {@code EmailService} records it with this context. */
    @Override
    public List<DeliveredMail> deliver(ComposeContext context, Recipients recipients, MailTrigger trigger) {
        ComposeRecipient all = recipients.recipients().get(0);
        Rendering rendering = render(context, all, false);
        MailContext mailContext = MailContext.of(trigger, context.event().getEventCode(), MailContext.Audience.PARTNERS,
                rendering.templateKey());
        emailService.sendHtmlEmailSync(mailContext, all.address(), null, rendering.subject(), rendering.html(),
                configurationSetName, rendering.replyTo());
        return List.of(new DeliveredMail(mailContext, rendering));
    }

    private String replyTo() {
        return "partner@" + forwarding.domain();
    }

    private Map<String, String> variables(Event event, String locale) {
        Map<String, String> vars = new HashMap<>();
        vars.put("eventTitle", Objects.requireNonNullElse(event.getTitle(), ""));
        vars.put("eventNumber", event.getEventNumber() == null ? "" : String.valueOf(event.getEventNumber()));
        vars.put("eventDate", event.getDate() == null ? ""
                : DateTimeFormatter.ofPattern("EEEE, d. MMMM yyyy", "en".equals(locale) ? Locale.ENGLISH
                        : Locale.GERMAN).withZone(ZoneId.of("Europe/Zurich")).format(event.getDate()));
        vars.put("currentYear", String.valueOf(Year.now().getValue()));
        vars.put("logoUrl", baseUrl + "/BATbern_white_logo.png");
        return vars;
    }

    private static String locale(String requested) {
        return requested != null && requested.toLowerCase(Locale.ROOT).startsWith("en") ? "en" : "de";
    }

    private static String displayName(UserResponse user) {
        String name = (Optional.ofNullable(user.getFirstName()).orElse("") + " "
                + Optional.ofNullable(user.getLastName()).orElse("")).trim();
        return name.isEmpty() ? Optional.ofNullable(user.getId()).orElse("") : name;
    }

    private static String lower(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }
}
