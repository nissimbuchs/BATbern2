package ch.batbern.shared.api;

import ch.batbern.shared.exception.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * TDD Tests for FilterParser (AC2)
 *
 * RED Phase: Tests written FIRST, before implementation
 *
 * FilterParser parses MongoDB-style JSON filter syntax into FilterCriteria objects
 * that can be used to build database queries (JPA Specification or SQL WHERE clauses).
 *
 * Supported operators:
 * - Comparison: $eq, $ne, $gt, $gte, $lt, $lte
 * - Logical: $and, $or, $not
 * - String: $contains, $startsWith, $endsWith
 * - Array: $in, $nin, $size
 * - Null: $isNull
 */
@DisplayName("FilterParser Unit Tests")
class FilterParserTest {

    // ========================================
    // Test 2.1: Basic Equality Filters
    // ========================================

    @Test
    @DisplayName("should_parseBasicEquality_when_simpleFilterProvided")
    void should_parseBasicEquality_when_simpleFilterProvided() {
        // Given: Simple equality filter
        String filterJson = "{\"status\":\"published\"}";

        // When: Parsing filter
        FilterCriteria criteria = FilterParser.parse(filterJson);

        // Then: Should create equality condition
        assertThat(criteria).isNotNull();
        assertThat(criteria.getField()).isEqualTo("status");
        assertThat(criteria.getOperator()).isEqualTo(FilterOperator.EQUALS);
        assertThat(criteria.getValue()).isEqualTo("published");
    }

    @Test
    @DisplayName("should_parseMultipleFields_when_implicitAndProvided")
    void should_parseMultipleFields_when_implicitAndProvided() {
        // Given: Multiple fields (implicit AND)
        String filterJson = "{\"status\":\"published\",\"category\":\"tech\"}";

        // When: Parsing filter
        FilterCriteria criteria = FilterParser.parse(filterJson);

        // Then: Should create AND condition with multiple criteria
        assertThat(criteria.getOperator()).isEqualTo(FilterOperator.AND);
        assertThat(criteria.getChildren()).hasSize(2);
    }

    // ========================================
    // Test 2.2: Comparison Operators
    // ========================================

    @Test
    @DisplayName("should_parseGreaterThan_when_gtOperatorProvided")
    void should_parseGreaterThan_when_gtOperatorProvided() {
        // Given: Greater than operator
        String filterJson = "{\"votes\":{\"$gt\":10}}";

        // When: Parsing filter
        FilterCriteria criteria = FilterParser.parse(filterJson);

        // Then: Should create GT condition
        assertThat(criteria.getField()).isEqualTo("votes");
        assertThat(criteria.getOperator()).isEqualTo(FilterOperator.GREATER_THAN);
        assertThat(criteria.getValue()).isEqualTo(10);
    }

    @Test
    @DisplayName("should_parseGreaterThanOrEqual_when_gteOperatorProvided")
    void should_parseGreaterThanOrEqual_when_gteOperatorProvided() {
        // Given: Greater than or equal operator
        String filterJson = "{\"votes\":{\"$gte\":10}}";

        // When: Parsing filter
        FilterCriteria criteria = FilterParser.parse(filterJson);

        // Then: Should create GTE condition
        assertThat(criteria.getOperator()).isEqualTo(FilterOperator.GREATER_THAN_OR_EQUAL);
        assertThat(criteria.getValue()).isEqualTo(10);
    }

    @Test
    @DisplayName("should_parseLessThan_when_ltOperatorProvided")
    void should_parseLessThan_when_ltOperatorProvided() {
        // Given: Less than operator
        String filterJson = "{\"votes\":{\"$lt\":100}}";

        // When: Parsing filter
        FilterCriteria criteria = FilterParser.parse(filterJson);

        // Then: Should create LT condition
        assertThat(criteria.getOperator()).isEqualTo(FilterOperator.LESS_THAN);
        assertThat(criteria.getValue()).isEqualTo(100);
    }

    @Test
    @DisplayName("should_parseLessThanOrEqual_when_lteOperatorProvided")
    void should_parseLessThanOrEqual_when_lteOperatorProvided() {
        // Given: Less than or equal operator
        String filterJson = "{\"votes\":{\"$lte\":100}}";

        // When: Parsing filter
        FilterCriteria criteria = FilterParser.parse(filterJson);

        // Then: Should create LTE condition
        assertThat(criteria.getOperator()).isEqualTo(FilterOperator.LESS_THAN_OR_EQUAL);
        assertThat(criteria.getValue()).isEqualTo(100);
    }

    @Test
    @DisplayName("should_parseNotEqual_when_neOperatorProvided")
    void should_parseNotEqual_when_neOperatorProvided() {
        // Given: Not equal operator
        String filterJson = "{\"status\":{\"$ne\":\"draft\"}}";

        // When: Parsing filter
        FilterCriteria criteria = FilterParser.parse(filterJson);

        // Then: Should create NE condition
        assertThat(criteria.getOperator()).isEqualTo(FilterOperator.NOT_EQUALS);
        assertThat(criteria.getValue()).isEqualTo("draft");
    }

    // ========================================
    // Test 2.3: Logical Operators
    // ========================================

    @Test
    @DisplayName("should_parseAndOperator_when_explicitAndProvided")
    void should_parseAndOperator_when_explicitAndProvided() {
        // Given: Explicit AND operator
        String filterJson = "{\"$and\":[{\"status\":\"published\"},{\"votes\":{\"$gte\":10}}]}";

        // When: Parsing filter
        FilterCriteria criteria = FilterParser.parse(filterJson);

        // Then: Should create AND condition with children
        assertThat(criteria.getOperator()).isEqualTo(FilterOperator.AND);
        assertThat(criteria.getChildren()).hasSize(2);
        assertThat(criteria.getChildren().get(0).getField()).isEqualTo("status");
        assertThat(criteria.getChildren().get(1).getField()).isEqualTo("votes");
    }

    @Test
    @DisplayName("should_parseOrOperator_when_orProvided")
    void should_parseOrOperator_when_orProvided() {
        // Given: OR operator
        String filterJson = "{\"$or\":[{\"status\":\"published\"},{\"status\":\"featured\"}]}";

        // When: Parsing filter
        FilterCriteria criteria = FilterParser.parse(filterJson);

        // Then: Should create OR condition with children
        assertThat(criteria.getOperator()).isEqualTo(FilterOperator.OR);
        assertThat(criteria.getChildren()).hasSize(2);
    }

    @Test
    @DisplayName("should_parseNotOperator_when_notProvided")
    void should_parseNotOperator_when_notProvided() {
        // Given: NOT operator
        String filterJson = "{\"$not\":{\"status\":\"draft\"}}";

        // When: Parsing filter
        FilterCriteria criteria = FilterParser.parse(filterJson);

        // Then: Should create NOT condition
        assertThat(criteria.getOperator()).isEqualTo(FilterOperator.NOT);
        assertThat(criteria.getChildren()).hasSize(1);
        assertThat(criteria.getChildren().get(0).getField()).isEqualTo("status");
    }

    // ========================================
    // Test 2.4: String Operators
    // ========================================

    @Test
    @DisplayName("should_parseContains_when_containsOperatorProvided")
    void should_parseContains_when_containsOperatorProvided() {
        // Given: Contains operator
        String filterJson = "{\"title\":{\"$contains\":\"API\"}}";

        // When: Parsing filter
        FilterCriteria criteria = FilterParser.parse(filterJson);

        // Then: Should create CONTAINS condition
        assertThat(criteria.getField()).isEqualTo("title");
        assertThat(criteria.getOperator()).isEqualTo(FilterOperator.CONTAINS);
        assertThat(criteria.getValue()).isEqualTo("API");
    }

    @Test
    @DisplayName("should_parseStartsWith_when_startsWithOperatorProvided")
    void should_parseStartsWith_when_startsWithOperatorProvided() {
        // Given: StartsWith operator
        String filterJson = "{\"title\":{\"$startsWith\":\"Introduction\"}}";

        // When: Parsing filter
        FilterCriteria criteria = FilterParser.parse(filterJson);

        // Then: Should create STARTS_WITH condition
        assertThat(criteria.getOperator()).isEqualTo(FilterOperator.STARTS_WITH);
        assertThat(criteria.getValue()).isEqualTo("Introduction");
    }

    @Test
    @DisplayName("should_parseEndsWith_when_endsWithOperatorProvided")
    void should_parseEndsWith_when_endsWithOperatorProvided() {
        // Given: EndsWith operator
        String filterJson = "{\"title\":{\"$endsWith\":\"Guide\"}}";

        // When: Parsing filter
        FilterCriteria criteria = FilterParser.parse(filterJson);

        // Then: Should create ENDS_WITH condition
        assertThat(criteria.getOperator()).isEqualTo(FilterOperator.ENDS_WITH);
        assertThat(criteria.getValue()).isEqualTo("Guide");
    }

    // ========================================
    // Test 2.5: Array Operators
    // ========================================

    @Test
    @DisplayName("should_parseInOperator_when_inProvided")
    void should_parseInOperator_when_inProvided() {
        // Given: IN operator with array of values
        String filterJson = "{\"status\":{\"$in\":[\"published\",\"featured\",\"trending\"]}}";

        // When: Parsing filter
        FilterCriteria criteria = FilterParser.parse(filterJson);

        // Then: Should create IN condition with list of values
        assertThat(criteria.getField()).isEqualTo("status");
        assertThat(criteria.getOperator()).isEqualTo(FilterOperator.IN);
        assertThat(criteria.getValue()).isInstanceOf(List.class);
        @SuppressWarnings("unchecked")
        List<String> values = (List<String>) criteria.getValue();
        assertThat(values).containsExactly("published", "featured", "trending");
    }

    @Test
    @DisplayName("should_parseNotInOperator_when_ninProvided")
    void should_parseNotInOperator_when_ninProvided() {
        // Given: NIN (not in) operator
        String filterJson = "{\"status\":{\"$nin\":[\"draft\",\"deleted\"]}}";

        // When: Parsing filter
        FilterCriteria criteria = FilterParser.parse(filterJson);

        // Then: Should create NOT_IN condition
        assertThat(criteria.getOperator()).isEqualTo(FilterOperator.NOT_IN);
        assertThat(criteria.getValue()).isInstanceOf(List.class);
    }

    // ========================================
    // Test 2.6: Error Cases
    // ========================================

    @Test
    @DisplayName("should_throwValidationError_when_invalidJsonProvided")
    void should_throwValidationError_when_invalidJsonProvided() {
        // Given: Invalid JSON syntax
        String invalidJson = "{invalid-json}";

        // When/Then: Should throw ValidationException
        assertThatThrownBy(() -> FilterParser.parse(invalidJson))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Invalid filter JSON");
    }

    @Test
    @DisplayName("should_throwValidationError_when_unknownOperatorProvided")
    void should_throwValidationError_when_unknownOperatorProvided() {
        // Given: Unknown operator
        String filterJson = "{\"field\":{\"$unknownOp\":\"value\"}}";

        // When/Then: Should throw ValidationException
        assertThatThrownBy(() -> FilterParser.parse(filterJson))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Unknown operator");
    }

    @Test
    @DisplayName("should_throwValidationError_when_emptyFilterProvided")
    void should_throwValidationError_when_emptyFilterProvided() {
        // Given: Empty filter
        String emptyFilter = "{}";

        // When/Then: Should throw ValidationException
        assertThatThrownBy(() -> FilterParser.parse(emptyFilter))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Filter cannot be empty");
    }

    @Test
    @DisplayName("should_returnNull_when_nullFilterProvided")
    void should_returnNull_when_nullFilterProvided() {
        // When: Parsing null filter
        FilterCriteria criteria = FilterParser.parse(null);

        // Then: Should return null (no filtering)
        assertThat(criteria).isNull();
    }

    @Test
    @DisplayName("should_returnNull_when_emptyStringFilterProvided")
    void should_returnNull_when_emptyStringFilterProvided() {
        // When: Parsing empty string filter
        FilterCriteria criteria = FilterParser.parse("");

        // Then: Should return null (no filtering)
        assertThat(criteria).isNull();
    }

    // ========================================
    // Complex Nested Filter Tests
    // ========================================

    @Test
    @DisplayName("should_parseNestedConditions_when_complexFilterProvided")
    void should_parseNestedConditions_when_complexFilterProvided() {
        // Given: Complex nested filter
        String filterJson = "{\"$and\":[{\"status\":\"published\"},{\"$or\":[{\"votes\":{\"$gte\":10}},{\"featured\":true}]}]}";

        // When: Parsing filter
        FilterCriteria criteria = FilterParser.parse(filterJson);

        // Then: Should create nested structure
        assertThat(criteria.getOperator()).isEqualTo(FilterOperator.AND);
        assertThat(criteria.getChildren()).hasSize(2);

        // First child: status = published
        assertThat(criteria.getChildren().get(0).getField()).isEqualTo("status");

        // Second child: OR condition
        assertThat(criteria.getChildren().get(1).getOperator()).isEqualTo(FilterOperator.OR);
        assertThat(criteria.getChildren().get(1).getChildren()).hasSize(2);
    }

    @Test
    @DisplayName("should_parseNestedFieldAccess_when_dotNotationProvided")
    void should_parseNestedFieldAccess_when_dotNotationProvided() {
        // Given: Nested field access with dot notation
        String filterJson = "{\"author.name\":\"John Doe\"}";

        // When: Parsing filter
        FilterCriteria criteria = FilterParser.parse(filterJson);

        // Then: Should support nested field names
        assertThat(criteria.getField()).isEqualTo("author.name");
        assertThat(criteria.getValue()).isEqualTo("John Doe");
    }

    @Test
    @DisplayName("should_handleBooleanValues_when_booleanProvided")
    void should_handleBooleanValues_when_booleanProvided() {
        // Given: Boolean value
        String filterJson = "{\"featured\":true}";

        // When: Parsing filter
        FilterCriteria criteria = FilterParser.parse(filterJson);

        // Then: Should parse boolean correctly
        assertThat(criteria.getValue()).isEqualTo(true);
    }

    @Test
    @DisplayName("should_handleNumericValues_when_numberProvided")
    void should_handleNumericValues_when_numberProvided() {
        // Given: Numeric value
        String filterJson = "{\"votes\":42}";

        // When: Parsing filter
        FilterCriteria criteria = FilterParser.parse(filterJson);

        // Then: Should parse number correctly
        assertThat(criteria.getValue()).isEqualTo(42);
    }

    @Test
    @DisplayName("should_handleNullCheck_when_isNullOperatorProvided")
    void should_handleNullCheck_when_isNullOperatorProvided() {
        // Given: Null check operator
        String filterJson = "{\"deletedAt\":{\"$isNull\":true}}";

        // When: Parsing filter
        FilterCriteria criteria = FilterParser.parse(filterJson);

        // Then: Should create IS_NULL condition
        assertThat(criteria.getField()).isEqualTo("deletedAt");
        assertThat(criteria.getOperator()).isEqualTo(FilterOperator.IS_NULL);
        assertThat(criteria.getValue()).isEqualTo(true);
    }
}
