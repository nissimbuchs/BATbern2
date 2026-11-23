package ch.batbern.events.service;

import ch.batbern.events.domain.SessionProposal;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SessionProposalRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
import ch.batbern.shared.types.SpeakerWorkflowState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * A speaker proposal and its Mail Center conversation. The Mail Center links the conversation it
 * opened for a proposal, and tells this service when an organizer answers in a conversation: the
 * first answer to a proposal still at IDENTIFIED is the contact, recorded as an email outreach
 * (IDENTIFIED to CONTACTED), as "Log outreach" would. Later answers record nothing.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SpeakerProposalConversations {

    static final String OUTREACH_NOTE = "Antwort im Mail-Center / answered in the Mail Center";

    private final SessionProposalRepository proposals;
    private final SpeakerPoolRepository speakerPool;
    private final EventRepository events;
    private final SpeakerOutreachService outreach;
    private final Clock clock;

    @Transactional
    public void link(UUID proposalId, String conversationKey) {
        proposals.findById(proposalId).ifPresent(proposal -> {
            if (proposal.getConversationKey() == null) {
                proposal.setConversationKey(conversationKey);
            }
        });
    }

    /**
     * An organizer answered in {@code conversationKey}. Records the contact when the conversation
     * is a proposal's and the speaker is still IDENTIFIED; otherwise does nothing. Its own
     * transaction: a failure here must not roll back the answer that was already sent.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void organizerAnswered(String conversationKey, String organizerUsername) {
        proposals.findByConversationKey(conversationKey).ifPresent(proposal -> {
            SpeakerPool speaker = speakerPool.findById(proposal.getSpeakerPoolId()).orElse(null);
            if (speaker == null || speaker.getStatus() != SpeakerWorkflowState.IDENTIFIED) {
                return;
            }
            String eventCode = eventCodeOf(proposal);
            outreach.recordOutreach(eventCode, speaker.getId(), Instant.now(clock), "email", OUTREACH_NOTE,
                    organizerUsername);
            log.info("Answer to speaker proposal {} recorded as email outreach by {}", proposal.getId(),
                    organizerUsername);
        });
    }

    private String eventCodeOf(SessionProposal proposal) {
        return events.findById(proposal.getEventId())
                .orElseThrow(() -> new IllegalStateException("Event of proposal " + proposal.getId() + " is gone"))
                .getEventCode();
    }
}
