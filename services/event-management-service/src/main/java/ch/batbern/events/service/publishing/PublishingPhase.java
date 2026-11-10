package ch.batbern.events.service.publishing;

import java.util.Arrays;
import java.util.Locale;

/**
 * A progressive-publishing phase, in publishing order (Story 4.9b: the addressed instance of
 * {@code POST /events/{eventCode}/phases/{phase}/publish|unpublish}). The wire and the stored
 * {@code events.current_published_phase} use the lower-case {@link #value()}.
 */
public enum PublishingPhase {
    TOPIC,
    SPEAKERS,
    AGENDA;

    /** The wire and database form: {@code topic}, {@code speakers}, {@code agenda}. */
    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The phase before this one, or {@code null} for the first. */
    public PublishingPhase previous() {
        return ordinal() == 0 ? null : values()[ordinal() - 1];
    }

    /**
     * The phase for a path value. An unknown value is a bad request (400 through the
     * {@code IllegalArgumentException} handler), never a 404 or a 500.
     */
    public static PublishingPhase fromValue(String value) {
        return Arrays.stream(values())
                .filter(phase -> phase.value().equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown publishing phase '" + value + "': expected topic, speakers or agenda"));
    }
}
