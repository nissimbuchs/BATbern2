package ch.batbern.events.mailcenter;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.domain.MailConversation;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.MailConversationRepository;
import ch.batbern.shared.exception.NotFoundException;
import ch.batbern.shared.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Story 16.2a AC12: an organizer sets a conversation's status and who answers it. Story 16.6 AC5:
 * when the assignee changes to another organizer than the caller, that organizer is notified once.
 * Story 16.7 AC1: and which event it belongs to.
 */
@Service
@RequiredArgsConstructor
public class ConversationService {

    private final MailConversationRepository conversations;
    private final UserApiClient userApiClient;
    private final ConversationAssignmentNotifier notifier;
    private final EventRepository events;
    private final NamedParameterJdbcTemplate jdbc;

    /**
     * @param status        new status, or null to keep it
     * @param assignee      new assignee, or null to keep it
     * @param clearAssignee leave the conversation unassigned
     * @param caller        the organizer making the change
     */
    @Transactional
    public void patch(String conversationKey, MailConversation.Status status, String assignee, boolean clearAssignee,
            String caller) {
        patch(conversationKey, status, assignee, clearAssignee, null, false, caller);
    }

    /**
     * As above; Story 16.7 AC1: {@code eventCode} puts the conversation and all its messages on that
     * event, {@code clearEvent} takes them off any event. Keys do not change.
     */
    @Transactional
    public void patch(String conversationKey, MailConversation.Status status, String assignee, boolean clearAssignee,
            String eventCode, boolean clearEvent, String caller) {
        MailConversation conversation = conversations.findByConversationKey(conversationKey)
                .orElseThrow(() -> new NotFoundException("Conversation", conversationKey));
        if (eventCode != null && clearEvent) {
            throw new ValidationException("eventCode", "eventCode and clearEvent exclude each other");
        }
        if (eventCode != null || clearEvent) {
            if (eventCode != null && !events.existsByEventCode(eventCode)) {
                throw new NotFoundException("Event", eventCode);
            }
            conversation.setEventCode(eventCode);
            jdbc.update("UPDATE mail_message SET event_code = :eventCode WHERE conversation_id = :id",
                    new MapSqlParameterSource().addValue("eventCode", eventCode)
                            .addValue("id", conversation.getId()));
        }
        if (status != null) {
            conversation.setStatus(status);
        }
        boolean notify = false;
        if (clearAssignee) {
            conversation.setAssigneeUsername(null);
        } else if (assignee != null) {
            if (!userApiClient.getOrganizerUsernames().contains(assignee)) {
                throw new ValidationException("assigneeUsername", "Not an organizer: " + assignee);
            }
            notify = !assignee.equals(conversation.getAssigneeUsername()) && !assignee.equals(caller);
            conversation.setAssigneeUsername(assignee);
        }
        conversation.setUpdatedAt(Instant.now());
        conversation.setUpdatedBy(caller);
        conversations.saveAndFlush(conversation);
        if (notify) {
            notifier.notifyAssigned(conversation, assignee);
        }
    }
}
