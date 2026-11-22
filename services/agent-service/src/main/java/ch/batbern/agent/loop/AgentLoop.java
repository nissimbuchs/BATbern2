package ch.batbern.agent.loop;

import ch.batbern.agent.config.AgentProperties;
import ch.batbern.agent.gate.AutonomyGate;
import ch.batbern.agent.gate.GateMessages;
import ch.batbern.agent.gate.Verdict;
import ch.batbern.agent.gateway.PublicApi;
import ch.batbern.agent.identity.AgentCredentialsRefusedException;
import ch.batbern.agent.identity.CumsAgentClient;
import ch.batbern.agent.identity.RunCredentials;
import ch.batbern.agent.log.DecisionLog;
import ch.batbern.agent.precedent.ProposedCall;
import ch.batbern.agent.run.RunEndReason;
import ch.batbern.agent.run.RunLimit;
import ch.batbern.agent.tools.AgentTool;
import ch.batbern.agent.tools.ToolContext;
import ch.batbern.agent.tools.ToolOutcome;
import ch.batbern.agent.tools.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The agent loop (AD-14): the only caller of {@link LlmPort}. It sends the conversation to the model,
 * runs the tools the model chooses, sends their results back, and repeats until the model ends its
 * turn or the run must stop. Every model call, step and tool call is written to the
 * {@link DecisionLog} as it happens.
 *
 * <p>Before its first model call a run obtains its CUMS grant and mints its first agent token
 * (story 4, R2); its tools reach the public API only through a per-run {@link PublicApi}, which mints
 * again before every write.
 *
 * <p>The model is offered the tools of the {@link ToolRegistry} (story 5, AD-6), each with its class
 * derived from the operation class table; a tool's API lets through only the operations it declares.
 *
 * <p>Before every non-read tool call the {@link AutonomyGate} decides (story 7, AD-7): the call is
 * logged, the gate writes its verdict, and only a RUN verdict runs the tool. A call held for an
 * organizer's approval or refused ends PENDING_APPROVAL or REFUSED without running; the model gets
 * {@link GateMessages#toModel} as the tool result and the run goes on. Read calls skip the gate.
 *
 * <p>A run ends:
 * <ul>
 *   <li>DONE when the model ends its turn;</li>
 *   <li>HANDED_TO_ORGANIZERS with GRANT_REFUSED or TOKEN_REFUSED and the CUMS code as end detail
 *       when CUMS refuses the grant, the first mint or a mint before a write (no further model
 *       call; a refused write is never sent);</li>
 *   <li>HANDED_TO_ORGANIZERS when the model is unreachable (no retry), its output is cut off, or it
 *       refuses or stops otherwise;</li>
 *   <li>HANDED_TO_ORGANIZERS with CAP_REACHED and the {@link RunLimit} as end detail when a limit of
 *       {@code agent.limits} is reached (story 8, R9): before every model call the loop checks the
 *       run's steps, the run's tokens and today's tokens of all runs against what is already
 *       recorded, so the call that crosses a token cap completes and no further call follows; the
 *       gate refuses the {@code reply} call past the reply cap, and the run ends there, without the
 *       answer's remaining tools;</li>
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

    /** End detail of a run whose grant another run already acts under. */
    public static final String GRANT_ALREADY_USED = "GRANT_ALREADY_USED";

    private final LlmPort llm;
    private final DecisionLog decisionLog;
    private final AgentProperties properties;
    private final CumsAgentClient cums;
    private final RestTemplate restTemplate;
    private final ToolRegistry registry;
    private final AutonomyGate gate;
    private final TokenLedger tokens;
    private final Clock clock;

    public AgentLoop(LlmPort llm, DecisionLog decisionLog, AgentProperties properties, CumsAgentClient cums,
                     RestTemplate restTemplate, ToolRegistry registry, AutonomyGate gate, TokenLedger tokens,
                     Clock clock) {
        this.llm = llm;
        this.decisionLog = decisionLog;
        this.properties = properties;
        this.cums = cums;
        this.restTemplate = restTemplate;
        this.registry = registry;
        this.gate = gate;
        this.tokens = tokens;
        this.clock = clock;
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
        List<AgentTool> offered = registry.tools();
        UUID runId = decisionLog.startRun(request.trigger());
        LOG.info("Agent run {} started: trigger={} tools={}", runId, request.trigger(), offered.size());
        StepCounter counter = new StepCounter();
        RunEnd end;
        try {
            end = loop(runId, request, offered, counter);
        } catch (RuntimeException e) {
            // Class only: the message may carry prompt or tool text (#1202)
            LOG.error("Agent run {} failed in step {}: {}", runId, counter.steps, e.getClass().getName());
            end = new RunEnd(RunEndReason.INTERNAL_ERROR, null);
        } catch (Error e) {
            // The run must not stay RUNNING; end it, then let the Error go on
            LOG.error("Agent run {} failed in step {}: {}", runId, counter.steps, e.getClass().getName());
            decisionLog.endRun(runId, RunEndReason.INTERNAL_ERROR);
            throw e;
        }
        RunEndReason reason = end.reason();
        decisionLog.endRun(runId, reason, end.detail());
        LOG.info("Agent run {} ended {} ({}) after {} steps", runId, reason.status(), reason, counter.steps);
        return new AgentRunResult(runId, reason.status(), reason, end.detail(), counter.steps);
    }

    private RunEnd loop(UUID runId, AgentRunRequest request, List<AgentTool> offered, StepCounter counter) {
        if (request.inbound() != null) {
            decisionLog.recordInbound(runId, request.inbound());
        }
        // The run's identity before its first model call: a refused grant costs no model tokens
        RunCredentials credentials;
        try {
            credentials = RunCredentials.obtain(cums, request.grant(), clock);
            try {
                decisionLog.recordGrant(runId, credentials.grantRunId(), credentials.principalKind());
            } catch (DataIntegrityViolationException e) {
                // A handed-over grant delivered twice, or a reused run id: one grant, one run
                throw new AgentCredentialsRefusedException(RunEndReason.GRANT_REFUSED, GRANT_ALREADY_USED);
            }
            credentials.start();
        } catch (AgentCredentialsRefusedException e) {
            return refused(runId, e);
        }
        PublicApi api = new PublicApi(restTemplate, properties.gateway().baseUrl(), credentials);
        Map<String, AgentTool> tools = new LinkedHashMap<>();
        offered.forEach(tool -> tools.put(tool.name(), tool));
        List<LlmToolSpec> specs = offered.stream()
                .map(tool -> new LlmToolSpec(tool.name(), tool.description(), tool.inputSchema()))
                .toList();
        List<LlmMessage> messages = new ArrayList<>();
        messages.add(LlmMessage.user(request.userMessage()));
        int maxOutputTokens = properties.loop().maxOutputTokens();

        while (true) {
            Optional<RunLimit> limit = limitBeforeModelCall(runId, counter.steps);
            if (limit.isPresent()) {
                return capReached(limit.get());
            }
            counter.steps++;
            UUID stepId = decisionLog.startStep(runId, counter.steps);
            try {
                LlmRequest llmRequest = new LlmRequest(request.system(), messages, specs, maxOutputTokens);
                UUID modelCallId = decisionLog.startModelCall(runId, stepId, llmRequest);
                LlmResponse response;
                try {
                    response = llm.complete(llmRequest);
                } catch (LlmUnavailableException e) {
                    decisionLog.failModelCall(modelCallId, e);
                    LOG.warn("Agent run {} step {}: model unavailable ({})", runId, counter.steps,
                            e.getClass().getName());
                    return new RunEnd(RunEndReason.MODEL_UNAVAILABLE, null);
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
                        return new RunEnd(RunEndReason.COMPLETED, null);
                    case MAX_TOKENS:
                        return new RunEnd(RunEndReason.MODEL_MAX_TOKENS, null);
                    case TOOL_USE:
                        if (uses.isEmpty()) {
                            return new RunEnd(RunEndReason.MODEL_STOPPED, null);
                        }
                        ToolRound round = runTools(runId, stepId, counter.steps, uses, tools, api, credentials);
                        // Read from the credentials, not from an exception a tool may have swallowed
                        Optional<AgentCredentialsRefusedException> refusal = credentials.refusal();
                        if (refusal.isPresent()) {
                            return refused(runId, refusal.get());
                        }
                        if (round.capReached() != null) {
                            // The gate refuses the reply once the run's replies that ran equal the cap
                            logged(runId, round.capReached(), configured(round.capReached()));
                            return capReached(round.capReached());
                        }
                        messages.add(LlmMessage.assistant(response.content()));
                        messages.add(LlmMessage.user(round.results()));
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
                        return new RunEnd(RunEndReason.MODEL_STOPPED, null);
                }
            } finally {
                decisionLog.endStep(stepId);
            }
        }
    }

    /**
     * The limit a further model call would go past, checked in this order: steps, the run's tokens,
     * today's tokens of all runs. Logs the limit, what was used and the configured value; nothing else.
     */
    private Optional<RunLimit> limitBeforeModelCall(UUID runId, int stepsTaken) {
        AgentProperties.Limits limits = properties.limits();
        if (stepsTaken >= limits.runSteps()) {
            return logged(runId, RunLimit.STEPS, stepsTaken);
        }
        if (stepsTaken > 0) {
            long runTokens = tokens.ofRun(runId);
            if (runTokens >= limits.runTokens()) {
                return logged(runId, RunLimit.RUN_TOKENS, runTokens);
            }
        }
        long todayTokens = tokens.ofToday();
        if (todayTokens >= limits.dailyTokens()) {
            return logged(runId, RunLimit.DAILY_TOKENS, todayTokens);
        }
        return Optional.empty();
    }

    private Optional<RunLimit> logged(UUID runId, RunLimit limit, long used) {
        LOG.warn("Agent run {} handed to the organizers: limit {} reached (used {}, configured {})", runId, limit,
                used, configured(limit));
        return Optional.of(limit);
    }

    private long configured(RunLimit limit) {
        AgentProperties.Limits limits = properties.limits();
        return switch (limit) {
            case STEPS -> limits.runSteps();
            case RUN_TOKENS -> limits.runTokens();
            case REPLIES -> limits.runReplies();
            case DAILY_TOKENS -> limits.dailyTokens();
        };
    }

    private static RunEnd capReached(RunLimit limit) {
        return new RunEnd(RunEndReason.CAP_REACHED, limit.name());
    }

    private RunEnd refused(UUID runId, AgentCredentialsRefusedException refusal) {
        // Run id, reason and code only: never a username, token or secret
        LOG.warn("Agent run {} handed to the organizers: {} {}", runId, refusal.reason(), refusal.code());
        return new RunEnd(refusal.reason(), refusal.code());
    }

    /**
     * Runs the tools of one model answer in order. Stops after a tool during which CUMS refused a
     * mint: that tool call is FAILED (even if the tool returned normally), the rest are not run.
     * Stops likewise after a call the gate refused because a limit is reached (CAP_REACHED).
     */
    private ToolRound runTools(UUID runId, UUID stepId, int step,
                                                 List<LlmContent.ToolUse> uses, Map<String, AgentTool> tools,
                                                 PublicApi api, RunCredentials credentials) {
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
            ToolOutcome outcome = null;
            Verdict verdict = null;
            try {
                if (AutonomyGate.gates(tool)) {
                    // The gate gets the run's API: its precedent read is not one of the tool's operations
                    verdict = gate.decide(new ProposedCall(runId, tool, use.input()), toolCallId, api);
                }
                if (verdict == null || verdict.runs()) {
                    // The tool reaches only the operations its class was derived from
                    ToolContext context = new ToolContext(runId, api.limitedTo(tool.operations()));
                    outcome = tool.execute(use.input(), context);
                }
            } catch (RuntimeException e) {
                decisionLog.failToolCall(toolCallId, credentials.refusal().map(r -> (RuntimeException) r).orElse(e));
                LOG.warn("Agent run {} step {}: tool {} failed ({})", runId, step, tool.name(),
                        e.getClass().getName());
                results.add(new LlmContent.ToolResult(use.id(),
                        "The tool failed (" + e.getClass().getSimpleName() + ").", true));
                if (credentials.refusal().isPresent()) {
                    break;
                }
                continue;
            }
            Optional<AgentCredentialsRefusedException> refusal = credentials.refusal();
            if (refusal.isPresent()) {
                // The tool swallowed the refusal: its call still failed, and the run ends
                decisionLog.failToolCall(toolCallId, refusal.get());
                LOG.warn("Agent run {} step {}: tool {} failed ({})", runId, step, tool.name(),
                        refusal.get().getClass().getName());
                break;
            }
            if (verdict != null && !verdict.runs()) {
                // Held by the gate: the tool did not run
                String told = GateMessages.toModel(verdict);
                decisionLog.holdToolCall(toolCallId, verdict.heldStatus(), told);
                LOG.info("Agent run {} step {}: tool {} held by the gate ({}, {})", runId, step, tool.name(),
                        verdict.outcome(), verdict.reason());
                results.add(new LlmContent.ToolResult(use.id(), told, GateMessages.isError(verdict)));
                if (verdict.cap() != null) {
                    return new ToolRound(results, verdict.cap());
                }
                continue;
            }
            decisionLog.completeToolCall(toolCallId, outcome);
            LOG.info("Agent run {} step {}: tool {} {}", runId, step, tool.name(),
                    outcome.error() ? "returned an error" : "succeeded");
            results.add(new LlmContent.ToolResult(use.id(), outcome.content(), outcome.error()));
        }
        return new ToolRound(results, null);
    }

    /** The tool results of one answer, and the limit a refused call reached ({@code null} for none). */
    private record ToolRound(List<LlmContent.ToolResult> results, RunLimit capReached) {
    }

    /** How a run ends: the reason and its machine-readable detail ({@code null} for none). */
    private record RunEnd(RunEndReason reason, String detail) {
    }

    /** The steps a run has taken, readable after a fault. */
    private static final class StepCounter {
        private int steps;
    }
}
