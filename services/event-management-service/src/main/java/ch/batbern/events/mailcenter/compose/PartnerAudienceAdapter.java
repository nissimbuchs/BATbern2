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
    private final MailPipeline mailPipeline;

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
        MailContent content = mailPipeline.storedTemplate(templateKey, locale);
        if (content.bodyHtml().isBlank()) {
            throw new IllegalArgumentException("Template not found: " + templateKey);
        }
        RenderedMail mail = mailPipeline.render(content, variables(context, recipient, test));
        return new Rendering(Objects.requireNonNullElse(mail.subject(), ""), mail.html(), templateKey,
                content.locale(), replyTo(), List.of(), List.of());
    }

    @Override
    public Map<String, String> variables(ComposeContext context, ComposeRecipient recipient, boolean test) {
        return mailPipeline.variables().event(context.event(), locale(context.request().locale())).asMap();
    }

    /**
     * One synchronous send to every partner in To:, each address handed over on its own (a joined
     * To: string is one invalid address for SES, #1176); {@code EmailService} records it once.
     */
    @Override
    public List<DeliveredMail> deliver(ComposeContext context, Recipients recipients, MailTrigger trigger) {
        ComposeRecipient all = recipients.recipients().get(0);
        Rendering rendering = render(context, all, false);
        MailContext mailContext = MailContext.of(trigger, context.event().getEventCode(), MailContext.Audience.PARTNERS,
                rendering.templateKey());
        emailService.sendGroupHtmlEmailSync(mailContext, List.of(all.address().split(",")), List.of(),
                rendering.subject(), rendering.html(), rendering.replyTo());
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
