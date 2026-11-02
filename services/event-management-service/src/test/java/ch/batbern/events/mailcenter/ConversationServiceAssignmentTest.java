package ch.batbern.events.mailcenter;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.domain.MailConversation;
import ch.batbern.events.repository.MailConversationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Story 16.6 AC5: who changes the assignee to whom decides whether one notification goes out. */
@ExtendWith(MockitoExtension.class)
@DisplayName("Story 16.6: reassign notifies once")
class ConversationServiceAssignmentTest {

    @Mock
    private MailConversationRepository conversations;
    @Mock
    private UserApiClient userApiClient;
    @Mock
    private ConversationAssignmentNotifier notifier;
    @InjectMocks
    private ConversationService service;

    private MailConversation conversation;

    @BeforeEach
    void setUp() {
        conversation = new MailConversation();
        conversation.setConversationKey("conv-1");
        conversation.setAssigneeUsername("lead.organizer");
        when(conversations.findByConversationKey("conv-1")).thenReturn(Optional.of(conversation));
        lenient().when(userApiClient.getOrganizerUsernames())
                .thenReturn(List.of("lead.organizer", "second.organizer", "third.organizer"));
    }

    @Test
    @DisplayName("assigned to another organizer: one notification to the new assignee")
    void changed() {
        service.patch("conv-1", null, "second.organizer", false, "lead.organizer");
        verify(notifier).notifyAssigned(conversation, "second.organizer");
    }

    @Test
    @DisplayName("'I'll take it' (yourself): nothing")
    void self() {
        service.patch("conv-1", null, "third.organizer", false, "third.organizer");
        verify(notifier, never()).notifyAssigned(any(), anyString());
    }

    @Test
    @DisplayName("same assignee saved again: nothing")
    void same() {
        service.patch("conv-1", null, "lead.organizer", false, "second.organizer");
        verify(notifier, never()).notifyAssigned(any(), anyString());
    }

    @Test
    @DisplayName("assignee cleared, or only the status changed: nothing")
    void clearedOrStatus() {
        service.patch("conv-1", MailConversation.Status.DONE, null, false, "second.organizer");
        service.patch("conv-1", null, null, true, "second.organizer");
        verify(notifier, never()).notifyAssigned(any(), anyString());
    }
}
