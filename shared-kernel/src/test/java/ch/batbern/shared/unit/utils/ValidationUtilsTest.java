package ch.batbern.shared.unit.utils;

import ch.batbern.shared.exceptions.ValidationException;
import ch.batbern.shared.utils.ValidationUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatNoException;

class ValidationUtilsTest {

    @Nested
    @DisplayName("Swiss UID Validation")
    class SwissUIDValidation {

        @Test
        void should_validateSwissUID_when_validUIDProvided() {
            String validUID = "CHE-123.456.789";

            assertThatNoException()
                .isThrownBy(() -> ValidationUtils.validateSwissUID(validUID));
        }

        @Test
        void should_throwValidationException_when_invalidUIDProvided() {
            String invalidUID = "CHE123456789";

            assertThatThrownBy(() -> ValidationUtils.validateSwissUID(invalidUID))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Invalid Swiss UID format");
        }

        @Test
        void should_throwValidationException_when_nullUIDProvided() {
            assertThatThrownBy(() -> ValidationUtils.validateSwissUID(null))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Swiss UID cannot be null");
        }

        @Test
        void should_throwValidationException_when_emptyUIDProvided() {
            assertThatThrownBy(() -> ValidationUtils.validateSwissUID(""))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Swiss UID cannot be empty");
        }

        @Test
        void should_acceptValidUIDFormats() {
            String[] validUIDs = {
                "CHE-123.456.789",
                "CHE-999.999.999",
                "CHE-100.000.000"
            };

            for (String uid : validUIDs) {
                assertThatNoException()
                    .describedAs("Should accept valid UID: " + uid)
                    .isThrownBy(() -> ValidationUtils.validateSwissUID(uid));
            }
        }

        @Test
        void should_rejectInvalidUIDFormats() {
            String[] invalidUIDs = {
                "CH-123.456.789",    // Missing 'E'
                "CHE123.456.789",     // Missing first hyphen
                "CHE-123456789",      // Missing dots
                "CHE-123.456.78",     // Too few digits
                "CHE-123.456.7890",   // Too many digits
                "ABC-123.456.789",    // Wrong prefix
                "CHE-ABC.DEF.GHI"     // Non-numeric
            };

            for (String uid : invalidUIDs) {
                assertThatThrownBy(() -> ValidationUtils.validateSwissUID(uid))
                    .isInstanceOf(ValidationException.class)
                    .as("Should reject invalid UID: " + uid);
            }
        }
    }

    @Nested
    @DisplayName("Email Validation")
    class EmailValidation {

        @Test
        void should_validateEmail_when_validEmailProvided() {
            String validEmail = "user@batbern.ch";

            assertThatNoException()
                .isThrownBy(() -> ValidationUtils.validateEmail(validEmail));
        }

        @Test
        void should_throwValidationException_when_invalidEmailProvided() {
            String invalidEmail = "invalid.email";

            assertThatThrownBy(() -> ValidationUtils.validateEmail(invalidEmail))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Invalid email format");
        }

        @Test
        void should_throwValidationException_when_nullEmailProvided() {
            assertThatThrownBy(() -> ValidationUtils.validateEmail(null))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Email cannot be null");
        }

        @Test
        void should_acceptValidEmailFormats() {
            String[] validEmails = {
                "user@example.com",
                "first.last@company.co.uk",
                "name+tag@domain.org",
                "test_user@sub.domain.com"
            };

            for (String email : validEmails) {
                assertThatNoException()
                    .describedAs("Should accept valid email: " + email)
                    .isThrownBy(() -> ValidationUtils.validateEmail(email));
            }
        }

        @Test
        void should_rejectInvalidEmailFormats() {
            String[] invalidEmails = {
                "",
                "@example.com",
                "user@",
                "user",
                "user@.com",
                "user@domain",
                "user @domain.com",
                "user@domain .com"
            };

            for (String email : invalidEmails) {
                assertThatThrownBy(() -> ValidationUtils.validateEmail(email))
                    .isInstanceOf(ValidationException.class)
                    .as("Should reject invalid email: " + email);
            }
        }
    }

    @Nested
    @DisplayName("Required Field Validation")
    class RequiredFieldValidation {

        @Test
        void should_validateRequired_when_nonNullValueProvided() {
            Object value = "some value";
            String fieldName = "testField";

            assertThatNoException()
                .isThrownBy(() -> ValidationUtils.validateRequired(value, fieldName));
        }

        @Test
        void should_throwValidationException_when_nullValueProvided() {
            String fieldName = "testField";

            assertThatThrownBy(() -> ValidationUtils.validateRequired(null, fieldName))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("testField is required");
        }

        @Test
        void should_throwValidationException_when_emptyStringProvided() {
            String fieldName = "testField";

            assertThatThrownBy(() -> ValidationUtils.validateRequired("", fieldName))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("testField cannot be empty");
        }

        @Test
        void should_throwValidationException_when_blankStringProvided() {
            String fieldName = "testField";

            assertThatThrownBy(() -> ValidationUtils.validateRequired("   ", fieldName))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("testField cannot be blank");
        }

        @Test
        void should_acceptNonEmptyValues() {
            Object[] validValues = {
                "text",
                123,
                Boolean.TRUE,
                new Object()
            };

            for (Object value : validValues) {
                assertThatNoException()
                    .describedAs("Should accept non-null value: " + value)
                    .isThrownBy(() -> ValidationUtils.validateRequired(value, "field"));
            }
        }
    }

    @Nested
    @DisplayName("String Length Validation")
    class StringLengthValidation {

        @Test
        void should_validateLength_when_withinBounds() {
            String value = "test";

            assertThatNoException()
                .isThrownBy(() -> ValidationUtils.validateLength(value, 1, 10, "testField"));
        }

        @Test
        void should_throwValidationException_when_tooShort() {
            String value = "a";

            assertThatThrownBy(() -> ValidationUtils.validateLength(value, 2, 10, "testField"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("testField must be between 2 and 10 characters");
        }

        @Test
        void should_throwValidationException_when_tooLong() {
            String value = "this is a very long string";

            assertThatThrownBy(() -> ValidationUtils.validateLength(value, 1, 10, "testField"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("testField must be between 1 and 10 characters");
        }

        @Test
        void should_acceptExactBoundaryValues() {
            String minLength = "ab";
            String maxLength = "1234567890";

            assertThatNoException()
                .isThrownBy(() -> ValidationUtils.validateLength(minLength, 2, 10, "field"));

            assertThatNoException()
                .isThrownBy(() -> ValidationUtils.validateLength(maxLength, 2, 10, "field"));
        }
    }

    @Nested
    @DisplayName("Pattern Validation")
    class PatternValidation {

        @Test
        void should_validatePattern_when_matchesRegex() {
            String value = "ABC123";
            String pattern = "^[A-Z]{3}[0-9]{3}$";

            assertThatNoException()
                .isThrownBy(() -> ValidationUtils.validatePattern(value, pattern, "Code must be 3 letters followed by 3 digits"));
        }

        @Test
        void should_throwValidationException_when_doesNotMatchPattern() {
            String value = "ABC12";
            String pattern = "^[A-Z]{3}[0-9]{3}$";

            assertThatThrownBy(() -> ValidationUtils.validatePattern(value, pattern, "Code must be 3 letters followed by 3 digits"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Code must be 3 letters followed by 3 digits");
        }

        @Test
        void should_throwValidationException_when_nullValueProvided() {
            String pattern = "^[A-Z]{3}[0-9]{3}$";

            assertThatThrownBy(() -> ValidationUtils.validatePattern(null, pattern, "Code is required"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Value cannot be null");
        }
    }
}