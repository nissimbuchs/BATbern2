package ch.batbern.partners.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for TopicSuggestion entity domain logic.
 *
 * Tests suggestion validation, status transitions,
 * and field length constraints.
 */
class TopicSuggestionTest {

    @Test
    void should_createTopicSuggestion_when_validDataProvided() {
        // Given
        UUID partnerId = UUID.randomUUID();
        String suggestedTopic = "Sustainable Architecture in Swiss Alpine Regions";
        String description = "This topic would explore sustainable building practices...";
        String businessJustification = "High interest from clients in eco-friendly construction";

        // When
        TopicSuggestion suggestion = TopicSuggestion.builder()
                .partnerId(partnerId)
                .suggestedTopic(suggestedTopic)
                .description(description)
                .businessJustification(businessJustification)
                .status(SuggestionStatus.SUBMITTED)
                .suggestedAt(Instant.now())
                .build();

        // Then
        assertThat(suggestion.getPartnerId()).isEqualTo(partnerId);
        assertThat(suggestion.getSuggestedTopic()).isEqualTo(suggestedTopic);
        assertThat(suggestion.getDescription()).isEqualTo(description);
        assertThat(suggestion.getBusinessJustification()).isEqualTo(businessJustification);
        assertThat(suggestion.getStatus()).isEqualTo(SuggestionStatus.SUBMITTED);
        assertThat(suggestion.getSuggestedAt()).isNotNull();
    }

    @Test
    void should_setSuggestionStatusToSubmitted_when_initiallyCreated() {
        // Given/When
        TopicSuggestion suggestion = TopicSuggestion.builder()
                .partnerId(UUID.randomUUID())
                .suggestedTopic("New Topic")
                .description("Description")
                .businessJustification("Justification")
                .status(SuggestionStatus.SUBMITTED)
                .suggestedAt(Instant.now())
                .build();

        // Then
        assertThat(suggestion.getStatus())
                .as("Initial status should be SUBMITTED")
                .isEqualTo(SuggestionStatus.SUBMITTED);
    }

    @Test
    void should_throwValidationException_when_suggestionTitleExceedsMaxLength() {
        // Given - 501 characters (exceeds 500 max)
        String longTitle = "A".repeat(501);

        // When/Then
        assertThatThrownBy(() ->
                TopicSuggestion.builder()
                        .partnerId(UUID.randomUUID())
                        .suggestedTopic(longTitle)
                        .description("Description")
                        .businessJustification("Justification")
                        .status(SuggestionStatus.SUBMITTED)
                        .build()
                        .validateSuggestedTopic()
        )
                .as("Suggested topic must not exceed 500 characters")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Suggested topic must not exceed 500 characters");
    }

    @Test
    void should_throwValidationException_when_descriptionExceedsMaxLength() {
        // Given - 2001 characters (exceeds 2000 max)
        String longDescription = "A".repeat(2001);

        // When/Then
        assertThatThrownBy(() ->
                TopicSuggestion.builder()
                        .partnerId(UUID.randomUUID())
                        .suggestedTopic("Valid Topic")
                        .description(longDescription)
                        .businessJustification("Justification")
                        .status(SuggestionStatus.SUBMITTED)
                        .build()
                        .validateDescription()
        )
                .as("Description must not exceed 2000 characters")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Description must not exceed 2000 characters");
    }

    @Test
    void should_throwValidationException_when_businessJustificationExceedsMaxLength() {
        // Given - 1001 characters (exceeds 1000 max)
        String longJustification = "A".repeat(1001);

        // When/Then
        assertThatThrownBy(() ->
                TopicSuggestion.builder()
                        .partnerId(UUID.randomUUID())
                        .suggestedTopic("Valid Topic")
                        .description("Valid Description")
                        .businessJustification(longJustification)
                        .status(SuggestionStatus.SUBMITTED)
                        .build()
                        .validateBusinessJustification()
        )
                .as("Business justification must not exceed 1000 characters")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Business justification must not exceed 1000 characters");
    }

    @Test
    void should_acceptValidFieldLengths_when_withinLimits() {
        // Given
        String validTopic = "A".repeat(500);  // Max length
        String validDescription = "B".repeat(2000);  // Max length
        String validJustification = "C".repeat(1000);  // Max length

        // When/Then - should not throw exception
        assertThatCode(() -> {
            TopicSuggestion suggestion = TopicSuggestion.builder()
                    .partnerId(UUID.randomUUID())
                    .suggestedTopic(validTopic)
                    .description(validDescription)
                    .businessJustification(validJustification)
                    .status(SuggestionStatus.SUBMITTED)
                    .suggestedAt(Instant.now())
                    .build();

            suggestion.validateSuggestedTopic();
            suggestion.validateDescription();
            suggestion.validateBusinessJustification();
        })
                .as("Valid field lengths should not throw exception")
                .doesNotThrowAnyException();
    }

    @Test
    void should_requirePartnerId_when_creating() {
        // Given/When/Then
        assertThatThrownBy(() ->
                TopicSuggestion.builder()
                        .partnerId(null)  // Missing required field
                        .suggestedTopic("Topic")
                        .description("Description")
                        .businessJustification("Justification")
                        .status(SuggestionStatus.SUBMITTED)
                        .build()
                        .validatePartnerId()
        )
                .as("Partner ID is required")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Partner ID is required");
    }

    @Test
    void should_requireSuggestedTopic_when_creating() {
        // Given/When/Then
        assertThatThrownBy(() ->
                TopicSuggestion.builder()
                        .partnerId(UUID.randomUUID())
                        .suggestedTopic(null)  // Missing required field
                        .description("Description")
                        .businessJustification("Justification")
                        .status(SuggestionStatus.SUBMITTED)
                        .build()
                        .validateSuggestedTopic()
        )
                .as("Suggested topic is required")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Suggested topic is required");
    }

    @Test
    void should_requireDescription_when_creating() {
        // Given/When/Then
        assertThatThrownBy(() ->
                TopicSuggestion.builder()
                        .partnerId(UUID.randomUUID())
                        .suggestedTopic("Topic")
                        .description(null)  // Missing required field
                        .businessJustification("Justification")
                        .status(SuggestionStatus.SUBMITTED)
                        .build()
                        .validateDescription()
        )
                .as("Description is required")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Description is required");
    }

    @Test
    void should_requireStatus_when_creating() {
        // Given/When/Then
        assertThatThrownBy(() ->
                TopicSuggestion.builder()
                        .partnerId(UUID.randomUUID())
                        .suggestedTopic("Topic")
                        .description("Description")
                        .businessJustification("Justification")
                        .status(null)  // Missing required field
                        .build()
                        .validateStatus()
        )
                .as("Status is required")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Status is required");
    }

    @Test
    void should_allowNullBusinessJustification_when_notProvided() {
        // Given/When
        TopicSuggestion suggestion = TopicSuggestion.builder()
                .partnerId(UUID.randomUUID())
                .suggestedTopic("Topic")
                .description("Description")
                .businessJustification(null)  // Optional field
                .status(SuggestionStatus.SUBMITTED)
                .suggestedAt(Instant.now())
                .build();

        // Then
        assertThat(suggestion.getBusinessJustification()).isNull();
    }

    @Test
    void should_trackReviewTimestamp_when_suggestionReviewed() {
        // Given
        TopicSuggestion suggestion = TopicSuggestion.builder()
                .partnerId(UUID.randomUUID())
                .suggestedTopic("Topic")
                .description("Description")
                .businessJustification("Justification")
                .status(SuggestionStatus.SUBMITTED)
                .suggestedAt(Instant.now())
                .build();

        UUID reviewerId = UUID.randomUUID();
        Instant reviewTime = Instant.now();

        // When
        suggestion.setStatus(SuggestionStatus.ACCEPTED);
        suggestion.setReviewedAt(reviewTime);
        suggestion.setReviewedBy(reviewerId);

        // Then
        assertThat(suggestion.getStatus()).isEqualTo(SuggestionStatus.ACCEPTED);
        assertThat(suggestion.getReviewedAt()).isEqualTo(reviewTime);
        assertThat(suggestion.getReviewedBy()).isEqualTo(reviewerId);
    }

    @Test
    void should_supportAllSuggestionStatuses_when_created() {
        // Test all valid status values
        for (SuggestionStatus status : SuggestionStatus.values()) {
            // When/Then - should not throw exception
            assertThatCode(() ->
                    TopicSuggestion.builder()
                            .partnerId(UUID.randomUUID())
                            .suggestedTopic("Topic")
                            .description("Description")
                            .businessJustification("Justification")
                            .status(status)
                            .suggestedAt(Instant.now())
                            .build()
            )
                    .as("Status " + status + " should be valid")
                    .doesNotThrowAnyException();
        }
    }
}
