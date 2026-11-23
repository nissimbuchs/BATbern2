package ch.batbern.agent.tools.read;

import ch.batbern.agent.gateway.Operation;
import ch.batbern.agent.tools.JsonProjection;
import ch.batbern.agent.tools.ToolContext;
import ch.batbern.agent.tools.ToolInput;
import ch.batbern.agent.tools.ToolOutcome;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Set;

/**
 * {@code listEventSessions}: the sessions of one event with their speakers
 * ({@code GET /events/{eventCode}/sessions}).
 */
public final class ListEventSessionsTool extends PublicApiReadTool {

    static final Operation LIST_SESSIONS = Operation.of("GET /events/{eventCode}/sessions");
    /** The spec's maximum page size: one page holds every session of an event. */
    static final int PAGE_SIZE = 100;

    @Override
    public String name() {
        return "listEventSessions";
    }

    @Override
    public String description() {
        return "Lists the sessions of one BATbern event: slug, title, type, times, room, language and "
                + "speakers (username, name, company, role).";
    }

    @Override
    public JsonNode inputSchema() {
        ObjectNode schema = JSON.createObjectNode().put("type", "object").put("additionalProperties", false);
        schema.putObject("properties").putObject("eventCode").put("type", "string")
                .put("pattern", ToolInput.EVENT_CODE.pattern()).put("description", "The event, e.g. BATbern56");
        schema.putArray("required").add("eventCode");
        return schema;
    }

    @Override
    public Set<Operation> operations() {
        return Set.of(LIST_SESSIONS);
    }

    @Override
    ToolOutcome read(JsonNode input, ToolContext context) {
        String eventCode = ToolInput.requiredId(input, "eventCode", ToolInput.EVENT_CODE);
        return answer(context.api().get("/events/" + eventCode + "/sessions?page=1&limit=" + PAGE_SIZE), body -> {
            ObjectNode result = JsonProjection.object().put("eventCode", eventCode);
            ArrayNode sessions = result.putArray("sessions");
            JsonNode data = body.get("data");
            if (data != null && data.isArray()) {
                data.forEach(session -> {
                    ObjectNode projected = JsonProjection.pick(session, Projections.SESSION);
                    projected.set("speakers", JsonProjection.pickEach(session.get("speakers"), Projections.SPEAKER));
                    sessions.add(projected);
                });
            }
            JsonNode pagination = body.path("pagination");
            if (pagination.has("totalItems")) {
                result.set("totalItems", pagination.get("totalItems"));
            }
            return result;
        });
    }
}
