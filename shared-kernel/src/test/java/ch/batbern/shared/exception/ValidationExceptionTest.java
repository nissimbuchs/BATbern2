package ch.batbern.shared.exception;

import org.junit.jupiter.api.Test;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class ValidationExceptionTest {

    @Test
    void should_haveValidationException_when_validationFails() {
        // Given
        String message = "Invalid input";

        // When
        ValidationException exception = new ValidationException(message);

        // Then
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ERR_VALIDATION);
        assertThat(exception.getSeverity()).isEqualTo(BATbernException.Severity.MEDIUM);
    }

    @Test
    void should_includeFieldInformation_when_fieldValidationFails() {
        // Given
        String field = "email";
        String message = "must be a valid email address";

        // When
        ValidationException exception = new ValidationException(field, message);

        // Then
        assertThat(exception.getMessage()).isEqualTo("Validation failed for field 'email': must be a valid email address");
        assertThat(exception.getDetails())
            .containsEntry("field", "email");
    }

    @Test
    void should_includeDetailsMap_when_providedInConstructor() {
        // Given
        String message = "Multiple validation errors";
        Map<String, Object> details = Map.of(
            "email", "invalid format",
            "age", "must be positive"
        );

        // When
        ValidationException exception = new ValidationException(message, details);

        // Then
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getDetails())
            .containsEntry("email", "invalid format")
            .containsEntry("age", "must be positive");
    }

    @Test
    void should_extendBATbernException_when_created() {
        // Given
        ValidationException exception = new ValidationException("test");

        // Then
        assertThat(exception).isInstanceOf(BATbernException.class);
    }

    @Test
    void should_haveResponseStatus400_when_annotationPresent() {
        // Given
        ValidationException exception = new ValidationException("test");

        // Then
        assertThat(exception.getClass().getAnnotation(org.springframework.web.bind.annotation.ResponseStatus.class))
            .isNotNull();
        assertThat(exception.getClass().getAnnotation(org.springframework.web.bind.annotation.ResponseStatus.class).value())
            .isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);
    }
}
