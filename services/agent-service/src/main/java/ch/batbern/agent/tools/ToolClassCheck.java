package ch.batbern.agent.tools;

import ch.batbern.agent.gateway.Operation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The build check of the tool registry (AD-6, R4): a tool's class is derived from the operation
 * class table, never chosen. {@link ToolRegistry} runs it at startup and refuses to start on any
 * violation; {@code ToolClassCheckTest} runs it on the shipped registry in the normal test suite,
 * so the build fails first.
 *
 * <p>A tool violates the check when
 * <ul>
 *   <li>it calls an operation, or names an undo operation, that the table does not list;</li>
 *   <li>its declared class is not the highest class of its operations ({@code read} for a tool
 *       calling none): below is an understated tool, above is a class chosen, not derived;</li>
 *   <li>it is {@code reversible} without an undo operation, or names one without being
 *       {@code reversible};</li>
 *   <li>its name is not camelCase verb plus object ({@code findPastEvents}), or another tool has it.</li>
 * </ul>
 */
public final class ToolClassCheck {

    /** camelCase, at least two words: a verb and an object. */
    private static final Pattern NAME = Pattern.compile("^[a-z]+(?:[A-Z][a-z0-9]*)+$");

    private ToolClassCheck() {
    }

    /**
     * Every violation of {@code tools} against {@code table}, one message each, naming the tool;
     * empty when the tools pass.
     */
    public static List<String> violations(Collection<? extends AgentTool> tools, OperationClassTable table) {
        List<String> violations = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (AgentTool tool : tools) {
            String name = tool.name();
            if (name == null || !NAME.matcher(name).matches()) {
                violations.add("Tool " + name + ": the name is not camelCase verb plus object");
            }
            if (!names.add(name)) {
                violations.add("Tool " + name + ": another tool has the same name");
            }
            violations.addAll(classViolations(tool, table));
        }
        return List.copyOf(violations);
    }

    private static List<String> classViolations(AgentTool tool, OperationClassTable table) {
        List<String> violations = new ArrayList<>();
        String name = tool.name();
        OperationClass declared = tool.operationClass();
        if (declared == null) {
            return List.of("Tool " + name + ": no class declared");
        }
        OperationClass highest = OperationClass.READ;
        Operation highestOperation = null;
        boolean complete = true;
        for (Operation operation : tool.operations()) {
            Optional<OperationClass> operationClass = table.classOf(operation);
            if (operationClass.isEmpty()) {
                violations.add("Tool " + name + " calls " + operation
                        + ", which the operation class table does not list");
                complete = false;
            } else if (highestOperation == null || operationClass.get().compareTo(highest) > 0) {
                highest = operationClass.get();
                highestOperation = operation;
            }
        }
        if (complete && declared.compareTo(highest) < 0) {
            violations.add("Tool " + name + " is declared " + declared.wireName() + " but calls " + highestOperation
                    + ", which is " + highest.wireName());
        } else if (complete && declared.compareTo(highest) > 0) {
            violations.add("Tool " + name + " is declared " + declared.wireName()
                    + " but its operations are at most " + highest.wireName() + "; the class is derived, not chosen");
        }
        Optional<Operation> undo = tool.undoOperation();
        if (declared == OperationClass.REVERSIBLE && undo.isEmpty()) {
            violations.add("Tool " + name + " is reversible but names no undo operation");
        }
        if (declared != OperationClass.REVERSIBLE && undo.isPresent()) {
            violations.add("Tool " + name + " names an undo operation but is " + declared.wireName()
                    + ", not reversible");
        }
        if (undo.isPresent() && table.classOf(undo.get()).isEmpty()) {
            violations.add("Tool " + name + " names undo operation " + undo.get()
                    + ", which the operation class table does not list");
        }
        return violations;
    }
}
