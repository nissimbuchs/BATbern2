package ch.batbern.shared.exception;

public enum ErrorCode {
    // Validation errors (400)
    ERR_VALIDATION("Validation failed"),
    ERR_INVALID_REQUEST("Invalid request"),

    // Not found errors (404)
    ERR_NOT_FOUND("Resource not found"),
    ERR_EVENT_NOT_FOUND("Event not found"),
    ERR_SPEAKER_NOT_FOUND("Speaker not found"),
    ERR_COMPANY_NOT_FOUND("Company not found"),

    // Authorization errors (401, 403)
    ERR_UNAUTHORIZED("Unauthorized access"),
    ERR_FORBIDDEN("Access forbidden"),
    ERR_INVALID_TOKEN("Invalid authentication token"),

    // Service errors (500)
    ERR_SERVICE("Internal service error"),
    ERR_DATABASE("Database operation failed"),
    ERR_EXTERNAL_SERVICE("External service call failed"),

    // Business logic errors (409, 422)
    ERR_DUPLICATE("Duplicate resource"),
    ERR_CONFLICT("Resource conflict"),
    ERR_BUSINESS_RULE("Business rule violation");

    private final String defaultMessage;

    ErrorCode(String defaultMessage) {
        this.defaultMessage = defaultMessage;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
