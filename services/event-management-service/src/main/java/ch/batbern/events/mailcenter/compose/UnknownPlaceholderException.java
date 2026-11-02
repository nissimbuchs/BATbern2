package ch.batbern.events.mailcenter.compose;

import java.util.List;

/** Own text names a placeholder the audience does not have (Story 16.5 AC2, 400). */
public class UnknownPlaceholderException extends IllegalArgumentException {

    private final List<String> placeholders;

    public UnknownPlaceholderException(List<String> placeholders) {
        super("Unknown placeholder(s): " + String.join(", ", placeholders));
        this.placeholders = List.copyOf(placeholders);
    }

    public List<String> getPlaceholders() {
        return placeholders;
    }
}
