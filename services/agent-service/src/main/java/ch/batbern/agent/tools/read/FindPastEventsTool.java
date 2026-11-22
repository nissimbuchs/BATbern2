package ch.batbern.agent.tools.read;

import ch.batbern.agent.gateway.ApiResponse;
import ch.batbern.agent.gateway.Operation;
import ch.batbern.agent.tools.JsonProjection;
import ch.batbern.agent.tools.ToolContext;
import ch.batbern.agent.tools.ToolInput;
import ch.batbern.agent.tools.ToolOutcome;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * {@code findPastEvents}: BATbern events that took place before now, newest first, optionally of one
 * year or matching a search text ({@code GET /events}, archived ones included). The search text is
 * EMS's archive search ({@code title $contains}): every word must occur in the event's title,
 * description or code, a session title or abstract, or a speaker name.
 */
public final class FindPastEventsTool extends PublicApiReadTool {

    static final Operation LIST_EVENTS = Operation.of("GET /events");
    static final int DEFAULT_LIMIT = 10;
    static final int MAX_LIMIT = 50;

    private final Clock clock;

    public FindPastEventsTool(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public String name() {
        return "findPastEvents";
    }

    @Override
    public String description() {
        return "Finds BATbern events that took place before now, newest first: eventCode, number, title, "
                + "date, type, state and venue. Optionally only one year, or only events matching a search "
                + "text: every word must occur in the event's title, description or code, a session title or "
                + "abstract, or a speaker name.";
    }

    @Override
    public JsonNode inputSchema() {
        ObjectNode schema = JSON.createObjectNode().put("type", "object").put("additionalProperties", false);
        ObjectNode properties = schema.putObject("properties");
        properties.putObject("year").put("type", "integer").put("minimum", 2000).put("maximum", 2100)
                .put("description", "Only events of this calendar year");
        properties.putObject("searchText").put("type", "string").put("maxLength", 100)
                .put("description", "Words that must all occur in the event, its sessions or its speakers");
        properties.putObject("limit").put("type", "integer").put("minimum", 1).put("maximum", MAX_LIMIT)
                .put("description", "How many events at most, default " + DEFAULT_LIMIT);
        return schema;
    }

    @Override
    public Set<Operation> operations() {
        return Set.of(LIST_EVENTS);
    }

    @Override
    ToolOutcome read(JsonNode input, ToolContext context) {
        Optional<Integer> year = ToolInput.optionalInt(input, "year", 2000, 2100);
        Optional<String> searchText = ToolInput.optionalText(input, "searchText", 100);
        int limit = ToolInput.optionalInt(input, "limit", 1, MAX_LIMIT).orElse(DEFAULT_LIMIT);

        ObjectNode filter = JSON.createObjectNode();
        filter.putObject("date").put("$lt", Instant.now(clock).truncatedTo(ChronoUnit.SECONDS).toString());
        year.ifPresent(value -> filter.put("year", value));
        searchText.ifPresent(value -> filter.putObject("title").put("$contains", value));

        ApiResponse response = context.api().get("/events?filter=" + encode(filter.toString())
                + "&includeArchived=true&sort=-date&page=1&limit=" + limit);
        return answer(response, body -> {
            ObjectNode result = JsonProjection.object();
            result.set("events", JsonProjection.pickEach(body.get("data"), Projections.EVENT));
            JsonNode pagination = body.path("pagination");
            if (pagination.has("totalItems")) {
                result.set("totalItems", pagination.get("totalItems"));
            }
            return result;
        });
    }
}
