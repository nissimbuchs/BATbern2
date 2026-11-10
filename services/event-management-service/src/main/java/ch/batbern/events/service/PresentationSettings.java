package ch.batbern.events.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Iterator;
import java.util.Set;

/**
 * Moderator presentation page settings (Story 10.8a): the About slide text and the partner
 * count. Stored by {@link AdminSettingsService} in app_settings under {@link #KEY} as JSON
 * ({@code {"aboutText": "...", "partnerCount": 9}}), moved there from CUMS in Story 4.9.
 *
 * @param aboutText    Verein purpose text, never blank
 * @param partnerCount number of partner companies, never negative
 */
public record PresentationSettings(String aboutText, int partnerCount) {

    /** The app_settings key. */
    public static final String KEY = "presentation";

    /** What the page shows while nothing is stored (the former CUMS default, verbatim). */
    public static final PresentationSettings DEFAULTS = new PresentationSettings(
            "BATbern ist eine unabhängige Plattform, die Berner Architekten und Ingenieure vernetzt. "
                    + "Wir veranstalten regelmässige Treffen, bei denen Fachleute Erfahrungen austauschen"
                    + " und neue Impulse gewinnen.",
            9);

    private static final Set<String> FIELDS = Set.of("aboutText", "partnerCount");

    /**
     * Parses and validates a stored or submitted value.
     *
     * @throws IllegalArgumentException when the value is not an object with exactly a non-blank
     *                                  {@code aboutText} and an integer {@code partnerCount} >= 0
     */
    public static PresentationSettings parse(ObjectMapper objectMapper, String json) {
        JsonNode node;
        try {
            node = json == null ? null : objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw invalid("not JSON");
        }
        if (node == null || !node.isObject()) {
            throw invalid("expected a JSON object");
        }
        for (Iterator<String> names = node.fieldNames(); names.hasNext(); ) {
            String name = names.next();
            if (!FIELDS.contains(name)) {
                throw invalid("unknown field " + name);
            }
        }
        JsonNode about = node.get("aboutText");
        if (about == null || !about.isTextual() || about.asText().isBlank()) {
            throw invalid("aboutText must be a non-blank string");
        }
        JsonNode count = node.get("partnerCount");
        if (count == null || !count.isIntegralNumber() || !count.canConvertToInt() || count.intValue() < 0) {
            throw invalid("partnerCount must be an integer >= 0");
        }
        return new PresentationSettings(about.asText(), count.intValue());
    }

    private static IllegalArgumentException invalid(String reason) {
        return new IllegalArgumentException("Invalid value for setting '" + KEY + "': " + reason);
    }
}
