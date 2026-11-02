package ch.batbern.events.mailcenter.compose;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.dto.generated.users.UserResponse;
import ch.batbern.events.forwarding.EmailForwardingProperties;
import ch.batbern.events.mailcenter.render.MailContent;
import ch.batbern.events.mailcenter.render.MailPipeline;
import ch.batbern.events.mailcenter.render.RenderedMail;
import ch.batbern.events.mailcenter.variables.MailVariable.Scope;
import ch.batbern.events.service.EmailTemplateService;
import ch.batbern.shared.mail.MailContext;
import ch.batbern.shared.mail.MailTrigger;
import ch.batbern.shared.service.EmailService;
import ch.batbern.shared.utils.LoggingUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Plain mail to all partners (Story 16.9 Part B). Every PARTNER-role user with an address gets the
 * mail on their own, sorted by company then name (owner decision 2026-11-02, replacing the shared
 * To: line of #1163: nobody sees the other partners' addresses). The text is the same for all, so
 * the preview does not step: system and event variables only, no salutation per person. Organizers
 * are not added; they get the test send and see the mail in "What went out".
 *
 * <p>Reply-To {@code partner@} (the organizer alias, {@code ForwardingAlias}). All sends share one
 * {@code MailContext}: one recorded mail, one recipient row per partner, delivery status per partner.
 */
@Slf4j
@Component
public class PartnerAudienceAdapter implements AudienceAdapter {

    static final String CATEGORY = "PARTNER";

    private final UserApiClient userApiClient;
    private final EmailTemplateService templates;
    private final EmailService emailService;
    private final EmailForwardingProperties forwarding;
    private final MailPipeline mailPipeline;

    @Value("${batbern.ses.configuration-set-name:#{null}}")
    private String configurationSetName;

    public PartnerAudienceAdapter(UserApiClient userApiClient, EmailTemplateService templates,
            EmailService emailService, EmailForwardingProperties forwarding, MailPipeline mailPipeline) {
        this.userApiClient = userApiClient;
        this.templates = templates;
        this.emailService = emailService;
        this.forwarding = forwarding;
        this.mailPipeline = mailPipeline;
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

    /** System and event variables only: one mail to all partners, no per-person values. */
    @Override
    public Set<Scope> scopes() {
        return Set.of();
    }

    @Override
    public List<String> offeredVariables() {
        return List.of("eventTitle", "eventDate", "eventTime", "eventNumber", "venueName");
    }

    @Override
    public void validate(ComposeRequest request) {
        if (!request.ownText() && request.templateKey() == null) {
            throw new IllegalArgumentException("templateKey is required for partners");
        }
    }

    /** Every partner with an address, once, company then name order. */
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
        List<ComposeRecipient> recipients = new ArrayList<>();
        for (int i = 0; i < addresses.size(); i++) {
            recipients.add(new ComposeRecipient(names.get(i), addresses.get(i), List.of(), null));
        }
        return new Recipients(recipients, skipped);
    }

    /** Partner mail carries no credential, so a test rendering equals the real one. */
    @Override
    public Rendering render(ComposeContext context, ComposeRecipient recipient, boolean test) {
        String templateKey = context.request().templateKey();
        String locale = locale(context.request().locale());
        MailContent content = context.request().ownText() ? ownTextContent(context)
                : mailPipeline.storedTemplate(templateKey, locale);
        if (content.bodyHtml().isBlank()) {
            throw new IllegalArgumentException("Template not found: " + templateKey);
        }
        RenderedMail mail = mailPipeline.render(content, variables(context, recipient, test));
        return new Rendering(Objects.requireNonNullElse(mail.subject(), ""), mail.html(), content.templateKey(),
                content.locale(), replyTo(), List.of(), List.of());
    }

    @Override
    public Map<String, String> variables(ComposeContext context, ComposeRecipient recipient, boolean test) {
        return mailPipeline.variables().event(context.event(), locale(context.request().locale())).asMap();
    }

    /**
     * One synchronous mail per partner (owner decision 2026-11-02: nobody sees the other partners'
     * addresses). All share one {@code MailContext}, so the send is one recorded mail with a row per
     * partner; a failed partner is recorded as FAILED and does not stop the others.
     */
    @Override
    public List<DeliveredMail> deliver(ComposeContext context, Recipients recipients, MailTrigger trigger) {
        Rendering rendering = render(context, recipients.recipients().get(0), false);
        String eventCode = context.event().getEventCode();
        MailContext mailContext = context.request().ownText()
                ? MailContext.ownText(trigger, eventCode, MailContext.Audience.PARTNERS)
                : MailContext.of(trigger, eventCode, MailContext.Audience.PARTNERS, rendering.templateKey());
        for (ComposeRecipient recipient : recipients.recipients()) {
            try {
                emailService.sendHtmlEmailSync(mailContext, recipient.address(), List.of(), rendering.subject(),
                        rendering.html(), configurationSetName, rendering.replyTo());
            } catch (RuntimeException e) {
                log.error("Partner mail to {} failed: {}", LoggingUtils.maskEmail(recipient.address()),
                        e.getClass().getSimpleName());
            }
        }
        return List.of(new DeliveredMail(mailContext, rendering));
    }

    private String replyTo() {
        return "partner@" + forwarding.domain();
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
