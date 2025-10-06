package ch.batbern.shared.api;

import ch.batbern.shared.exception.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * TDD Tests for PaginationUtils (AC4, AC10)
 *
 * RED Phase: Tests written FIRST, before implementation
 *
 * PaginationUtils handles pagination parameter parsing and metadata generation.
 * Default values: page=1, limit=20, maxLimit=100
 */
@DisplayName("PaginationUtils Unit Tests")
class PaginationUtilsTest {

    // ========================================
    // Test 4.1: Parse Page and Limit
    // ========================================

    @Test
    @DisplayName("should_parsePageAndLimit_when_queryParamsProvided")
    void should_parsePageAndLimit_when_queryParamsProvided() {
        // Given: Page and limit parameters
        int page = 3;
        int limit = 25;

        // When: Parsing pagination params
        PaginationParams params = PaginationUtils.parseParams(page, limit);

        // Then: Should parse correctly
        assertThat(params.getPage()).isEqualTo(3);
        assertThat(params.getLimit()).isEqualTo(25);
    }

    // ========================================
    // Test 4.2: Default Values
    // ========================================

    @Test
    @DisplayName("should_useDefaults_when_paramsOmitted")
    void should_useDefaults_when_paramsOmitted() {
        // When: Parsing with null/default values
        PaginationParams params = PaginationUtils.parseParams(null, null);

        // Then: Should use defaults (page=1, limit=20)
        assertThat(params.getPage()).isEqualTo(1);
        assertThat(params.getLimit()).isEqualTo(20);
    }

    @Test
    @DisplayName("should_useDefaultLimit_when_limitOmitted")
    void should_useDefaultLimit_when_limitOmitted() {
        // When: Page provided but limit omitted
        PaginationParams params = PaginationUtils.parseParams(2, null);

        // Then: Should use default limit
        assertThat(params.getPage()).isEqualTo(2);
        assertThat(params.getLimit()).isEqualTo(20);
    }

    @Test
    @DisplayName("should_useDefaultPage_when_pageOmitted")
    void should_useDefaultPage_when_pageOmitted() {
        // When: Limit provided but page omitted
        PaginationParams params = PaginationUtils.parseParams(null, 50);

        // Then: Should use default page
        assertThat(params.getPage()).isEqualTo(1);
        assertThat(params.getLimit()).isEqualTo(50);
    }

    // ========================================
    // Test 4.3: Max Limit Enforcement
    // ========================================

    @Test
    @DisplayName("should_enforceMaxLimit_when_excessiveLimitRequested")
    void should_enforceMaxLimit_when_excessiveLimitRequested() {
        // Given: Limit exceeding maximum (100)
        int excessiveLimit = 500;

        // When: Parsing pagination params
        PaginationParams params = PaginationUtils.parseParams(1, excessiveLimit);

        // Then: Should cap at max limit (100)
        assertThat(params.getLimit()).isEqualTo(100);
    }

    @Test
    @DisplayName("should_allowLimit_when_withinMaxLimit")
    void should_allowLimit_when_withinMaxLimit() {
        // Given: Limit within maximum
        int validLimit = 50;

        // When: Parsing pagination params
        PaginationParams params = PaginationUtils.parseParams(1, validLimit);

        // Then: Should use requested limit
        assertThat(params.getLimit()).isEqualTo(50);
    }

    @Test
    @DisplayName("should_allowMaxLimit_when_exactlyMaxRequested")
    void should_allowMaxLimit_when_exactlyMaxRequested() {
        // Given: Limit exactly at maximum
        int maxLimit = 100;

        // When: Parsing pagination params
        PaginationParams params = PaginationUtils.parseParams(1, maxLimit);

        // Then: Should allow max limit
        assertThat(params.getLimit()).isEqualTo(100);
    }

    // ========================================
    // Test 4.4: Validation Errors
    // ========================================

    @Test
    @DisplayName("should_throwValidationError_when_negativePageProvided")
    void should_throwValidationError_when_negativePageProvided() {
        // Given: Negative page number
        int invalidPage = -1;

        // When/Then: Should throw ValidationException
        assertThatThrownBy(() -> PaginationUtils.parseParams(invalidPage, 20))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Page number must be positive");
    }

    @Test
    @DisplayName("should_throwValidationError_when_zeroPageProvided")
    void should_throwValidationError_when_zeroPageProvided() {
        // Given: Zero page number
        int invalidPage = 0;

        // When/Then: Should throw ValidationException
        assertThatThrownBy(() -> PaginationUtils.parseParams(invalidPage, 20))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Page number must be positive");
    }

    @Test
    @DisplayName("should_throwValidationError_when_negativeLimitProvided")
    void should_throwValidationError_when_negativeLimitProvided() {
        // Given: Negative limit
        int invalidLimit = -10;

        // When/Then: Should throw ValidationException
        assertThatThrownBy(() -> PaginationUtils.parseParams(1, invalidLimit))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Limit must be positive");
    }

    @Test
    @DisplayName("should_throwValidationError_when_zeroLimitProvided")
    void should_throwValidationError_when_zeroLimitProvided() {
        // Given: Zero limit
        int invalidLimit = 0;

        // When/Then: Should throw ValidationException
        assertThatThrownBy(() -> PaginationUtils.parseParams(1, invalidLimit))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Limit must be positive");
    }

    // ========================================
    // Test 10.1: Generate Pagination Metadata
    // ========================================

    @Test
    @DisplayName("should_generateMetadata_when_paginationDataProvided")
    void should_generateMetadata_when_paginationDataProvided() {
        // Given: Pagination data
        int page = 2;
        int limit = 10;
        long total = 45;

        // When: Generating metadata
        PaginationMetadata metadata = PaginationUtils.generateMetadata(page, limit, total);

        // Then: Should generate complete metadata
        assertThat(metadata.getPage()).isEqualTo(2);
        assertThat(metadata.getLimit()).isEqualTo(10);
        assertThat(metadata.getTotal()).isEqualTo(45);
        assertThat(metadata.getTotalPages()).isEqualTo(5); // ceil(45/10) = 5
        assertThat(metadata.isHasNext()).isTrue(); // page 2 of 5
        assertThat(metadata.isHasPrev()).isTrue(); // page > 1
    }

    // ========================================
    // Test 10.2: Calculate hasNext
    // ========================================

    @Test
    @DisplayName("should_calculateHasNext_when_morePagesExist")
    void should_calculateHasNext_when_morePagesExist() {
        // Given: Not on last page
        int page = 1;
        int limit = 10;
        long total = 50;

        // When: Generating metadata
        PaginationMetadata metadata = PaginationUtils.generateMetadata(page, limit, total);

        // Then: hasNext should be true
        assertThat(metadata.isHasNext()).isTrue();
    }

    @Test
    @DisplayName("should_calculateHasNextFalse_when_onLastPage")
    void should_calculateHasNextFalse_when_onLastPage() {
        // Given: On last page
        int page = 5;
        int limit = 10;
        long total = 50;

        // When: Generating metadata
        PaginationMetadata metadata = PaginationUtils.generateMetadata(page, limit, total);

        // Then: hasNext should be false
        assertThat(metadata.isHasNext()).isFalse();
    }

    @Test
    @DisplayName("should_calculateHasNextFalse_when_onPartialLastPage")
    void should_calculateHasNextFalse_when_onPartialLastPage() {
        // Given: On last page with partial results
        int page = 3;
        int limit = 20;
        long total = 45; // Page 3 would have 5 items

        // When: Generating metadata
        PaginationMetadata metadata = PaginationUtils.generateMetadata(page, limit, total);

        // Then: hasNext should be false
        assertThat(metadata.isHasNext()).isFalse();
    }

    // ========================================
    // Test 10.2: Calculate hasPrev
    // ========================================

    @Test
    @DisplayName("should_calculateHasPrevFalse_when_onFirstPage")
    void should_calculateHasPrevFalse_when_onFirstPage() {
        // Given: On first page
        int page = 1;
        int limit = 10;
        long total = 50;

        // When: Generating metadata
        PaginationMetadata metadata = PaginationUtils.generateMetadata(page, limit, total);

        // Then: hasPrev should be false
        assertThat(metadata.isHasPrev()).isFalse();
    }

    @Test
    @DisplayName("should_calculateHasPrevTrue_when_notOnFirstPage")
    void should_calculateHasPrevTrue_when_notOnFirstPage() {
        // Given: On second or later page
        int page = 2;
        int limit = 10;
        long total = 50;

        // When: Generating metadata
        PaginationMetadata metadata = PaginationUtils.generateMetadata(page, limit, total);

        // Then: hasPrev should be true
        assertThat(metadata.isHasPrev()).isTrue();
    }

    // ========================================
    // Test 10.3: Calculate totalPages
    // ========================================

    @Test
    @DisplayName("should_calculateTotalPages_when_totalProvided")
    void should_calculateTotalPages_when_totalProvided() {
        // Given: Total and limit
        int limit = 10;
        long total = 45;

        // When: Generating metadata
        PaginationMetadata metadata = PaginationUtils.generateMetadata(1, limit, total);

        // Then: totalPages should be ceil(total/limit) = 5
        assertThat(metadata.getTotalPages()).isEqualTo(5);
    }

    @Test
    @DisplayName("should_calculateTotalPagesExact_when_evenlyDivisible")
    void should_calculateTotalPagesExact_when_evenlyDivisible() {
        // Given: Total evenly divisible by limit
        int limit = 10;
        long total = 50;

        // When: Generating metadata
        PaginationMetadata metadata = PaginationUtils.generateMetadata(1, limit, total);

        // Then: totalPages should be exactly total/limit = 5
        assertThat(metadata.getTotalPages()).isEqualTo(5);
    }

    @Test
    @DisplayName("should_calculateTotalPagesOne_when_lessThanLimit")
    void should_calculateTotalPagesOne_when_lessThanLimit() {
        // Given: Total less than limit
        int limit = 20;
        long total = 5;

        // When: Generating metadata
        PaginationMetadata metadata = PaginationUtils.generateMetadata(1, limit, total);

        // Then: totalPages should be 1
        assertThat(metadata.getTotalPages()).isEqualTo(1);
    }

    @Test
    @DisplayName("should_calculateTotalPagesZero_when_noResults")
    void should_calculateTotalPagesZero_when_noResults() {
        // Given: No results
        int limit = 20;
        long total = 0;

        // When: Generating metadata
        PaginationMetadata metadata = PaginationUtils.generateMetadata(1, limit, total);

        // Then: totalPages should be 0
        assertThat(metadata.getTotalPages()).isEqualTo(0);
        assertThat(metadata.isHasNext()).isFalse();
        assertThat(metadata.isHasPrev()).isFalse();
    }

    // ========================================
    // Offset Calculation
    // ========================================

    @Test
    @DisplayName("should_calculateOffset_when_pageAndLimitProvided")
    void should_calculateOffset_when_pageAndLimitProvided() {
        // Given: Page 3, limit 10
        PaginationParams params = PaginationUtils.parseParams(3, 10);

        // When: Calculating offset
        int offset = params.getOffset();

        // Then: Offset should be (page-1) * limit = 20
        assertThat(offset).isEqualTo(20);
    }

    @Test
    @DisplayName("should_calculateOffsetZero_when_firstPage")
    void should_calculateOffsetZero_when_firstPage() {
        // Given: First page
        PaginationParams params = PaginationUtils.parseParams(1, 10);

        // When: Calculating offset
        int offset = params.getOffset();

        // Then: Offset should be 0
        assertThat(offset).isEqualTo(0);
    }
}
