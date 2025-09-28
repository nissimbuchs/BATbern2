package ch.batbern.shared.exceptions;

public class ValidationException extends RuntimeException {

    public ValidationException(String message) {
        super(message);
    }

    public ValidationException(String message, Throwable cause) {
        super(message, cause);
    }

    public static ValidationException nullValue(String fieldName) {
        return new ValidationException(fieldName + " cannot be null");
    }

    public static ValidationException emptyValue(String fieldName) {
        return new ValidationException(fieldName + " cannot be empty");
    }

    public static ValidationException blankValue(String fieldName) {
        return new ValidationException(fieldName + " cannot be blank");
    }

    public static ValidationException invalidFormat(String fieldName, String expectedFormat) {
        return new ValidationException(String.format("Invalid %s format. Expected: %s", fieldName, expectedFormat));
    }

    public static ValidationException invalidValue(String fieldName, String value, String reason) {
        return new ValidationException(String.format("Invalid %s: '%s'. %s", fieldName, value, reason));
    }
}