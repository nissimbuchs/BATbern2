package ch.batbern.agent.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Objects;
import java.util.Optional;

/**
 * The past events a tool's call should be judged against (story 6, AD-8): the input of
 * {@code findPastEvents} that the precedent retrieval reads with the run's token. A tool declares
 * it through {@link AgentTool#pastEventQuery}.
 *
 * @param year       only events of this calendar year (2000 to 2100)
 * @param searchText words that must all occur in the event, its sessions or its speakers (at most 100 characters)
 * @param limit      how many events at most, 1 to 50
 */
public record PastEventQuery(Optional<Integer> year, Optional<String> searchText, int limit) {

    private static final ObjectMapper JSON = new ObjectMapper();

    public PastEventQuery {
        Objects.requireNonNull(year, "year");
        Objects.requireNonNull(searchText, "searchText");
        year.ifPresent(value -> {
            if (value < 2000 || value > 2100) {
                throw new IllegalArgumentException("year must be between 2000 and 2100");
            }
        });
        searchText.ifPresent(value -> {
            if (value.isBlank() || value.length() > 100) {
                throw new IllegalArgumentException("searchText must hold 1 to 100 characters");
            }
        });
        if (limit < 1 || limit > 50) {
            throw new IllegalArgumentException("limit must be between 1 and 50");
        }
    }

    /** The past events matching {@code searchText}, newest {@code limit} first. */
    public static PastEventQuery matching(String searchText, int limit) {
        return new PastEventQuery(Optional.empty(), Optional.of(searchText), limit);
    }

    /** The input {@code findPastEvents} takes for this query. */
    public ObjectNode toInput() {
        ObjectNode input = JSON.createObjectNode();
        year.ifPresent(value -> input.put("year", value));
        searchText.ifPresent(value -> input.put("searchText", value));
        input.put("limit", limit);
        return input;
    }
}
