package ch.batbern.shared.api;

import ch.batbern.shared.exception.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.*;

/**
 * TDD Tests for IncludeParser (AC6)
 *
 * IncludeParser parses include parameter for resource expansion.
 * Syntax: "author,comments,venue" or "author,event.venue"
 */
@DisplayName("IncludeParser Unit Tests")
class IncludeParserTest {

    @Test
    @DisplayName("should_parseIncludeList_when_commaSeparatedProvided")
    void should_parseIncludeList_when_commaSeparatedProvided() {
        // Given: Comma-separated include list
        String includeStr = "author,comments,venue";

        // When: Parsing includes
        Set<String> includes = IncludeParser.parse(includeStr);

        // Then: Should return set of relations
        assertThat(includes).containsExactlyInAnyOrder("author", "comments", "venue");
    }

    @Test
    @DisplayName("should_returnEmptySet_when_includeParamOmitted")
    void should_returnEmptySet_when_includeParamOmitted() {
        // When: Parsing null include
        Set<String> includes = IncludeParser.parse(null);

        // Then: Should return empty set
        assertThat(includes).isEmpty();
    }

    @Test
    @DisplayName("should_supportNestedIncludes_when_dotNotationProvided")
    void should_supportNestedIncludes_when_dotNotationProvided() {
        // Given: Nested includes
        String includeStr = "author,event.venue,event.speakers";

        // When: Parsing includes
        Set<String> includes = IncludeParser.parse(includeStr);

        // Then: Should support dot notation
        assertThat(includes).contains("author", "event.venue", "event.speakers");
    }

    @Test
    @DisplayName("should_trimWhitespace_when_includesHaveSpaces")
    void should_trimWhitespace_when_includesHaveSpaces() {
        // Given: Includes with whitespace
        String includeStr = " author , comments , venue ";

        // When: Parsing includes
        Set<String> includes = IncludeParser.parse(includeStr);

        // Then: Should trim whitespace
        assertThat(includes).containsExactlyInAnyOrder("author", "comments", "venue");
    }

    @Test
    @DisplayName("should_removeDuplicates_when_sameIncludeRepeated")
    void should_removeDuplicates_when_sameIncludeRepeated() {
        // Given: Duplicate includes
        String includeStr = "author,comments,author,venue";

        // When: Parsing includes
        Set<String> includes = IncludeParser.parse(includeStr);

        // Then: Should deduplicate
        assertThat(includes).containsExactlyInAnyOrder("author", "comments", "venue");
    }

    @Test
    @DisplayName("should_returnEmptySet_when_emptyStringProvided")
    void should_returnEmptySet_when_emptyStringProvided() {
        // When: Parsing empty string
        Set<String> includes = IncludeParser.parse("");

        // Then: Should return empty set
        assertThat(includes).isEmpty();
    }

    @Test
    @DisplayName("should_throwValidationError_when_invalidRelationNameProvided")
    void should_throwValidationError_when_invalidRelationNameProvided() {
        // Given: Invalid relation name
        String invalidInclude = "author,invalid relation!,comments";

        // When/Then: Should throw ValidationException
        assertThatThrownBy(() -> IncludeParser.parse(invalidInclude))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Invalid relation name");
    }
}
