package ch.batbern.shared.exception;

import org.junit.jupiter.api.Test;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class BATbernExceptionTest {

    @Test
    void should_createBATbernException_when_constructorCalled() {
        // Given
        ErrorCode errorCode = ErrorCode.ERR_SERVICE;
        String message = "Test error message";
        BATbernException.Severity severity = BATbernException.Severity.HIGH;

        // When
        TestException exception = new TestException(errorCode, message, severity);

        // Then
        assertThat(exception.getErrorCode()).isEqualTo(errorCode);
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getSeverity()).isEqualTo(severity);
        assertThat(exception.getDetails()).isNotNull().isEmpty();
    }

    @Test
    void should_includeErrorCode_when_exceptionCreated() {
        // Given
        ErrorCode errorCode = ErrorCode.ERR_VALIDATION;

        // When
        TestException exception = new TestException(errorCode, "Validation failed", BATbernException.Severity.MEDIUM);

        // Then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ERR_VALIDATION);
        assertThat(exception.getErrorCode().getDefaultMessage()).isEqualTo("Validation failed");
    }

    @Test
    void should_includeDetailsMap_when_providedInConstructor() {
        // Given
        Map<String, Object> details = Map.of(
            "field", "email",
            "value", "invalid-email"
        );

        // When
        TestException exception = new TestException(
            ErrorCode.ERR_VALIDATION,
            "Invalid email",
            details,
            BATbernException.Severity.MEDIUM
        );

        // Then
        assertThat(exception.getDetails())
            .containsEntry("field", "email")
            .containsEntry("value", "invalid-email");
    }

    @Test
    void should_haveSeverityLevels_when_exceptionCreated() {
        // Then
        assertThat(BATbernException.Severity.values())
            .containsExactly(
                BATbernException.Severity.LOW,
                BATbernException.Severity.MEDIUM,
                BATbernException.Severity.HIGH,
                BATbernException.Severity.CRITICAL
            );
    }

    // Test concrete exception for testing abstract base class
    private static class TestException extends BATbernException {
        public TestException(ErrorCode errorCode, String message, Severity severity) {
            super(errorCode, message, severity);
        }

        public TestException(ErrorCode errorCode, String message, Map<String, Object> details, Severity severity) {
            super(errorCode, message, details, severity);
        }
    }
}
