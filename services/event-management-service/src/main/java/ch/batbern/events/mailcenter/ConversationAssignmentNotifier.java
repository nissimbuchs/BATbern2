package ch.batbern.events.mailcenter;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.MailConversation;
import ch.batbern.events.dto.generated.users.UserResponse;
import ch.batbern.events.mailcenter.render.MailPipeline;
import ch.batbern.events.mailcenter.render.RenderedMail;
import ch.batbern.events.mailcenter.variables.MailVariable;
import ch.batbern.events.mailcenter.variables.MailVariables;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.shared.mail.MailContext;
import ch.batbern.shared.mail.MailTrigger;
import ch.batbern.shared.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Story 16.6 AC5: tells an organizer that a conversation was assigned to them, once, in their
 * preferred language (template {@code mailcenter-assigned}, DE + EN). Never throws: a failed
 * notification must not undo the assignment.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ConversationAssignmentNotifier {

    static final String TEMPLATE_KEY = "mailcenter-assigned";

    private final EmailService emailService;
    private final UserApiClient userApiClient;
    private final EventRepository eventRepository;
    private final MailPipeline mailPipeline;
    private final ConversationLinks links;

    public void notifyAssigned(MailConversation conversation, String assignee) {
        try {
            UserResponse user = userApiClient.getUserByUsername(assignee);
            if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
                log.warn("Assignment of {} not notified: no address for the assignee",
                        conversation.getConversationKey());
                return;
            }
            String locale = "en".equals(userApiClient.getPreferredLanguage(assignee)) ? "en" : "de";
            Optional<Event> event = Optional.ofNullable(conversation.getEventCode())
                    .flatMap(eventRepository::findByEventCode);
            MailVariables variables = event.map(e -> mailPipeline.variables().event(e, locale))
                    .orElseGet(() -> mailPipeline.variables().system().put(MailVariable.EVENT_CODE, ""))
                    .put(MailVariable.RECIPIENT_NAME, user.getFirstName() != null && !user.getFirstName().isBlank()
                            ? user.getFirstName() : assignee)
                    .put(MailVariable.COUNTERPART_NAME, counterpart(conversation))
                    .put(MailVariable.CONVERSATION_SUBJECT, conversation.getSubject())
                    .put(MailVariable.CONVERSATION_LINK,
                            links.link(conversation.getEventCode(), conversation.getConversationKey()));
            RenderedMail mail = mailPipeline.renderNotice(mailPipeline.template(TEMPLATE_KEY, locale),
                    variables.asMap());
            String subject = mail.subject() != null ? mail.subject()
                    : ("en".equals(locale) ? "Assigned to you: " : "Dir zugewiesen: ") + conversation.getSubject();
            MailContext context = MailContext.of(MailTrigger.system(), conversation.getEventCode(),
                    MailContext.Audience.ORGANIZERS, TEMPLATE_KEY);
            emailService.sendHtmlEmail(context, user.getEmail(), null, subject, mail.html(), null);
        } catch (RuntimeException e) {
            log.error("Assignment of {} not notified: {}", conversation.getConversationKey(), e.toString(), e);
        }
    }

    private static String counterpart(MailConversation conversation) {
        String name = conversation.getCounterpartName();
        return name != null && !name.isBlank() ? name : conversation.getCounterpartAddress();
    }
}
