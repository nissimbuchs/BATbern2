package ch.batbern.partners.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for TopicVote entity domain logic.
 *
 * Tests vote weight calculation based on partnership level
 * and vote value validation (1-5 scale).
 */
class TopicVoteTest {

    @Test
    void should_createTopicVote_when_validDataProvided() {
        // Given
        UUID topicId = UUID.randomUUID();
        UUID partnerId = UUID.randomUUID();
        int voteValue = 5;
        int voteWeight = 3;  // GOLD partnership

        // When
        TopicVote vote = TopicVote.builder()
                .topicId(topicId)
                .partnerId(partnerId)
                .voteValue(voteValue)
                .voteWeight(voteWeight)
                .votedAt(Instant.now())
                .build();

        // Then
        assertThat(vote.getTopicId()).isEqualTo(topicId);
        assertThat(vote.getPartnerId()).isEqualTo(partnerId);
        assertThat(vote.getVoteValue()).isEqualTo(5);
        assertThat(vote.getVoteWeight()).isEqualTo(3);
        assertThat(vote.getVotedAt()).isNotNull();
    }

    @Test
    void should_applyVoteWeight_when_calculatingVoteValue() {
        // Test vote weight based on partnership levels

        // Given - BRONZE partner (weight 1)
        TopicVote bronzeVote = TopicVote.builder()
                .topicId(UUID.randomUUID())
                .partnerId(UUID.randomUUID())
                .voteValue(5)
                .voteWeight(1)  // BRONZE = 1
                .build();

        // Then
        assertThat(bronzeVote.getVoteWeight())
                .as("BRONZE partnership should have weight 1")
                .isEqualTo(1);

        // Given - SILVER partner (weight 2)
        TopicVote silverVote = TopicVote.builder()
                .topicId(UUID.randomUUID())
                .partnerId(UUID.randomUUID())
                .voteValue(4)
                .voteWeight(2)  // SILVER = 2
                .build();

        // Then
        assertThat(silverVote.getVoteWeight())
                .as("SILVER partnership should have weight 2")
                .isEqualTo(2);

        // Given - GOLD partner (weight 3)
        TopicVote goldVote = TopicVote.builder()
                .topicId(UUID.randomUUID())
                .partnerId(UUID.randomUUID())
                .voteValue(3)
                .voteWeight(3)  // GOLD = 3
                .build();

        // Then
        assertThat(goldVote.getVoteWeight())
                .as("GOLD partnership should have weight 3")
                .isEqualTo(3);

        // Given - PLATINUM partner (weight 4)
        TopicVote platinumVote = TopicVote.builder()
                .topicId(UUID.randomUUID())
                .partnerId(UUID.randomUUID())
                .voteValue(5)
                .voteWeight(4)  // PLATINUM = 4
                .build();

        // Then
        assertThat(platinumVote.getVoteWeight())
                .as("PLATINUM partnership should have weight 4")
                .isEqualTo(4);

        // Given - STRATEGIC partner (weight 5)
        TopicVote strategicVote = TopicVote.builder()
                .topicId(UUID.randomUUID())
                .partnerId(UUID.randomUUID())
                .voteValue(5)
                .voteWeight(5)  // STRATEGIC = 5
                .build();

        // Then
        assertThat(strategicVote.getVoteWeight())
                .as("STRATEGIC partnership should have weight 5")
                .isEqualTo(5);
    }

    @Test
    void should_throwValidationException_when_voteValueOutOfRange() {
        // Test vote value < 1
        assertThatThrownBy(() ->
                TopicVote.builder()
                        .topicId(UUID.randomUUID())
                        .partnerId(UUID.randomUUID())
                        .voteValue(0)  // Invalid: less than 1
                        .voteWeight(3)
                        .build()
                        .validateVoteValue()
        )
                .as("Vote value must be at least 1")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Vote value must be between 1 and 5");

        // Test vote value > 5
        assertThatThrownBy(() ->
                TopicVote.builder()
                        .topicId(UUID.randomUUID())
                        .partnerId(UUID.randomUUID())
                        .voteValue(6)  // Invalid: greater than 5
                        .voteWeight(3)
                        .build()
                        .validateVoteValue()
        )
                .as("Vote value must not exceed 5")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Vote value must be between 1 and 5");
    }

    @Test
    void should_acceptValidVoteValues_when_within1to5Range() {
        // Test all valid vote values (1-5)
        for (int voteValue = 1; voteValue <= 5; voteValue++) {
            final int value = voteValue;

            // When/Then - should not throw exception
            assertThatCode(() ->
                    TopicVote.builder()
                            .topicId(UUID.randomUUID())
                            .partnerId(UUID.randomUUID())
                            .voteValue(value)
                            .voteWeight(3)
                            .build()
                            .validateVoteValue()
            )
                    .as("Vote value " + value + " should be valid")
                    .doesNotThrowAnyException();
        }
    }

    @Test
    void should_requireTopicId_when_creating() {
        // Given/When/Then
        assertThatThrownBy(() ->
                TopicVote.builder()
                        .topicId(null)  // Missing required field
                        .partnerId(UUID.randomUUID())
                        .voteValue(5)
                        .voteWeight(3)
                        .build()
                        .validateTopicId()
        )
                .as("Topic ID is required")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Topic ID is required");
    }

    @Test
    void should_requirePartnerId_when_creating() {
        // Given/When/Then
        assertThatThrownBy(() ->
                TopicVote.builder()
                        .topicId(UUID.randomUUID())
                        .partnerId(null)  // Missing required field
                        .voteValue(5)
                        .voteWeight(3)
                        .build()
                        .validatePartnerId()
        )
                .as("Partner ID is required")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Partner ID is required");
    }

    @Test
    void should_requireVoteWeight_when_creating() {
        // Given/When/Then
        assertThatThrownBy(() ->
                TopicVote.builder()
                        .topicId(UUID.randomUUID())
                        .partnerId(UUID.randomUUID())
                        .voteValue(5)
                        .voteWeight(null)  // Missing required field
                        .build()
                        .validateVoteWeight()
        )
                .as("Vote weight is required")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Vote weight is required");
    }

    @Test
    void should_validateVoteWeight_when_outOfRange() {
        // Test vote weight < 1
        assertThatThrownBy(() ->
                TopicVote.builder()
                        .topicId(UUID.randomUUID())
                        .partnerId(UUID.randomUUID())
                        .voteValue(5)
                        .voteWeight(0)  // Invalid: less than 1
                        .build()
                        .validateVoteWeight()
        )
                .as("Vote weight must be at least 1")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Vote weight must be between 1 and 5");

        // Test vote weight > 5
        assertThatThrownBy(() ->
                TopicVote.builder()
                        .topicId(UUID.randomUUID())
                        .partnerId(UUID.randomUUID())
                        .voteValue(5)
                        .voteWeight(6)  // Invalid: greater than 5
                        .build()
                        .validateVoteWeight()
        )
                .as("Vote weight must not exceed 5")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Vote weight must be between 1 and 5");
    }
}
