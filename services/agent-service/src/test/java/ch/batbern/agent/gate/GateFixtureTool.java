package ch.batbern.agent.gate;

import ch.batbern.agent.gateway.Operation;
import ch.batbern.agent.tools.AgentTool;
import ch.batbern.agent.tools.MailRecipient;
import ch.batbern.agent.tools.OperationClass;
import ch.batbern.agent.tools.OutboundMail;
import ch.batbern.agent.tools.PastEventQuery;
import ch.batbern.agent.tools.ToolContext;
import ch.batbern.agent.tools.ToolOutcome;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * A non-read tool that exists only in tests (the epic ships read tools only), one per class, to
 * drive the autonomy gate through the loop. It calls nothing when it runs; it records that it ran.
 *
 * <p>How it keeps the {@code outboundMail} contract: a mail fixture reads its recipients from the
 * input field {@code to} (an array of addresses) and marks as organizer exactly the addresses of
 * {@link #ORGANIZER_ADDRESSES}, which stands for the organizer side a real tool reads from the API;
 * no input field can make an address an organizer. A mail fixture without a usable {@code to}
 * throws, as a real tool must. With {@code topic} in its input it asks for the past events matching
 * that topic.
 */
public final class GateFixtureTool implements AgentTool {

    /** What a real tool would resolve as organizers (an organizer alias). */
    static final Set<String> ORGANIZER_ADDRESSES = Set.of("organizers@example.com");
    public static final int PAST_EVENT_LIMIT = 3;
    private static final ObjectMapper JSON = new ObjectMapper();

    private final String name;
    private final OperationClass operationClass;
    private final Operation operation;
    private final boolean mails;
    private final List<JsonNode> executed = new ArrayList<>();

    private GateFixtureTool(String name, OperationClass operationClass, String operation, boolean mails) {
        this.name = name;
        this.operationClass = operationClass;
        this.operation = Operation.of(operation);
        this.mails = mails;
    }

    /** {@code reversible}, no mail; undone by patching the session back. */
    public static GateFixtureTool reversible() {
        return new GateFixtureTool("rescheduleSession", OperationClass.REVERSIBLE,
                "PATCH /events/{eventCode}/sessions/{sessionSlug}", false);
    }

    /** {@code reply}: one mail in the requester's conversation. */
    public static GateFixtureTool reply() {
        return new GateFixtureTool("replyToRequester", OperationClass.REPLY,
                "POST /conversations/{conversationKey}/replies", true);
    }

    /** {@code binding}, no mail. */
    public static GateFixtureTool binding() {
        return new GateFixtureTool("createCompany", OperationClass.BINDING, "POST /companies", false);
    }

    /** {@code binding} mail to one speaker. */
    static GateFixtureTool bindingMail() {
        return new GateFixtureTool("inviteSpeaker", OperationClass.BINDING,
                "POST /events/{eventCode}/speakers/{speakerId}/send-invitation", true);
    }

    /** {@code audience}: a mail to the participants. */
    static GateFixtureTool audience() {
        return new GateFixtureTool("mailParticipants", OperationClass.AUDIENCE, "POST /events/{eventCode}/mails", true);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String description() {
        return "Gate fixture " + name;
    }

    @Override
    public JsonNode inputSchema() {
        return JSON.createObjectNode().put("type", "object");
    }

    @Override
    public OperationClass operationClass() {
        return operationClass;
    }

    @Override
    public Set<Operation> operations() {
        return Set.of(operation);
    }

    @Override
    public Optional<Operation> undoOperation() {
        return operationClass == OperationClass.REVERSIBLE ? Optional.of(operation) : Optional.empty();
    }

    @Override
    public Optional<PastEventQuery> pastEventQuery(JsonNode input) {
        JsonNode topic = input.path("topic");
        return topic.isTextual() ? Optional.of(PastEventQuery.matching(topic.asText(), PAST_EVENT_LIMIT))
                : Optional.empty();
    }

    @Override
    public Optional<OutboundMail> outboundMail(JsonNode input, ToolContext readOnly) {
        if (!mails) {
            return Optional.empty();
        }
        JsonNode to = input.path("to");
        if (!to.isArray() || to.isEmpty()) {
            throw new IllegalArgumentException("to is required");
        }
        List<MailRecipient> recipients = new ArrayList<>();
        for (JsonNode address : to) {
            if (!address.isTextual()) {
                throw new IllegalArgumentException("to holds addresses");
            }
            String value = address.asText();
            boolean organizer = ORGANIZER_ADDRESSES.contains(value.strip().toLowerCase(Locale.ROOT));
            recipients.add(new MailRecipient(value, organizer));
        }
        return Optional.of(new OutboundMail(recipients));
    }

    @Override
    public synchronized ToolOutcome execute(JsonNode input, ToolContext context) {
        executed.add(input);
        return ToolOutcome.ok("{\"done\":true}");
    }

    /** The inputs it ran with. */
    public synchronized List<JsonNode> executed() {
        return List.copyOf(executed);
    }
}
