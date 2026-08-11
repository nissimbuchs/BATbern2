package ch.batbern.shared.api;

import ch.batbern.shared.exception.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TDD Tests for SortParser (AC3)
 *
 * RED Phase: Tests written FIRST, before implementation
 *
 * Sort Parser parses sort strings into SortCriteria objects.
 * Syntax: "-field" for descending, "+field" or "field" for ascending
 * Multiple fields separated by comma: "-votes,+createdAt"
 */
@DisplayName("SortParser Unit Tests")
class SortParserTest {

    // ========================================
    // Test 3.1: Ascending Sort
    // ========================================

    @Test
    @DisplayName("should_parseAscending_when_fieldNameProvided")
    void should_parseAscending_when_fieldNameProvided() {
        // Given: Simple field name (defaults to ascending)
        String sortStr = "createdAt";

        // When: Parsing sort
        List<SortCriteria> sortList = SortParser.parse(sortStr);

        // Then: Should create ascending sort
        assertThat(sortList).hasSize(1);
        assertThat(sortList.get(0).getField()).isEqualTo("createdAt");
        assertThat(sortList.get(0).getDirection()).isEqualTo(SortDirection.ASC);
    }

    @Test
    @DisplayName("should_parseAscending_when_plusPrefixProvided")
    void should_parseAscending_when_plusPrefixProvided() {
        // Given: Explicit ascending with + prefix
        String sortStr = "+createdAt";

        // When: Parsing sort
        List<SortCriteria> sortList = SortParser.parse(sortStr);

        // Then: Should create ascending sort
        assertThat(sortList).hasSize(1);
        assertThat(sortList.get(0).getField()).isEqualTo("createdAt");
        assertThat(sortList.get(0).getDirection()).isEqualTo(SortDirection.ASC);
    }

    // ========================================
    // Test 3.2: Descending Sort
    // ========================================

    @Test
    @DisplayName("should_parseDescending_when_minusPrefixProvided")
    void should_parseDescending_when_minusPrefixProvided() {
        // Given: Descending with - prefix
        String sortStr = "-votes";

        // When: Parsing sort
        List<SortCriteria> sortList = SortParser.parse(sortStr);

        // Then: Should create descending sort
        assertThat(sortList).hasSize(1);
        assertThat(sortList.get(0).getField()).isEqualTo("votes");
        assertThat(sortList.get(0).getDirection()).isEqualTo(SortDirection.DESC);
    }

    // ========================================
    // Test 3.3: Multiple Fields
    // ========================================

    @Test
    @DisplayName("should_parseMultipleFields_when_commaSeparatedProvided")
    void should_parseMultipleFields_when_commaSeparatedProvided() {
        // Given: Multiple fields with comma separator
        String sortStr = "-votes,+createdAt";

        // When: Parsing sort
        List<SortCriteria> sortList = SortParser.parse(sortStr);

        // Then: Should create multiple sort criteria in order
        assertThat(sortList).hasSize(2);

        // First: votes descending
        assertThat(sortList.get(0).getField()).isEqualTo("votes");
        assertThat(sortList.get(0).getDirection()).isEqualTo(SortDirection.DESC);

        // Second: createdAt ascending
        assertThat(sortList.get(1).getField()).isEqualTo("createdAt");
        assertThat(sortList.get(1).getDirection()).isEqualTo(SortDirection.ASC);
    }

    @Test
    @DisplayName("should_parseThreeFields_when_multipleFieldsProvided")
    void should_parseThreeFields_when_multipleFieldsProvided() {
        // Given: Three fields
        String sortStr = "-votes,title,+createdAt";

        // When: Parsing sort
        List<SortCriteria> sortList = SortParser.parse(sortStr);

        // Then: Should parse all three in order
        assertThat(sortList).hasSize(3);
        assertThat(sortList.get(0).getField()).isEqualTo("votes");
        assertThat(sortList.get(0).getDirection()).isEqualTo(SortDirection.DESC);
        assertThat(sortList.get(1).getField()).isEqualTo("title");
        assertThat(sortList.get(1).getDirection()).isEqualTo(SortDirection.ASC);
        assertThat(sortList.get(2).getField()).isEqualTo("createdAt");
        assertThat(sortList.get(2).getDirection()).isEqualTo(SortDirection.ASC);
    }

    // ========================================
    // Test 3.4: Error Cases
    // ========================================

    @Test
    @DisplayName("should_throwValidationError_when_invalidSortFormatProvided")
    void should_throwValidationError_when_invalidSortFormatProvided() {
        // Given: Invalid format (multiple prefix symbols)
        String invalidSort = "++field";

        // When/Then: Should throw ValidationException
        assertThatThrownBy(() -> SortParser.parse(invalidSort))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Invalid sort format");
    }

    @Test
    @DisplayName("should_throwValidationError_when_emptyFieldProvided")
    void should_throwValidationError_when_emptyFieldProvided() {
        // Given: Empty field (just prefix)
        String invalidSort = "-";

        // When/Then: Should throw ValidationException
        assertThatThrownBy(() -> SortParser.parse(invalidSort))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Empty field name");
    }

    @Test
    @DisplayName("should_returnEmptyList_when_nullSortProvided")
    void should_returnEmptyList_when_nullSortProvided() {
        // When: Parsing null sort
        List<SortCriteria> sortList = SortParser.parse(null);

        // Then: Should return empty list (no sorting)
        assertThat(sortList).isEmpty();
    }

    @Test
    @DisplayName("should_returnEmptyList_when_emptyStringSortProvided")
    void should_returnEmptyList_when_emptyStringSortProvided() {
        // When: Parsing empty string sort
        List<SortCriteria> sortList = SortParser.parse("");

        // Then: Should return empty list (no sorting)
        assertThat(sortList).isEmpty();
    }

    // ========================================
    // Nested Field Support
    // ========================================

    @Test
    @DisplayName("should_parseNestedField_when_dotNotationProvided")
    void should_parseNestedField_when_dotNotationProvided() {
        // Given: Nested field with dot notation
        String sortStr = "-author.name";

        // When: Parsing sort
        List<SortCriteria> sortList = SortParser.parse(sortStr);

        // Then: Should support nested field names
        assertThat(sortList).hasSize(1);
        assertThat(sortList.get(0).getField()).isEqualTo("author.name");
        assertThat(sortList.get(0).getDirection()).isEqualTo(SortDirection.DESC);
    }

    // ========================================
    // Whitespace Handling
    // ========================================

    @Test
    @DisplayName("should_trimWhitespace_when_sortWithSpacesProvided")
    void should_trimWhitespace_when_sortWithSpacesProvided() {
        // Given: Sort string with whitespace
        String sortStr = " -votes , +createdAt ";

        // When: Parsing sort
        List<SortCriteria> sortList = SortParser.parse(sortStr);

        // Then: Should handle whitespace correctly
        assertThat(sortList).hasSize(2);
        assertThat(sortList.get(0).getField()).isEqualTo("votes");
        assertThat(sortList.get(1).getField()).isEqualTo("createdAt");
    }

    // ========================================
    // Preserve Order
    // ========================================

    @Test
    @DisplayName("should_preserveOrder_when_multipleFieldsProvided")
    void should_preserveOrder_when_multipleFieldsProvided() {
        // Given: Specific field order
        String sortStr = "status,-priority,+id";

        // When: Parsing sort
        List<SortCriteria> sortList = SortParser.parse(sortStr);

        // Then: Order should be preserved
        assertThat(sortList.get(0).getField()).isEqualTo("status");
        assertThat(sortList.get(1).getField()).isEqualTo("priority");
        assertThat(sortList.get(2).getField()).isEqualTo("id");
    }

    // ========================================
    // toString Support for SQL Generation
    // ========================================

    @Test
    @DisplayName("should_generateSqlOrderBy_when_toStringSqlCalled")
    void should_generateSqlOrderBy_when_toStringSqlCalled() {
        // Given: Sort criteria
        String sortStr = "-votes,+createdAt";
        List<SortCriteria> sortList = SortParser.parse(sortStr);

        // When: Converting to SQL ORDER BY clause
        String sql = SortParser.toSql(sortList);

        // Then: Should generate proper SQL
        assertThat(sql).isEqualTo("votes DESC, createdAt ASC");
    }

    @Test
    @DisplayName("should_returnEmptyString_when_toSqlCalledWithEmptyList")
    void should_returnEmptyString_when_toSqlCalledWithEmptyList() {
        // When: Converting empty list to SQL
        String sql = SortParser.toSql(List.of());

        // Then: Should return empty string
        assertThat(sql).isEmpty();
    }

    // ========================================
    // Issue #903: reject unsafe field names at the parse boundary
    //
    // Spring Data JPA's QueryUtils.checkSortExpression rejects ANY \p{Punct}
    // character in a sort property with InvalidDataAccessApiUsageException —
    // BEFORE the property is resolved. That is an unhandled 500 rather than a
    // 400, and it fires even when the field name itself is valid (e.g. "date%").
    // Validating here turns the whole class into a 400 at the edge.
    // ========================================

    @Test
    @DisplayName("should_throwValidationError_when_fieldContainsPercent")
    void should_throwValidationError_when_fieldContainsPercent() {
        // Given: the exact value the weekly ZAP scan reports (decoded form)
        String unsafeSort = "ZAP%n%s%n%s";

        // When/Then: rejected at the parse boundary, not deep inside Spring Data
        assertThatThrownBy(() -> SortParser.parse(unsafeSort))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Invalid field name in sort");
    }

    @Test
    @DisplayName("should_throwValidationError_when_validFieldHasTrailingPercent")
    void should_throwValidationError_when_validFieldHasTrailingPercent() {
        // Given: a REAL field name with a single stray percent appended.
        // This is the case that proves the trigger is the punctuation, not the
        // field name — "date" resolves fine, "date%" produced a 500 in prod.
        assertThatThrownBy(() -> SortParser.parse("date%"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Invalid field name in sort");
    }

    @Test
    @DisplayName("should_throwValidationError_when_fieldContainsSqlPunctuation")
    void should_throwValidationError_when_fieldContainsSqlPunctuation() {
        // Given: punctuation that Spring Data's guard exists to stop
        String[] unsafe = {
            "date;DROP TABLE events",
            "count(*)",
            "date desc",
            "date'",
            "date\"",
            "date-title",
            "date/title",
            "date|title",
            "date%20n"
        };

        // When/Then: every one is a 400-shaped ValidationException
        for (String sort : unsafe) {
            assertThatThrownBy(() -> SortParser.parse(sort))
                    .as("sort=%s must be rejected", sort)
                    .isInstanceOf(ValidationException.class);
        }
    }

    @Test
    @DisplayName("should_acceptValidIdentifiers_when_legitimateFieldNamesProvided")
    void should_acceptValidIdentifiers_when_legitimateFieldNamesProvided() {
        // Given: every field shape the platform actually sorts by today.
        // Guards against the validation being too strict for real callers.
        String[] valid = {
            "date", "-date", "+date",
            "eventNumber", "attendeeCount", "currentAttendeeCount",
            "createdAt", "-votes", "title",
            "last_name", "_internal",
            "author.name", "-event.date", "a.b.c"
        };

        // When/Then: all parse without throwing
        for (String sort : valid) {
            assertThat(SortParser.parse(sort))
                    .as("sort=%s must be accepted", sort)
                    .hasSize(1);
        }
    }

    @Test
    @DisplayName("should_rejectUnsafeField_when_mixedWithValidFieldsInMultiSort")
    void should_rejectUnsafeField_when_mixedWithValidFieldsInMultiSort() {
        // Given: one bad field hidden among good ones
        // When/Then: the whole sort is rejected — no partial application
        assertThatThrownBy(() -> SortParser.parse("-date,ZAP%n,+title"))
                .isInstanceOf(ValidationException.class);
    }
}
