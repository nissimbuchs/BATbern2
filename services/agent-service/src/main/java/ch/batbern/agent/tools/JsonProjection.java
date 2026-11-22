package ch.batbern.agent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;

/**
 * Deterministic projection of an API answer to what a tool gives the model: only the named fields,
 * in that order, absent fields left out. Keeps tokens low and keeps personal data the model does
 * not need (e-mail, picture, bio, material links) out of the prompt.
 */
public final class JsonProjection {

    private static final ObjectMapper JSON = new ObjectMapper();

    private JsonProjection() {
    }

    /** {@code fields} of {@code source}; nested projections go through {@link #pickEach}. */
    public static ObjectNode pick(JsonNode source, List<String> fields) {
        ObjectNode target = JSON.createObjectNode();
        for (String field : fields) {
            JsonNode value = source.get(field);
            if (value != null) {
                target.set(field, value);
            }
        }
        return target;
    }

    /** {@link #pick} of every element of {@code array}; an empty array for a missing or non-array node. */
    public static ArrayNode pickEach(JsonNode array, List<String> fields) {
        ArrayNode target = JSON.createArrayNode();
        if (array != null && array.isArray()) {
            array.forEach(element -> target.add(pick(element, fields)));
        }
        return target;
    }

    /** A new empty object. */
    public static ObjectNode object() {
        return JSON.createObjectNode();
    }
}
