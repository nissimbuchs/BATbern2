package ch.batbern.agent.tools;

import ch.batbern.agent.gateway.Operation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The one table giving the class of each public operation a tool may call (AD-6, R4). A tool's
 * class is the highest class among its operations; {@link ToolClassCheck} refuses a tool declared
 * otherwise, or one calling an operation this table does not list. Every entry is an operation of
 * {@code docs/api/*.openapi.yml} (checked by a test).
 *
 * <p>Add an operation here in the same change as the first tool that calls it, with the class the
 * architecture gives it: {@code reply} for one mail to the run's requester, {@code binding} for any
 * other mail to a non-organizer or a change no operation can take back, {@code audience} for a mail
 * reaching more than one non-organizer.
 */
public final class OperationClassTable {

    private static final OperationClassTable STANDARD = new OperationClassTable(Map.of(
            // Past events, sessions and speakers (story 5): public reads, open to every principal
            Operation.of("GET /events"), OperationClass.READ,
            Operation.of("GET /events/{eventCode}/sessions"), OperationClass.READ,
            Operation.of("GET /events/{eventCode}/sessions/{sessionSlug}/speakers"), OperationClass.READ));

    private final Map<Operation, OperationClass> classes;

    private OperationClassTable(Map<Operation, OperationClass> classes) {
        this.classes = Collections.unmodifiableMap(new LinkedHashMap<>(classes));
    }

    /** The table agent-service ships. */
    public static OperationClassTable standard() {
        return STANDARD;
    }

    /** This table plus {@code more} (tests: fixture operations beside the shipped ones). */
    public OperationClassTable with(Map<Operation, OperationClass> more) {
        Map<Operation, OperationClass> merged = new LinkedHashMap<>(classes);
        more.forEach((operation, operationClass) -> {
            OperationClass previous = merged.put(Objects.requireNonNull(operation),
                    Objects.requireNonNull(operationClass));
            if (previous != null && previous != operationClass) {
                throw new IllegalArgumentException("Two classes for " + operation);
            }
        });
        return new OperationClassTable(merged);
    }

    /** The class of {@code operation}, empty when the table does not list it. */
    public Optional<OperationClass> classOf(Operation operation) {
        return Optional.ofNullable(classes.get(operation));
    }

    /** Every listed operation with its class. */
    public Map<Operation, OperationClass> entries() {
        return classes;
    }
}
