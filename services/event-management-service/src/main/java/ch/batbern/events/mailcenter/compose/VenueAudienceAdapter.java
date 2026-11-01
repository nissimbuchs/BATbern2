package ch.batbern.events.mailcenter.compose;

import ch.batbern.events.dto.venuecoordination.VenueCoordinationConfig.Role;
import ch.batbern.events.service.EmailTemplateService;
import ch.batbern.events.service.VenueCoordinationService;
import ch.batbern.events.service.VenueCoordinationService.VenueMail;
import ch.batbern.shared.mail.MailContext;
import ch.batbern.shared.mail.MailTrigger;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * Venue and catering (Story 16.8). One mail for the chosen roles: the first is To, the rest Cc,
 * Reply-To the coordinator, one combined salutation. So the audience is one recipient entry and
 * the mail is not personalised. Delivery is the existing synchronous venue send.
 */
@Component
@RequiredArgsConstructor
public class VenueAudienceAdapter implements AudienceAdapter {

    private static final String CATEGORY = "VENUE_COORDINATION";

    private final VenueCoordinationService venue;
    private final EmailTemplateService templates;

    @Override
    public MailContext.Audience audience() {
        return MailContext.Audience.VENUE;
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
        return Set.of("eventDate", "eventTitle", "topicTitle", "venueName", "salutation", "agenda", "notes",
                "organizerSignature", "currentYear", "logoUrl");
    }

    @Override
    public Set<String> credentialVariables() {
        return Set.of();
    }

    @Override
    public void validate(ComposeRequest request) {
        if (request.templateKey() == null) {
            throw new IllegalArgumentException("templateKey is required for venue and catering");
        }
        if (request.venueRecipients().isEmpty()) {
            throw new IllegalArgumentException("venueRecipients needs at least one of VENUE, CATERING");
        }
        roles(request);
    }

    @Override
    public Recipients resolveRecipients(ComposeContext context) {
        VenueMail mail = render(context);
        return new Recipients(List.of(new ComposeRecipient(String.join(", ", mail.toNames()), mail.to(), mail.cc(),
                mail)), List.of());
    }

    /** Venue mail carries no credential, so a test rendering equals the real one. */
    @Override
    public Rendering render(ComposeContext context, ComposeRecipient recipient, boolean test) {
        VenueMail mail = recipient.key() instanceof VenueMail rendered ? rendered : render(context);
        return new Rendering(mail.subject(), mail.body(), context.request().templateKey(), context.request().locale(),
                mail.replyTo(), List.of(), List.of());
    }

    @Override
    public List<DeliveredMail> deliver(ComposeContext context, Recipients recipients, MailTrigger trigger) {
        ComposeRequest request = context.request();
        venue.send(context.event().getEventCode(), request.templateKey(), roles(request), request.locale(),
                request.venueNotes(), context.organizerUsername(), trigger);
        MailContext mailContext = MailContext.of(trigger, context.event().getEventCode(), MailContext.Audience.VENUE,
                request.templateKey());
        return List.of(new DeliveredMail(mailContext, render(context, recipients.recipients().get(0), false)));
    }

    private VenueMail render(ComposeContext context) {
        ComposeRequest request = context.request();
        return venue.renderFor(context.event().getEventCode(), request.templateKey(), roles(request),
                request.locale(), request.venueNotes());
    }

    private static List<Role> roles(ComposeRequest request) {
        try {
            return request.venueRecipients().stream().map(Role::valueOf).toList();
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("venueRecipients may contain VENUE and CATERING only", e);
        }
    }
}
