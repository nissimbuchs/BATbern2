package ch.batbern.events.mapper;

import ch.batbern.events.sessions.dto.generated.MaterialsStatus;
import ch.batbern.events.sessions.dto.generated.SessionType;

/**
 * Conversions between the stored strings of {@code sessions.session_type} /
 * {@code sessions.materials_status} and the contract enums {@link SessionType} /
 * {@link MaterialsStatus} (#1123).
 *
 * <p>The V127 CHECK constraints keep every stored value inside both enums, so an unknown value here
 * is a bug and fails loudly instead of reaching a client. The {@code Object} overloads accept either
 * form, because the legacy map-based event assembly carries entity strings and DTO enums side by side.
 */
public final class SessionEnums {

    private SessionEnums() {
    }

    public static SessionType sessionType(Object value) {
        if (value == null || value instanceof SessionType) {
            return (SessionType) value;
        }
        return SessionType.fromValue((String) value);
    }

    public static MaterialsStatus materialsStatus(Object value) {
        if (value == null || value instanceof MaterialsStatus) {
            return (MaterialsStatus) value;
        }
        return MaterialsStatus.fromValue((String) value);
    }

    /** The stored/wire string of a contract value, or {@code null}. */
    public static String value(SessionType type) {
        return type == null ? null : type.getValue();
    }
}
