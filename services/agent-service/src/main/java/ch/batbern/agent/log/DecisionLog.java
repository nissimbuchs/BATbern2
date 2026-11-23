package ch.batbern.agent.log;

import ch.batbern.agent.loop.LlmContent;
import ch.batbern.agent.loop.LlmRequest;
import ch.batbern.agent.loop.LlmResponse;
import ch.batbern.agent.run.AgentRunEntity;
import ch.batbern.agent.run.AgentRunRepository;
import ch.batbern.agent.run.PrincipalKind;
import ch.batbern.agent.run.RunEndReason;
import ch.batbern.agent.run.RunTrigger;
import ch.batbern.agent.tools.ToolOutcome;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * The agent decision log (R7, AD-9): every run, inbound message, step, model call and tool call, in
 * agent-service's own {@code agent_*} tables.
 *
 * <p>One transaction per write, each its own ({@code REQUIRES_NEW}): a row is committed when it
 * happens, so a run that crashes, or a caller that rolls back, still leaves its trace. Model and
 * tool calls are written before they run and completed after, so even a call that never returns is
 * in the log with its prompt or input.
 */
@Service
public class DecisionLog {

    private final AgentRunRepository runs;
    private final AgentInboundMessageRepository inboundMessages;
    private final AgentStepRepository steps;
    private final AgentModelCallRepository modelCalls;
    private final AgentToolCallRepository toolCalls;
    private final Clock clock;

    public DecisionLog(AgentRunRepository runs, AgentInboundMessageRepository inboundMessages,
                       AgentStepRepository steps, AgentModelCallRepository modelCalls,
                       AgentToolCallRepository toolCalls, Clock clock) {
        this.runs = runs;
        this.inboundMessages = inboundMessages;
        this.steps = steps;
        this.modelCalls = modelCalls;
        this.toolCalls = toolCalls;
        this.clock = clock;
    }

    /** A new RUNNING run; returns its id. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UUID startRun(RunTrigger trigger) {
        return runs.save(AgentRunEntity.started(UUID.randomUUID(), trigger, clock.instant())).getId();
    }

    /** Ends the run for {@code reason}; the reason fixes DONE, FAILED or HANDED_TO_ORGANIZERS. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void endRun(UUID runId, RunEndReason reason) {
        endRun(runId, reason, null);
    }

    /**
     * Ends the run for {@code reason} with its machine-readable {@code detail} (the CUMS code of a
     * refusal; {@code null} for none). A detail that is not a code is stored as UNKNOWN_CODE.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void endRun(UUID runId, RunEndReason reason, String detail) {
        runs.findById(runId).orElseThrow(() -> missing("run", runId)).end(reason, detail, clock.instant());
    }

    /**
     * The run acts under the CUMS grant {@code grantRunId} (story 4) for a principal of
     * {@code kind} (story 6). One grant, one run.
     *
     * @throws DataIntegrityViolationException when another run already acts under that grant
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordGrant(UUID runId, UUID grantRunId, PrincipalKind kind) {
        if (runs.existsByGrantRunId(grantRunId)) {
            throw new DataIntegrityViolationException("The grant is already used by another run");
        }
        runs.findById(runId).orElseThrow(() -> missing("run", runId)).recordGrant(grantRunId, kind);
        // Flush here, so a concurrent run with the same grant fails inside this call
        runs.flush();
    }

    /** The message the run was given, in full. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UUID recordInbound(UUID runId, InboundMessage message) {
        return inboundMessages.save(AgentInboundMessageEntity.of(runId, message,
                LlmJson.json(message.content()), clock.instant())).getId();
    }

    /** Step {@code number} (1-based) of the run begins. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UUID startStep(UUID runId, int number) {
        return steps.save(AgentStepEntity.started(runId, number, clock.instant())).getId();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void endStep(UUID stepId) {
        steps.findById(stepId).orElseThrow(() -> missing("step", stepId)).end(clock.instant());
    }

    /** The model call is about to be sent; stores the whole prompt. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UUID startModelCall(UUID runId, UUID stepId, LlmRequest request) {
        return modelCalls.save(AgentModelCallEntity.started(runId, stepId, LlmJson.prompt(request),
                clock.instant())).getId();
    }

    /** The model answered: the answer, the model id, the stop reason, the chosen tools, the tokens. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completeModelCall(UUID modelCallId, LlmResponse response) {
        modelCalls.findById(modelCallId).orElseThrow(() -> missing("model call", modelCallId))
                .succeed(LlmJson.response(response), LlmJson.storable(response.model()),
                        response.stopReason(), LlmJson.chosenTools(response), response.usage(), clock.instant());
    }

    /** The model call failed; the row keeps the error (class and message). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failModelCall(UUID modelCallId, Throwable error) {
        modelCalls.findById(modelCallId).orElseThrow(() -> missing("model call", modelCallId))
                .fail(describe(error), clock.instant());
    }

    /** The tool the model chose is about to run; stores its input. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UUID startToolCall(UUID runId, UUID stepId, LlmContent.ToolUse use) {
        return toolCalls.save(AgentToolCallEntity.started(runId, stepId, LlmJson.storable(use.id()),
                LlmJson.storable(use.name()), LlmJson.json(use.input()), clock.instant())).getId();
    }

    /** The tool returned: SUCCEEDED, or ERRORED when it reported an error itself. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completeToolCall(UUID toolCallId, ToolOutcome outcome) {
        toolCalls.findById(toolCallId).orElseThrow(() -> missing("tool call", toolCallId))
                .end(outcome.error() ? ToolCallStatus.ERRORED : ToolCallStatus.SUCCEEDED,
                        LlmJson.storable(outcome.content()), null, clock.instant());
    }

    /** The tool threw; the row keeps the error (class and message). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failToolCall(UUID toolCallId, Throwable error) {
        toolCalls.findById(toolCallId).orElseThrow(() -> missing("tool call", toolCallId))
                .end(ToolCallStatus.FAILED, null, describe(error), clock.instant());
    }

    /**
     * The autonomy gate held the call (story 7): PENDING_APPROVAL or REFUSED, with what the model was
     * told. The tool did not run.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void holdToolCall(UUID toolCallId, ToolCallStatus status, String toldTheModel) {
        if (status != ToolCallStatus.PENDING_APPROVAL && status != ToolCallStatus.REFUSED) {
            throw new IllegalArgumentException("A held call is PENDING_APPROVAL or REFUSED, not " + status);
        }
        toolCalls.findById(toolCallId).orElseThrow(() -> missing("tool call", toolCallId))
                .end(status, LlmJson.storable(toldTheModel), null, clock.instant());
    }

    /** The model asked for a tool the run does not offer; logged as asked, never run. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UUID recordUnknownTool(UUID runId, UUID stepId, LlmContent.ToolUse use, String error) {
        AgentToolCallEntity call = AgentToolCallEntity.started(runId, stepId, LlmJson.storable(use.id()),
                LlmJson.storable(use.name()), LlmJson.json(use.input()), clock.instant());
        call.end(ToolCallStatus.UNKNOWN_TOOL, null, LlmJson.storable(error), clock.instant());
        return toolCalls.save(call).getId();
    }

    /** The decision log keeps the message: it is the full record. The application log never does (#1202). */
    private static String describe(Throwable error) {
        return error.getMessage() == null
                ? error.getClass().getName()
                : LlmJson.storable(error.getClass().getName() + ": " + error.getMessage());
    }

    private static NoSuchElementException missing(String what, UUID id) {
        return new NoSuchElementException("No " + what + " " + id + " in the decision log");
    }
}
