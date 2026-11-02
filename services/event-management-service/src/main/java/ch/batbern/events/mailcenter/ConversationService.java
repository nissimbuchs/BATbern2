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
 * Story 16.2a AC12: an organizer sets a conversation's status and who answers it. Sends nothing
 * (the assignment notification is Story 16.6).
 */
@Service
@RequiredArgsConstructor
public class ConversationService {

    private final MailConversationRepository conversations;
    private final UserApiClient userApiClient;

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
        if (clearAssignee) {
            conversation.setAssigneeUsername(null);
        } else if (assignee != null) {
            if (!userApiClient.getOrganizerUsernames().contains(assignee)) {
                throw new ValidationException("assigneeUsername", "Not an organizer: " + assignee);
            }
            conversation.setAssigneeUsername(assignee);
        }
        conversation.setUpdatedAt(Instant.now());
        conversation.setUpdatedBy(caller);
        conversations.saveAndFlush(conversation);
    }
}
