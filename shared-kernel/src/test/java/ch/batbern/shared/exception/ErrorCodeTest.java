package ch.batbern.shared.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class ErrorCodeTest {

    @Test
    void should_haveValidationErrorCodes_when_enumDefined() {
        // Then
        assertThat(ErrorCode.ERR_VALIDATION).isNotNull();
        assertThat(ErrorCode.ERR_VALIDATION.getDefaultMessage()).isEqualTo("Validation failed");

        assertThat(ErrorCode.ERR_INVALID_REQUEST).isNotNull();
        assertThat(ErrorCode.ERR_INVALID_REQUEST.getDefaultMessage()).isEqualTo("Invalid request");
    }

    @Test
    void should_haveNotFoundErrorCodes_when_enumDefined() {
        // Then
        assertThat(ErrorCode.ERR_NOT_FOUND).isNotNull();
        assertThat(ErrorCode.ERR_NOT_FOUND.getDefaultMessage()).isEqualTo("Resource not found");

        assertThat(ErrorCode.ERR_EVENT_NOT_FOUND).isNotNull();
        assertThat(ErrorCode.ERR_SPEAKER_NOT_FOUND).isNotNull();
        assertThat(ErrorCode.ERR_COMPANY_NOT_FOUND).isNotNull();
    }

    @Test
    void should_haveAuthorizationErrorCodes_when_enumDefined() {
        // Then
        assertThat(ErrorCode.ERR_UNAUTHORIZED).isNotNull();
        assertThat(ErrorCode.ERR_UNAUTHORIZED.getDefaultMessage()).isEqualTo("Unauthorized access");

        assertThat(ErrorCode.ERR_FORBIDDEN).isNotNull();
        assertThat(ErrorCode.ERR_INVALID_TOKEN).isNotNull();
    }

    @Test
    void should_haveServiceErrorCodes_when_enumDefined() {
        // Then
        assertThat(ErrorCode.ERR_SERVICE).isNotNull();
        assertThat(ErrorCode.ERR_SERVICE.getDefaultMessage()).isEqualTo("Internal service error");

        assertThat(ErrorCode.ERR_DATABASE).isNotNull();
        assertThat(ErrorCode.ERR_EXTERNAL_SERVICE).isNotNull();
    }

    @Test
    void should_haveBusinessLogicErrorCodes_when_enumDefined() {
        // Then
        assertThat(ErrorCode.ERR_DUPLICATE).isNotNull();
        assertThat(ErrorCode.ERR_CONFLICT).isNotNull();
        assertThat(ErrorCode.ERR_BUSINESS_RULE).isNotNull();
    }
}
