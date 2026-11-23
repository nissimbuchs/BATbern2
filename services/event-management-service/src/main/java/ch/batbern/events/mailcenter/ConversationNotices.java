package ch.batbern.events.mailcenter;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.domain.Event;
import ch.batbern.events.dto.generated.users.UserResponse;
import ch.batbern.events.forwarding.EmailForwardingService;
import ch.batbern.events.forwarding.ForwardingDirectory;
import ch.batbern.events.mailcenter.inbound.InboundMailCenter;
import ch.batbern.events.mailcenter.render.MailLocales;
import ch.batbern.events.mailcenter.render.MailPipeline;
import ch.batbern.events.mailcenter.render.RenderedMail;
import ch.batbern.events.mailcenter.variables.MailVariable;
import ch.batbern.events.mailcenter.variables.MailVariables;
import ch.batbern.events.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Story 16.6 AC8: the short mail that replaces a forwarded copy when the mail's group is answered in
 * the Mail Center (template {@code mailcenter-new-mail}, DE + EN): who wrote, the subject, and a link
 * to the conversation. No body, no attachment, no quoted text. The language follows the recipient's
 * preference when the address belongs to an organizer, else German.
 *
 * <p>Who wrote: the From display name; without one (a bare address in the header), the person CUMS
 * knows for that address when exactly one owns it, else the address.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ConversationNotices {

    static final String TEMPLATE_KEY = "mailcenter-new-mail";

    private final MailPipeline mailPipeline;
    private final EventRepository eventRepository;
    private final ForwardingDirectory directory;
    private final UserApiClient userApiClient;
    private final ConversationLinks links;

    /** The notice for each recipient address, rendered once per language. */
    public Function<String, EmailForwardingService.Notice> forStored(InboundMailCenter.Stored stored) {
        Optional<Event> event = Optional.ofNullable(stored.eventCode()).flatMap(eventRepository::findByEventCode);
        String from = senderOf(stored);
        Map<String, EmailForwardingService.Notice> byLocale = new HashMap<>();
        return recipient -> byLocale.computeIfAbsent(localeOf(recipient),
                locale -> render(stored, from, event, locale));
    }

    private EmailForwardingService.Notice render(InboundMailCenter.Stored stored, String from,
            Optional<Event> event, String locale) {
        MailVariables variables = event.map(e -> mailPipeline.variables().event(e, locale))
                .orElseGet(() -> mailPipeline.variables().system().put(MailVariable.EVENT_CODE, ""))
                .put(MailVariable.COUNTERPART_NAME, from)
                .put(MailVariable.CONVERSATION_SUBJECT, stored.subject())
                .put(MailVariable.CONVERSATION_LINK, links.link(stored.eventCode(), stored.conversationKey()));
        RenderedMail mail = mailPipeline.renderNotice(mailPipeline.template(TEMPLATE_KEY, locale), variables.asMap());
        String subject = mail.subject() != null ? mail.subject()
                : ("en".equals(locale) ? "New mail from " : "Neue Mail von ") + from;
        return new EmailForwardingService.Notice(subject, mail.html());
    }

    private String senderOf(InboundMailCenter.Stored stored) {
        if (stored.fromName() != null && !stored.fromName().isBlank()) {
            return stored.fromName().trim();
        }
        String address = stored.fromAddress();
        if (address == null || address.isBlank()) {
            return "?";
        }
        return knownPerson(address).orElse(address);
    }

    private Optional<String> knownPerson(String address) {
        try {
            List<String> owners = userApiClient.getEmailOwners(address);
            if (owners.size() != 1) {
                return Optional.empty();
            }
            UserResponse user = userApiClient.getUserByUsername(owners.get(0));
            String name = Stream.of(user.getFirstName(), user.getLastName())
                    .filter(part -> part != null && !part.isBlank())
                    .map(String::trim)
                    .collect(Collectors.joining(" "));
            return name.isEmpty() ? Optional.empty() : Optional.of(name);
        } catch (RuntimeException e) {
            log.debug("Notice sender name not resolved: {}", e.toString());
            return Optional.empty();
        }
    }

    private String localeOf(String recipient) {
        try {
            return directory.organizerUsernameFor(recipient)
                    .map(userApiClient::getPreferredLanguage)
                    .map(MailLocales::languageOf)
                    .orElse("de");
        } catch (RuntimeException e) {
            log.debug("Notice language for a recipient not resolved: {}", e.toString());
            return "de";
        }
    }
}
