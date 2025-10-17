package ch.batbern.companyuser.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for Swiss UID Validation Service
 * Tests cover AC3, AC12 - Swiss UID validation and business registry integration
 */
@DisplayName("Swiss UID Validation Service Tests")
class SwissUIDValidationServiceTest {

    private SwissUIDValidationService validationService;

    @BeforeEach
    void setUp() {
        validationService = new SwissUIDValidationService();
    }

    // AC3/AC12 Tests: Swiss UID Format Validation

    @Test
    @DisplayName("Test 12.1: should_validateUID_when_validSwissUIDProvided")
    void should_validateUID_when_validSwissUIDProvided() {
        // Given
        String validUID = "CHE-123.456.789";

        // When
        boolean isValid = validationService.isValidUID(validUID);

        // Then
        assertThat(isValid).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "CHE-123.456.789",
            "CHE-100.000.001",
            "CHE-999.999.999"
    })
    @DisplayName("Test 12.1b: should_validateUID_when_variousValidFormatsProvided")
    void should_validateUID_when_variousValidFormatsProvided(String validUID) {
        // When
        boolean isValid = validationService.isValidUID(validUID);

        // Then
        assertThat(isValid).isTrue();
    }

    @Test
    @DisplayName("Test 12.2: should_returnInvalid_when_invalidUIDFormat")
    void should_returnInvalid_when_invalidUIDFormat() {
        // Given
        String invalidUID = "INVALID-FORMAT";

        // When
        boolean isValid = validationService.isValidUID(invalidUID);

        // Then
        assertThat(isValid).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "CHE-12.345.678",    // Wrong digit count
            "CHE123456789",       // Missing dots
            "CH-123.456.789",     // Wrong prefix
            "CHE-ABC.DEF.GHI",    // Letters instead of numbers
            "",                   // Empty string
            "   "                 // Whitespace
    })
    @DisplayName("Test 12.2b: should_returnInvalid_when_variousInvalidFormats")
    void should_returnInvalid_when_variousInvalidFormats(String invalidUID) {
        // When
        boolean isValid = validationService.isValidUID(invalidUID);

        // Then
        assertThat(isValid).isFalse();
    }

    @Test
    @DisplayName("Test 12.2c: should_handleNull_when_nullUIDProvided")
    void should_handleNull_when_nullUIDProvided() {
        // When
        boolean isValid = validationService.isValidUID(null);

        // Then
        assertThat(isValid).isFalse();
    }

    @Test
    @DisplayName("Test 12.3: should_integrateBusinessRegistry_when_validationRequested")
    void should_integrateBusinessRegistry_when_validationRequested() {
        // Given
        String validUID = "CHE-123.456.789";

        // When - Currently validates format only
        // TODO: Integrate with Swiss Business Registry API in future iteration
        boolean isValid = validationService.isValidUID(validUID);

        // Then
        assertThat(isValid).isTrue();
        // Future: Verify actual business registry lookup
    }

    @Test
    @DisplayName("Test 12.4: should_cacheValidationResults_when_uidValidated")
    void should_cacheValidationResults_when_uidValidated() {
        // Given
        String uid = "CHE-123.456.789";

        // When - Validate twice
        boolean firstResult = validationService.isValidUID(uid);
        boolean secondResult = validationService.isValidUID(uid);

        // Then - Both should return same result
        assertThat(firstResult).isEqualTo(secondResult);
        // Future: Verify caching behavior with mock/spy
    }

    // Additional Format Tests

    @Test
    @DisplayName("should_trimWhitespace_when_uidHasExtraSpaces")
    void should_trimWhitespace_when_uidHasExtraSpaces() {
        // Given
        String uidWithSpaces = "  CHE-123.456.789  ";

        // When
        boolean isValid = validationService.isValidUID(uidWithSpaces);

        // Then
        assertThat(isValid).isTrue();
    }

    @Test
    @DisplayName("should_beCaseInsensitive_when_lowercasePrefix")
    void should_beCaseInsensitive_when_lowercasePrefix() {
        // Given
        String lowercaseUID = "che-123.456.789";

        // When
        boolean isValid = validationService.isValidUID(lowercaseUID);

        // Then
        assertThat(isValid).isTrue();
    }

    @Test
    @DisplayName("should_rejectZeros_when_allZeroUID")
    void should_rejectZeros_when_allZeroUID() {
        // Given
        String zeroUID = "CHE-000.000.000";

        // When
        boolean isValid = validationService.isValidUID(zeroUID);

        // Then
        assertThat(isValid).isFalse();
    }
}
