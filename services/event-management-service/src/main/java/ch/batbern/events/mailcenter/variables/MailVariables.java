package ch.batbern.events.mailcenter.variables;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The values one mail renders with (Story 16.11). Only {@link MailVariable} names can be set, so the
 * catalog stays the single source of names; {@link #asMap()} adds every alias, so old templates
 * keep working (AC3). Built by {@link MailVariableFactory} with the shared values, then completed by
 * the sending service with its context's values; a later {@link #put} replaces an earlier one.
 */
public final class MailVariables {

    private final Map<MailVariable, String> values = new EnumMap<>(MailVariable.class);

    public MailVariables put(MailVariable variable, String value) {
        values.put(variable, value == null ? "" : value);
        return this;
    }

    public MailVariables put(MailVariable variable, Object value) {
        return put(variable, value == null ? null : String.valueOf(value));
    }

    public Optional<String> get(MailVariable variable) {
        return Optional.ofNullable(values.get(variable));
    }

    /** Placeholder name → value, canonical names and aliases. */
    public Map<String, String> asMap() {
        Map<String, String> map = new LinkedHashMap<>();
        values.forEach((variable, value) -> variable.names().forEach(name -> map.put(name, value)));
        return map;
    }
}
