package ch.batbern.shared.contract;

/**
 * An implementer refused a contract call with 409 (a holder blocks a merge, for instance). The
 * message is the implementer's {@code message}, fit to show to the user who acted.
 */
public class ContractRefusedException extends RuntimeException {

    private final String contract;
    private final String implementer;

    public ContractRefusedException(String contract, String implementer, String message) {
        super(message);
        this.contract = contract;
        this.implementer = implementer;
    }

    public String contract() {
        return contract;
    }

    public String implementer() {
        return implementer;
    }
}
