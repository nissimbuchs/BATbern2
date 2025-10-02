package ch.batbern.shared.utils;

import ch.batbern.shared.exceptions.ValidationException;

import java.util.regex.Pattern;

public class ValidationUtils {

    private static final Pattern SWISS_UID_PATTERN = Pattern.compile("^CHE-\\d{3}\\.\\d{3}\\.\\d{3}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@" +
        "(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$"
    );

    private ValidationUtils() {
        // Utility class, prevent instantiation
    }

    public static void validateSwissUID(String uid) throws ValidationException {
        if (uid == null) {
            throw new ValidationException("Swiss UID cannot be null");
        }
        if (uid.isEmpty()) {
            throw new ValidationException("Swiss UID cannot be empty");
        }
        if (!SWISS_UID_PATTERN.matcher(uid).matches()) {
            throw new ValidationException("Invalid Swiss UID format: " + uid + ". Expected format: CHE-XXX.XXX.XXX");
        }
    }

    public static void validateEmail(String email) throws ValidationException {
        if (email == null) {
            throw new ValidationException("Email cannot be null");
        }
        if (email.trim().isEmpty()) {
            throw new ValidationException("Email cannot be empty");
        }
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new ValidationException("Invalid email format: " + email);
        }
    }

    public static void validateRequired(Object value, String fieldName) throws ValidationException {
        if (value == null) {
            throw new ValidationException(fieldName + " is required");
        }
        if (value instanceof String) {
            String str = (String) value;
            if (str.isEmpty()) {
                throw new ValidationException(fieldName + " cannot be empty");
            }
            if (str.trim().isEmpty()) {
                throw new ValidationException(fieldName + " cannot be blank");
            }
        }
    }

    public static void validateLength(String value, int minLength, int maxLength, String fieldName)
            throws ValidationException {
        if (value == null) {
            throw new ValidationException(fieldName + " cannot be null");
        }
        int length = value.length();
        if (length < minLength || length > maxLength) {
            throw new ValidationException(
                String.format("%s must be between %d and %d characters", fieldName, minLength, maxLength)
            );
        }
    }

    public static void validatePattern(String value, String pattern, String errorMessage)
            throws ValidationException {
        if (value == null) {
            throw new ValidationException("Value cannot be null");
        }
        if (!Pattern.compile(pattern).matcher(value).matches()) {
            throw new ValidationException(errorMessage);
        }
    }

    public static void validateNotNull(Object value, String fieldName) throws ValidationException {
        if (value == null) {
            throw new ValidationException(fieldName + " cannot be null");
        }
    }

    public static void validateNotEmpty(String value, String fieldName) throws ValidationException {
        validateNotNull(value, fieldName);
        if (value.trim().isEmpty()) {
            throw new ValidationException(fieldName + " cannot be empty");
        }
    }

    public static void validatePositive(Number value, String fieldName) throws ValidationException {
        validateNotNull(value, fieldName);
        if (value.doubleValue() <= 0) {
            throw new ValidationException(fieldName + " must be positive");
        }
    }

    public static void validateInRange(int value, int min, int max, String fieldName)
            throws ValidationException {
        if (value < min || value > max) {
            throw new ValidationException(
                String.format("%s must be between %d and %d", fieldName, min, max)
            );
        }
    }
}