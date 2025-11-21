package ch.batbern.partners.controller;

import ch.batbern.partners.domain.TopicVote;
import ch.batbern.partners.service.TopicVotingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for Topic Voting operations.
 *
 * Provides endpoints for:
 * - Listing partner votes
 * - Casting votes on topics with weighted values
 *
 * All business logic delegated to TopicVotingService.
 */
@RestController
@RequestMapping("/api/v1/partners/{companyName}/votes")
@RequiredArgsConstructor
@Slf4j
public class TopicVotingController {

    private final TopicVotingService topicVotingService;

    /**
     * List all votes cast by a partner.
     *
     * @param companyName Company name (meaningful ID)
     * @return List of votes
     */
    @GetMapping
    public ResponseEntity<List<TopicVoteResponse>> getPartnerVotes(
            @PathVariable String companyName) {

        log.debug("GET /partners/{}/votes", companyName);

        List<TopicVote> votes = topicVotingService.getPartnerVotes(companyName);
        List<TopicVoteResponse> responses = votes.stream()
                .map(this::mapToResponse)
                .toList();

        return ResponseEntity.ok(responses);
    }

    /**
     * Cast a vote on a topic.
     *
     * @param companyName     Company name (meaningful ID)
     * @param castVoteRequest Vote request containing topicId and voteValue
     * @return Created topic vote
     */
    @PostMapping
    public ResponseEntity<TopicVoteResponse> castVote(
            @PathVariable String companyName,
            @RequestBody Map<String, Object> castVoteRequest) {

        log.info("POST /partners/{}/votes", companyName);

        UUID topicId = UUID.fromString((String) castVoteRequest.get("topicId"));
        int voteValue = (Integer) castVoteRequest.get("voteValue");

        TopicVote vote = topicVotingService.castVote(companyName, topicId, voteValue);

        return ResponseEntity.status(HttpStatus.CREATED).body(mapToResponse(vote));
    }

    /**
     * Map TopicVote entity to response DTO.
     *
     * @param vote Topic vote entity
     * @return Topic vote response DTO
     */
    private TopicVoteResponse mapToResponse(TopicVote vote) {
        TopicVoteResponse response = new TopicVoteResponse();
        response.setTopicId(vote.getTopicId().toString());
        response.setVoteValue(vote.getVoteValue());
        response.setVoteWeight(vote.getVoteWeight());
        response.setVotedAt(vote.getVotedAt().toString());
        return response;
    }

    /**
     * Response DTO for topic vote.
     */
    @lombok.Data
    public static class TopicVoteResponse {
        private String topicId;
        private Integer voteValue;
        private Integer voteWeight;
        private String votedAt;
    }
}
