package ch.batbern.shared.types;

import ch.batbern.shared.exceptions.ValidationException;
import lombok.Value;

import java.util.Objects;
import java.util.regex.Pattern;

@Value
public class CompanyId {
    private static final Pattern SWISS_UID_PATTERN = Pattern.compile(
        "^CHE[-\\s]?([0-9]{3})[\\.\\s]?([0-9]{3})[\\.\\s]?([0-9]{3})$"
    );

    String value;
    String normalizedValue;

    private CompanyId(String value) {
        if (value == null) {
            throw ValidationException.nullValue("CompanyId");
        }
        if (value.trim().isEmpty()) {
            throw ValidationException.emptyValue("CompanyId");
        }

        String normalized = normalizeSwissUID(value);
        if (!isValidSwissUID(normalized)) {
            throw new ValidationException("Invalid Swiss UID format: " + value);
        }

        this.value = value;
        this.normalizedValue = normalized;
    }

    public static CompanyId from(String swissUid) {
        return new CompanyId(swissUid);
    }

    private static String normalizeSwissUID(String uid) {
        String cleaned = uid.toUpperCase().replaceAll("[\\s.-]", "");

        if (!cleaned.startsWith("CHE") || cleaned.length() != 12) {
            return uid;
        }

        String digits = cleaned.substring(3);
        return String.format("CHE-%s.%s.%s",
            digits.substring(0, 3),
            digits.substring(3, 6),
            digits.substring(6, 9));
    }

    private static boolean isValidSwissUID(String uid) {
        if (!SWISS_UID_PATTERN.matcher(uid).matches()) {
            return false;
        }

        String digitsOnly = uid.replaceAll("[^0-9]", "");

        if (digitsOnly.equals("000000000")) {
            return false;
        }

        return true;
    }

    @Override
    public String toString() {
        return normalizedValue;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CompanyId companyId = (CompanyId) o;
        return Objects.equals(normalizedValue, companyId.normalizedValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(normalizedValue);
    }
}