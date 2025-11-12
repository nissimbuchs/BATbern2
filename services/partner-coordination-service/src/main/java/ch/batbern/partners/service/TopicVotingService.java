package ch.batbern.partners.service;

import ch.batbern.partners.domain.Partner;
import ch.batbern.partners.domain.PartnershipLevel;
import ch.batbern.partners.domain.TopicVote;
import ch.batbern.partners.events.TopicVoteSubmittedEvent;
import ch.batbern.partners.exception.PartnerNotFoundException;
import ch.batbern.partners.exception.VoteAlreadyExistsException;
import ch.batbern.partners.repository.PartnerRepository;
import ch.batbern.partners.repository.TopicVoteRepository;
import ch.batbern.shared.events.DomainEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Service layer for Topic Voting operations.
 *
 * Implements business logic for:
 * - Casting votes on topics with weighted values
 * - Vote weight calculation based on partnership level
 * - Vote uniqueness enforcement (one vote per topic per partner)
 * - Publishing domain events
 *
 * Vote Weight Calculation:
 * - BRONZE = 1
 * - SILVER = 2
 * - GOLD = 3
 * - PLATINUM = 4
 * - STRATEGIC = 5
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class TopicVotingService {

    private final TopicVoteRepository topicVoteRepository;
    private final PartnerRepository partnerRepository;
    private final DomainEventPublisher eventPublisher;

    /**
     * Get all votes cast by a partner.
     *
     * @param companyName Company name (meaningful ID)
     * @return List of votes cast by the partner
     */
    @Transactional(readOnly = true)
    public List<TopicVote> getPartnerVotes(String companyName) {
        log.debug("Getting votes for partner: {}", companyName);

        Partner partner = partnerRepository.findByCompanyName(companyName)
                .orElseThrow(() -> new PartnerNotFoundException("Partner not found for company: " + companyName));

        return topicVoteRepository.findByPartnerId(partner.getId());
    }

    /**
     * Cast a vote on a topic.
     *
     * @param companyName Company name (meaningful ID)
     * @param topicId     Topic ID
     * @param voteValue   Vote value (1-5)
     * @return Created topic vote
     * @throws PartnerNotFoundException   if partner not found
     * @throws VoteAlreadyExistsException if partner already voted on this topic
     */
    public TopicVote castVote(String companyName, UUID topicId, int voteValue) {
        log.debug("Casting vote for partner: {}, topic: {}, value: {}", companyName, topicId, voteValue);

        // Validate vote value range
        if (voteValue < 1 || voteValue > 5) {
            throw new IllegalArgumentException("Vote value must be between 1 and 5");
        }

        // Get partner
        Partner partner = partnerRepository.findByCompanyName(companyName)
                .orElseThrow(() -> new PartnerNotFoundException("Partner not found for company: " + companyName));

        // Check for duplicate vote
        if (topicVoteRepository.existsByTopicIdAndPartnerId(topicId, partner.getId())) {
            throw new VoteAlreadyExistsException("Partner has already voted on this topic");
        }

        // Calculate vote weight based on partnership level
        int voteWeight = calculateVoteWeight(partner.getPartnershipLevel());

        // Create and save vote
        TopicVote vote = TopicVote.builder()
                .topicId(topicId)
                .partnerId(partner.getId())
                .voteValue(voteValue)
                .voteWeight(voteWeight)
                .votedAt(Instant.now())
                .build();

        TopicVote savedVote = topicVoteRepository.save(vote);

        // Publish domain event
        publishTopicVoteSubmittedEvent(savedVote, companyName);

        log.info("Vote cast successfully - partner: {}, topic: {}, value: {}, weight: {}",
                companyName, topicId, voteValue, voteWeight);

        return savedVote;
    }

    /**
     * Calculate vote weight based on partnership level.
     *
     * @param level Partnership level
     * @return Vote weight (1-5)
     */
    public int calculateVoteWeight(PartnershipLevel level) {
        return switch (level) {
            case BRONZE -> 1;
            case SILVER -> 2;
            case GOLD -> 3;
            case PLATINUM -> 4;
            case STRATEGIC -> 5;
        };
    }

    /**
     * Publish TopicVoteSubmittedEvent to EventBridge.
     *
     * @param vote        Topic vote
     * @param companyName Company name
     */
    private void publishTopicVoteSubmittedEvent(TopicVote vote, String companyName) {
        TopicVoteSubmittedEvent event = TopicVoteSubmittedEvent.builder()
                .voteId(vote.getId())
                .topicId(vote.getTopicId())
                .partnerId(vote.getPartnerId())
                .companyName(companyName)
                .voteValue(vote.getVoteValue())
                .voteWeight(vote.getVoteWeight())
                .votedAt(vote.getVotedAt())
                .timestamp(Instant.now())
                .build();

        eventPublisher.publish(event);

        log.debug("Published TopicVoteSubmittedEvent for vote: {}", vote.getId());
    }
}
