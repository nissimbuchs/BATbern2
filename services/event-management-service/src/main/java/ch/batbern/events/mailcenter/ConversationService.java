package ch.batbern.events.mailcenter;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.domain.MailConversation;
import ch.batbern.events.repository.MailConversationRepository;
import ch.batbern.shared.exception.NotFoundException;
import ch.batbern.shared.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Story 16.2a AC12: an organizer sets a conversation's status and who answers it. Story 16.6 AC5:
 * when the assignee changes to another organizer than the caller, that organizer is notified once.
 */
@Service
@RequiredArgsConstructor
public class ConversationService {

    private final MailConversationRepository conversations;
    private final UserApiClient userApiClient;
    private final ConversationAssignmentNotifier notifier;

    /**
     * @param status        new status, or null to keep it
     * @param assignee      new assignee, or null to keep it
     * @param clearAssignee leave the conversation unassigned
     * @param caller        the organizer making the change
     */
    @Transactional
    public void patch(String conversationKey, MailConversation.Status status, String assignee, boolean clearAssignee,
            String caller) {
        MailConversation conversation = conversations.findByConversationKey(conversationKey)
                .orElseThrow(() -> new NotFoundException("Conversation", conversationKey));
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
