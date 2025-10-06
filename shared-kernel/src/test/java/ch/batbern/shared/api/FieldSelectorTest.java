package ch.batbern.shared.api;

import ch.batbern.shared.exception.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.*;

/**
 * TDD Tests for FieldSelector (AC5)
 *
 * FieldSelector parses fields parameter for sparse fieldsets.
 * Syntax: "id,title,votes" or "id,author.name,createdAt"
 */
@DisplayName("FieldSelector Unit Tests")
class FieldSelectorTest {

    @Test
    @DisplayName("should_parseFieldList_when_commaSeparatedProvided")
    void should_parseFieldList_when_commaSeparatedProvided() {
        // Given: Comma-separated field list
        String fieldsStr = "id,title,votes";

        // When: Parsing fields
        Set<String> fields = FieldSelector.parse(fieldsStr);

        // Then: Should return set of fields
        assertThat(fields).containsExactlyInAnyOrder("id", "title", "votes");
    }

    @Test
    @DisplayName("should_returnAllFields_when_fieldsParamOmitted")
    void should_returnAllFields_when_fieldsParamOmitted() {
        // When: Parsing null fields
        Set<String> fields = FieldSelector.parse(null);

        // Then: Should return null (indicating all fields)
        assertThat(fields).isNull();
    }

    @Test
    @DisplayName("should_supportNestedFields_when_dotNotationProvided")
    void should_supportNestedFields_when_dotNotationProvided() {
        // Given: Nested field names
        String fieldsStr = "id,author.name,event.venue.name";

        // When: Parsing fields
        Set<String> fields = FieldSelector.parse(fieldsStr);

        // Then: Should support dot notation
        assertThat(fields).contains("author.name", "event.venue.name");
    }

    @Test
    @DisplayName("should_trimWhitespace_when_fieldsHaveSpaces")
    void should_trimWhitespace_when_fieldsHaveSpaces() {
        // Given: Fields with whitespace
        String fieldsStr = " id , title , votes ";

        // When: Parsing fields
        Set<String> fields = FieldSelector.parse(fieldsStr);

        // Then: Should trim whitespace
        assertThat(fields).containsExactlyInAnyOrder("id", "title", "votes");
    }

    @Test
    @DisplayName("should_removeDuplicates_when_sameFieldRepeated")
    void should_removeDuplicates_when_sameFieldRepeated() {
        // Given: Duplicate fields
        String fieldsStr = "id,title,id,votes,title";

        // When: Parsing fields
        Set<String> fields = FieldSelector.parse(fieldsStr);

        // Then: Should deduplicate
        assertThat(fields).containsExactlyInAnyOrder("id", "title", "votes");
    }

    @Test
    @DisplayName("should_returnEmptySet_when_emptyStringProvided")
    void should_returnEmptySet_when_emptyStringProvided() {
        // When: Parsing empty string
        Set<String> fields = FieldSelector.parse("");

        // Then: Should return null (same as omitted)
        assertThat(fields).isNull();
    }

    @Test
    @DisplayName("should_throwValidationError_when_invalidFieldNameProvided")
    void should_throwValidationError_when_invalidFieldNameProvided() {
        // Given: Field with invalid characters
        String invalidFields = "id,title,invalid field!";

        // When/Then: Should throw ValidationException
        assertThatThrownBy(() -> FieldSelector.parse(invalidFields))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Invalid field name");
    }
}
