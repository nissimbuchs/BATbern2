package ch.batbern.agent.gate;

import ch.batbern.agent.gateway.Operation;
import ch.batbern.agent.gateway.PublicApi;
import ch.batbern.agent.identity.AgentCredentialsRefusedException;
import ch.batbern.agent.precedent.PrecedentRetrieval;
import ch.batbern.agent.precedent.Precedents;
import ch.batbern.agent.precedent.ProposedCall;
import ch.batbern.agent.tools.AgentTool;
import ch.batbern.agent.tools.OperationClass;
import ch.batbern.agent.tools.ToolContext;
import ch.batbern.agent.tools.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The one autonomy gate (story 7, R5, R6, AD-7, AD-8): the agent loop asks it before every non-read
 * tool call, after logging the call and before running it. Only a {@link VerdictOutcome#RUN}
 * verdict lets the tool run.
 *
 * <p>For a proposed call it (1) asks the tool for the mail the call would send
 * ({@code AgentTool.outboundMail}, with an API limited to the tool's {@code read} operations),
 * (2) retrieves the call's precedents with the run's API ({@link PrecedentRetrieval}), and (3) has
 * {@link GateLedger} decide by {@link GateRules} and record the verdict, its inputs and its cited
 * precedents in one transaction. Nothing the model writes and nothing in the inbound message is an
 * input: an approval can only come from the decision log.
 *
 * <p>The application log carries the run id, the tool name, the class, the outcome, the reason and
 * counts; never a recipient or the input (#1202).
 */
@Service
public class AutonomyGate {

    private static final Logger LOG = LoggerFactory.getLogger(AutonomyGate.class);

    private final PrecedentRetrieval precedents;
    private final GateLedger ledger;
    private final ToolRegistry registry;
    private final ObjectProvider<GateCaps> caps;

    public AutonomyGate(PrecedentRetrieval precedents, GateLedger ledger, ToolRegistry registry,
                        ObjectProvider<GateCaps> caps) {
        this.precedents = precedents;
        this.ledger = ledger;
        this.registry = registry;
        this.caps = caps;
    }

    /** Whether a call of {@code tool} goes through the gate: every tool but a {@code read} one. */
    public static boolean gates(AgentTool tool) {
        return tool.operationClass() != OperationClass.READ;
    }

    /**
     * Decides {@code call}, logged as {@code toolCallId}, and writes the verdict.
     *
     * @param runApi the run's public API (its token, not limited to the tool), for the precedent read
     * @throws IllegalArgumentException          for a {@code read} tool (read calls skip the gate)
     * @throws AgentCredentialsRefusedException when CUMS refuses a mint the gate's reads need
     */
    public Verdict decide(ProposedCall call, UUID toolCallId, PublicApi runApi) {
        AgentTool tool = call.tool();
        if (!gates(tool)) {
            throw new IllegalArgumentException("Read calls skip the gate");
        }
        GateLedger.MailResolution mail = resolveMail(call, runApi);
        Precedents found = precedents.retrieve(call, runApi);
        Verdict verdict = ledger.decideAndRecord(call.runId(), toolCallId, tool, mail, found,
                caps.getIfAvailable(() -> GateCaps.NONE));
        LOG.info("Gate run {} tool {}: class={} effective={} outcome={} reason={} cited={}", call.runId(),
                tool.name(), tool.operationClass(), verdict.effectiveClass(), verdict.outcome(), verdict.reason(),
                verdict.cited().size());
        return verdict;
    }

    private GateLedger.MailResolution resolveMail(ProposedCall call, PublicApi runApi) {
        AgentTool tool = call.tool();
        try {
            ToolContext readOnly = new ToolContext(call.runId(), runApi.limitedTo(readOperations(tool)));
            return GateLedger.MailResolution.of(tool.outboundMail(call.input(), readOnly));
        } catch (AgentCredentialsRefusedException e) {
            throw e;
        } catch (RuntimeException e) {
            // Bad input, an unreachable lookup or a fault of the tool: the mail is unknown, so it does
            // not run. Class only: the message may carry input (#1202)
            LOG.warn("Gate run {} tool {}: mail recipients unresolved ({})", call.runId(), tool.name(),
                    e.getClass().getName());
            return GateLedger.MailResolution.UNRESOLVED;
        }
    }

    /** The tool's operations the table classes {@code read}: all a recipient lookup may call. */
    private Set<Operation> readOperations(AgentTool tool) {
        return tool.operations().stream()
                .filter(operation -> registry.table().classOf(operation).orElse(null) == OperationClass.READ)
                .collect(Collectors.toUnmodifiableSet());
    }
}
