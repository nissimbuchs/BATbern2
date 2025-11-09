package ch.batbern.partners.service;

import ch.batbern.partners.domain.Partner;
import ch.batbern.partners.domain.SuggestionStatus;
import ch.batbern.partners.domain.TopicSuggestion;
import ch.batbern.partners.events.TopicSuggestionSubmittedEvent;
import ch.batbern.partners.exception.PartnerNotFoundException;
import ch.batbern.partners.repository.PartnerRepository;
import ch.batbern.partners.repository.TopicSuggestionRepository;
import ch.batbern.shared.events.DomainEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Service layer for Topic Suggestion operations.
 *
 * Implements business logic for:
 * - Submitting topic suggestions
 * - Listing partner suggestions
 * - Validating suggestion fields
 * - Publishing domain events
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class TopicSuggestionService {

    private final TopicSuggestionRepository topicSuggestionRepository;
    private final PartnerRepository partnerRepository;
    private final DomainEventPublisher eventPublisher;

    /**
     * Get all suggestions submitted by a partner.
     *
     * @param companyName Company name (meaningful ID)
     * @return List of suggestions by the partner
     */
    @Transactional(readOnly = true)
    public List<TopicSuggestion> getPartnerSuggestions(String companyName) {
        log.debug("Getting suggestions for partner: {}", companyName);

        Partner partner = partnerRepository.findByCompanyName(companyName)
                .orElseThrow(() -> new PartnerNotFoundException("Partner not found for company: " + companyName));

        return topicSuggestionRepository.findByPartnerId(partner.getId());
    }

    /**
     * Submit a new topic suggestion.
     *
     * @param companyName           Company name (meaningful ID)
     * @param suggestedTopic        Topic title
     * @param description           Topic description
     * @param businessJustification Business justification (optional)
     * @return Created topic suggestion
     * @throws PartnerNotFoundException if partner not found
     */
    public TopicSuggestion submitSuggestion(
            String companyName,
            String suggestedTopic,
            String description,
            String businessJustification) {

        log.debug("Submitting suggestion for partner: {}, topic: {}", companyName, suggestedTopic);

        // Get partner
        Partner partner = partnerRepository.findByCompanyName(companyName)
                .orElseThrow(() -> new PartnerNotFoundException("Partner not found for company: " + companyName));

        // Create suggestion
        TopicSuggestion suggestion = TopicSuggestion.builder()
                .partnerId(partner.getId())
                .suggestedTopic(suggestedTopic)
                .description(description)
                .businessJustification(businessJustification)
                .status(SuggestionStatus.SUBMITTED)
                .suggestedAt(Instant.now())
                .build();

        // Validate fields
        suggestion.validate();

        // Save suggestion
        TopicSuggestion savedSuggestion = topicSuggestionRepository.save(suggestion);

        // Publish domain event
        publishTopicSuggestionSubmittedEvent(savedSuggestion, companyName);

        log.info("Suggestion submitted successfully - partner: {}, topic: {}",
                companyName, suggestedTopic);

        return savedSuggestion;
    }

    /**
     * Publish TopicSuggestionSubmittedEvent to EventBridge.
     *
     * @param suggestion  Topic suggestion
     * @param companyName Company name
     */
    private void publishTopicSuggestionSubmittedEvent(TopicSuggestion suggestion, String companyName) {
        TopicSuggestionSubmittedEvent event = TopicSuggestionSubmittedEvent.builder()
                .suggestionId(suggestion.getId())
                .partnerId(suggestion.getPartnerId())
                .companyName(companyName)
                .suggestedTopic(suggestion.getSuggestedTopic())
                .description(suggestion.getDescription())
                .businessJustification(suggestion.getBusinessJustification())
                .status(suggestion.getStatus())
                .suggestedAt(suggestion.getSuggestedAt())
                .timestamp(Instant.now())
                .build();

        eventPublisher.publish(event);

        log.debug("Published TopicSuggestionSubmittedEvent for suggestion: {}", suggestion.getId());
    }
}
