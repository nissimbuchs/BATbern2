package ch.batbern.shared.unit.utils;

import ch.batbern.shared.exceptions.DomainException;
import ch.batbern.shared.exceptions.ValidationException;
import ch.batbern.shared.utils.ErrorHandlingUtils;
import ch.batbern.shared.utils.ErrorResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorHandlingUtilsTest {

    @Nested
    @DisplayName("Error Message Formatting")
    class ErrorMessageFormatting {

        @Test
        void should_formatErrorMessage_when_validationFails() {
            ValidationException exception = new ValidationException("Email format is invalid");

            String formatted = ErrorHandlingUtils.formatErrorMessage(exception);

            assertThat(formatted)
                .contains("VALIDATION_ERROR")
                .contains("Email format is invalid");
        }

        @Test
        void should_formatErrorMessageWithCode_when_domainExceptionOccurs() {
            DomainException exception = new DomainException("EVENT_001", "Event already exists");

            String formatted = ErrorHandlingUtils.formatErrorMessage(exception);

            assertThat(formatted)
                .contains("EVENT_001")
                .contains("Event already exists");
        }

        @Test
        void should_includeStackTrace_when_debugModeEnabled() {
            Exception exception = new RuntimeException("Test error");

            String formatted = ErrorHandlingUtils.formatErrorMessage(exception, true);

            assertThat(formatted)
                .contains("RuntimeException")
                .contains("Test error")
                .contains("at ch.batbern");
        }

        @Test
        void should_excludeStackTrace_when_debugModeDisabled() {
            Exception exception = new RuntimeException("Test error");

            String formatted = ErrorHandlingUtils.formatErrorMessage(exception, false);

            assertThat(formatted)
                .contains("Test error")
                .doesNotContain("at ch.batbern");
        }

        @Test
        void should_handleNestedExceptions() {
            Exception cause = new IllegalArgumentException("Invalid argument");
            Exception exception = new RuntimeException("Processing failed", cause);

            String formatted = ErrorHandlingUtils.formatErrorMessage(exception);

            assertThat(formatted)
                .contains("Processing failed")
                .contains("Caused by")
                .contains("Invalid argument");
        }
    }

    @Nested
    @DisplayName("Error Response Creation")
    class ErrorResponseCreation {

        @Test
        void should_createErrorResponse_when_exceptionOccurs() {
            ValidationException exception = new ValidationException("Invalid email");

            ErrorResponse response = ErrorHandlingUtils.createErrorResponse(exception);

            assertThat(response).isNotNull();
            assertThat(response.getError()).isEqualTo("VALIDATION_ERROR");
            assertThat(response.getMessage()).isEqualTo("Invalid email");
            assertThat(response.getTimestamp()).isNotNull();
            assertThat(response.getPath()).isNull();
        }

        @Test
        void should_includeRequestPath_when_provided() {
            Exception exception = new RuntimeException("Error");
            String path = "/api/events/123";

            ErrorResponse response = ErrorHandlingUtils.createErrorResponse(exception, path);

            assertThat(response.getPath()).isEqualTo(path);
        }

        @Test
        void should_includeErrorCode_when_domainException() {
            DomainException exception = new DomainException("EVT_404", "Event not found");

            ErrorResponse response = ErrorHandlingUtils.createErrorResponse(exception);

            assertThat(response.getError()).isEqualTo("EVT_404");
            assertThat(response.getMessage()).isEqualTo("Event not found");
        }

        @Test
        void should_includeValidationDetails_when_multipleValidationErrors() {
            Map<String, String> fieldErrors = Map.of(
                "email", "Invalid email format",
                "name", "Name is required"
            );

            ErrorResponse response = ErrorHandlingUtils.createValidationErrorResponse(fieldErrors);

            assertThat(response.getError()).isEqualTo("VALIDATION_ERROR");
            assertThat(response.getDetails()).isNotNull();
            assertThat(response.getDetails()).containsKeys("email", "name");
            assertThat(response.getDetails().get("email")).isEqualTo("Invalid email format");
        }

        @Test
        void should_includeTraceId_when_available() {
            String traceId = "abc-123-def";
            Exception exception = new RuntimeException("Error");

            ErrorResponse response = ErrorHandlingUtils.createErrorResponse(exception, null, traceId);

            assertThat(response.getTraceId()).isEqualTo(traceId);
        }
    }

    @Nested
    @DisplayName("Error Classification")
    class ErrorClassification {

        @Test
        void should_classifyAsClientError_when_validationException() {
            ValidationException exception = new ValidationException("Invalid input");

            boolean isClientError = ErrorHandlingUtils.isClientError(exception);
            boolean isServerError = ErrorHandlingUtils.isServerError(exception);

            assertThat(isClientError).isTrue();
            assertThat(isServerError).isFalse();
        }

        @Test
        void should_classifyAsClientError_when_illegalArgumentException() {
            IllegalArgumentException exception = new IllegalArgumentException("Bad argument");

            boolean isClientError = ErrorHandlingUtils.isClientError(exception);

            assertThat(isClientError).isTrue();
        }

        @Test
        void should_classifyAsServerError_when_runtimeException() {
            RuntimeException exception = new RuntimeException("Internal error");

            boolean isServerError = ErrorHandlingUtils.isServerError(exception);
            boolean isClientError = ErrorHandlingUtils.isClientError(exception);

            assertThat(isServerError).isTrue();
            assertThat(isClientError).isFalse();
        }

        @Test
        void should_classifyAsRetryable_when_temporaryError() {
            RuntimeException exception = new RuntimeException("Connection timeout");

            boolean isRetryable = ErrorHandlingUtils.isRetryableError(exception);

            assertThat(isRetryable).isTrue();
        }

        @Test
        void should_classifyAsNonRetryable_when_validationError() {
            ValidationException exception = new ValidationException("Invalid format");

            boolean isRetryable = ErrorHandlingUtils.isRetryableError(exception);

            assertThat(isRetryable).isFalse();
        }
    }

    @Nested
    @DisplayName("Error Aggregation")
    class ErrorAggregation {

        @Test
        void should_aggregateErrors_when_multipleValidationFailures() {
            List<ValidationException> errors = List.of(
                new ValidationException("Email is invalid"),
                new ValidationException("Name is too short"),
                new ValidationException("Date is in the past")
            );

            ErrorResponse aggregated = ErrorHandlingUtils.aggregateErrors(errors);

            assertThat(aggregated.getError()).isEqualTo("MULTIPLE_ERRORS");
            assertThat(aggregated.getDetails()).hasSize(3);
        }

        @Test
        void should_groupErrorsByType_when_mixedExceptions() {
            List<Exception> errors = List.of(
                new ValidationException("Validation error 1"),
                new ValidationException("Validation error 2"),
                new RuntimeException("Runtime error"),
                new IllegalArgumentException("Argument error")
            );

            Map<String, List<String>> grouped = ErrorHandlingUtils.groupErrorsByType(errors);

            assertThat(grouped).containsKeys("VALIDATION_ERROR", "RUNTIME_ERROR", "ILLEGAL_ARGUMENT");
            assertThat(grouped.get("VALIDATION_ERROR")).hasSize(2);
            assertThat(grouped.get("RUNTIME_ERROR")).hasSize(1);
        }
    }

    @Nested
    @DisplayName("Error Context Enhancement")
    class ErrorContextEnhancement {

        @Test
        void should_addContextToError_when_additionalInfoProvided() {
            Exception original = new RuntimeException("Processing failed");
            Map<String, Object> context = Map.of(
                "eventId", "123",
                "userId", "456",
                "operation", "createEvent"
            );

            Exception enhanced = ErrorHandlingUtils.enhanceWithContext(original, context);

            assertThat(enhanced.getMessage())
                .contains("Processing failed")
                .contains("eventId=123")
                .contains("userId=456")
                .contains("operation=createEvent");
        }

        @Test
        void should_preserveOriginalCause_when_enhancingError() {
            Exception cause = new IllegalStateException("Invalid state");
            Exception original = new RuntimeException("Operation failed", cause);

            Exception enhanced = ErrorHandlingUtils.enhanceWithContext(original, Map.of());

            assertThat(enhanced.getCause()).isEqualTo(cause);
        }
    }
}