package ch.batbern.agent.tools.read;

import ch.batbern.agent.gateway.Operation;
import ch.batbern.agent.tools.JsonProjection;
import ch.batbern.agent.tools.ToolContext;
import ch.batbern.agent.tools.ToolInput;
import ch.batbern.agent.tools.ToolOutcome;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Set;

/**
 * {@code listSessionSpeakers}: the speakers of one session
 * ({@code GET /events/{eventCode}/sessions/{sessionSlug}/speakers}).
 */
public final class ListSessionSpeakersTool extends PublicApiReadTool {

    static final Operation LIST_SESSION_SPEAKERS =
            Operation.of("GET /events/{eventCode}/sessions/{sessionSlug}/speakers");

    @Override
    public String name() {
        return "listSessionSpeakers";
    }

    @Override
    public String description() {
        return "Lists the speakers of one session of a BATbern event: username, name, company, role, "
                + "whether confirmed, and presentation title.";
    }

    @Override
    public JsonNode inputSchema() {
        ObjectNode schema = JSON.createObjectNode().put("type", "object").put("additionalProperties", false);
        ObjectNode properties = schema.putObject("properties");
        properties.putObject("eventCode").put("type", "string").put("pattern", ToolInput.EVENT_CODE.pattern())
                .put("description", "The event, e.g. BATbern56");
        properties.putObject("sessionSlug").put("type", "string").put("pattern", ToolInput.SESSION_SLUG.pattern())
                .put("description", "The session's slug, as listEventSessions returns it");
        schema.putArray("required").add("eventCode").add("sessionSlug");
        return schema;
    }

    @Override
    public Set<Operation> operations() {
        return Set.of(LIST_SESSION_SPEAKERS);
    }

    @Override
    ToolOutcome read(JsonNode input, ToolContext context) {
        String eventCode = ToolInput.requiredId(input, "eventCode", ToolInput.EVENT_CODE);
        String sessionSlug = ToolInput.requiredId(input, "sessionSlug", ToolInput.SESSION_SLUG);
        return answer(context.api().get("/events/" + eventCode + "/sessions/" + sessionSlug + "/speakers"),
                body -> {
                    ObjectNode result = JsonProjection.object().put("eventCode", eventCode)
                            .put("sessionSlug", sessionSlug);
                    result.set("speakers", JsonProjection.pickEach(body, Projections.SPEAKER));
                    return result;
                });
    }
}
