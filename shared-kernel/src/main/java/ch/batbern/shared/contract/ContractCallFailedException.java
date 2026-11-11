package ch.batbern.shared.contract;

import org.springframework.web.client.HttpStatusCodeException;

/**
 * An implementer did not take a contract call: no answer (connection refused, timeout) or a status
 * other than success and 409. The message names contract, implementer and cause, never a body.
 */
public class ContractCallFailedException extends RuntimeException {

    private final String contract;
    private final String implementer;

    public ContractCallFailedException(String contract, String implementer, Throwable cause) {
        super("contract " + contract + ": " + implementer + " failed (" + reason(cause) + ")", cause);
        this.contract = contract;
        this.implementer = implementer;
    }

    private static String reason(Throwable cause) {
        return cause instanceof HttpStatusCodeException status
                ? "status " + status.getStatusCode().value()
                : cause.getClass().getSimpleName();
    }

    public String contract() {
        return contract;
    }

    public String implementer() {
        return implementer;
    }
}
