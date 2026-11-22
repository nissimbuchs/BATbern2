package ch.batbern.agent.loop;

import ch.batbern.agent.config.AgentProperties;
import ch.batbern.agent.log.DecisionLog;
import ch.batbern.agent.run.RunEndReason;
import ch.batbern.agent.tools.AgentTool;
import ch.batbern.agent.tools.ToolOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The agent loop (AD-14): the only caller of {@link LlmPort}. It sends the conversation to the model,
 * runs the tools the model chooses, sends their results back, and repeats until the model ends its
 * turn or the run must stop. Every model call, step and tool call is written to the
 * {@link DecisionLog} as it happens.
 *
 * <p>A run ends:
 * <ul>
 *   <li>DONE when the model ends its turn;</li>
 *   <li>HANDED_TO_ORGANIZERS when the model is unreachable (no retry), its output is cut off, it
 *       refuses or stops otherwise, or it is still calling tools after {@code agent.loop.max-steps}
 *       model calls;</li>
 *   <li>FAILED on a fault in agent-service itself.</li>
 * </ul>
 * A tool that throws or is unknown goes back to the model as an error tool_result; the run goes on.
 *
 * <p>The application log carries ids, counts, tool names and outcomes only: never prompt text, tool
 * input or output, or an exception message (#1202). Those are in the decision log.
 */
@Service
public class AgentLoop {

    private static final Logger LOG = LoggerFactory.getLogger(AgentLoop.class);

    private final LlmPort llm;
    private final DecisionLog decisionLog;
    private final AgentProperties properties;

    public AgentLoop(LlmPort llm, DecisionLog decisionLog, AgentProperties properties) {
        this.llm = llm;
        this.decisionLog = decisionLog;
        this.properties = properties;
    }

    /**
     * Runs the agent until it is done or must stop.
     *
     * @throws AgentSwitchedOffException while {@code agent.enabled} is false; no run is written
     */
    public AgentRunResult run(AgentRunRequest request) {
        if (!properties.enabled()) {
            throw new AgentSwitchedOffException();
        }
        UUID runId = decisionLog.startRun(request.trigger());
        LOG.info("Agent run {} started: trigger={} tools={}", runId, request.trigger(), request.tools().size());
        StepCounter counter = new StepCounter();
        RunEndReason reason;
        try {
            reason = loop(runId, request, counter);
        } catch (RuntimeException e) {
            // Class only: the message may carry prompt or tool text (#1202)
            LOG.error("Agent run {} failed in step {}: {}", runId, counter.steps, e.getClass().getName());
            reason = RunEndReason.INTERNAL_ERROR;
        } catch (Error e) {
            // The run must not stay RUNNING; end it, then let the Error go on
            LOG.error("Agent run {} failed in step {}: {}", runId, counter.steps, e.getClass().getName());
            decisionLog.endRun(runId, RunEndReason.INTERNAL_ERROR);
            throw e;
        }
        decisionLog.endRun(runId, reason);
        LOG.info("Agent run {} ended {} ({}) after {} steps", runId, reason.status(), reason, counter.steps);
        return new AgentRunResult(runId, reason.status(), reason, counter.steps);
    }

    private RunEndReason loop(UUID runId, AgentRunRequest request, StepCounter counter) {
        if (request.inbound() != null) {
            decisionLog.recordInbound(runId, request.inbound());
        }
        Map<String, AgentTool> tools = new LinkedHashMap<>();
        request.tools().forEach(tool -> tools.put(tool.name(), tool));
        List<LlmToolSpec> specs = request.tools().stream()
                .map(tool -> new LlmToolSpec(tool.name(), tool.description(), tool.inputSchema()))
                .toList();
        List<LlmMessage> messages = new ArrayList<>();
        messages.add(LlmMessage.user(request.userMessage()));
        AgentProperties.Loop limits = properties.loop();

        while (true) {
            if (counter.steps >= limits.maxSteps()) {
                LOG.warn("Agent run {} reached the step limit of {}", runId, limits.maxSteps());
                return RunEndReason.STEP_LIMIT;
            }
            counter.steps++;
            UUID stepId = decisionLog.startStep(runId, counter.steps);
            try {
                LlmRequest llmRequest = new LlmRequest(request.system(), messages, specs, limits.maxOutputTokens());
                UUID modelCallId = decisionLog.startModelCall(runId, stepId, llmRequest);
                LlmResponse response;
                try {
                    response = llm.complete(llmRequest);
                } catch (LlmUnavailableException e) {
                    decisionLog.failModelCall(modelCallId, e);
                    LOG.warn("Agent run {} step {}: model unavailable ({})", runId, counter.steps,
                            e.getClass().getName());
                    return RunEndReason.MODEL_UNAVAILABLE;
                } catch (RuntimeException e) {
                    // Any other fault of the port is agent-service's own: the call is failed, the run ends FAILED
                    decisionLog.failModelCall(modelCallId, e);
                    throw e;
                }
                decisionLog.completeModelCall(modelCallId, response);
                List<LlmContent.ToolUse> uses = response.toolUses();
                LOG.info("Agent run {} step {}: model stop={} tool_uses={} input_tokens={} output_tokens={}",
                        runId, counter.steps, response.stopReason(), uses.size(),
                        response.usage().inputTokens(), response.usage().outputTokens());

                switch (response.stopReason()) {
                    case END_TURN:
                        return RunEndReason.COMPLETED;
                    case MAX_TOKENS:
                        return RunEndReason.MODEL_MAX_TOKENS;
                    case TOOL_USE:
                        if (uses.isEmpty()) {
                            return RunEndReason.MODEL_STOPPED;
                        }
                        messages.add(LlmMessage.assistant(response.content()));
                        messages.add(LlmMessage.user(runTools(runId, stepId, counter.steps, uses, tools)));
                        break;
                    case PAUSE_TURN:
                        // agent-service declares no server tools, so the API has nothing to pause
                        // for; resuming blind would act on a turn we cannot see. Handed over.
                    case STOP_SEQUENCE:
                        // No stop sequences are sent
                    case REFUSAL:
                        // A refusal goes to the organizers; there is no fallback model (story 3)
                    case UNKNOWN:
                    default:
                        // The exact stop reason is in agent_model_calls; the end reason stays one
                        // value (the V2 check constraint is frozen)
                        return RunEndReason.MODEL_STOPPED;
                }
            } finally {
                decisionLog.endStep(stepId);
            }
        }
    }

    private List<LlmContent.ToolResult> runTools(UUID runId, UUID stepId, int step,
                                                 List<LlmContent.ToolUse> uses, Map<String, AgentTool> tools) {
        List<LlmContent.ToolResult> results = new ArrayList<>();
        for (LlmContent.ToolUse use : uses) {
            AgentTool tool = tools.get(use.name());
            if (tool == null) {
                String error = "Unknown tool. This run offers: " + String.join(", ", tools.keySet());
                decisionLog.recordUnknownTool(runId, stepId, use, error);
                // The name is the model's text, not a tool of ours: not logged
                LOG.warn("Agent run {} step {}: the model asked for a tool the run does not offer", runId, step);
                results.add(new LlmContent.ToolResult(use.id(), error, true));
                continue;
            }
            UUID toolCallId = decisionLog.startToolCall(runId, stepId, use);
            ToolOutcome outcome;
            try {
                outcome = tool.execute(use.input());
            } catch (RuntimeException e) {
                decisionLog.failToolCall(toolCallId, e);
                LOG.warn("Agent run {} step {}: tool {} failed ({})", runId, step, tool.name(),
                        e.getClass().getName());
                results.add(new LlmContent.ToolResult(use.id(),
                        "The tool failed (" + e.getClass().getSimpleName() + ").", true));
                continue;
            }
            decisionLog.completeToolCall(toolCallId, outcome);
            LOG.info("Agent run {} step {}: tool {} {}", runId, step, tool.name(),
                    outcome.error() ? "returned an error" : "succeeded");
            results.add(new LlmContent.ToolResult(use.id(), outcome.content(), outcome.error()));
        }
        return results;
    }

    /** The steps a run has taken, readable after a fault. */
    private static final class StepCounter {
        private int steps;
    }
}
