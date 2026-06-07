package ch.batbern.gateway.client;

/**
 * Raised by {@link GatewayUserStatusClient} on a TRANSIENT failure of the CUMS user-status
 * lookup — a timeout, connection error, 5xx, or unexpected client error. It deliberately
 * does NOT cover a {@code 404} (user not provisioned), which the client signals as an empty
 * result instead.
 *
 * <p>The {@code AccountActiveFilter} treats this exception as fail-open (allow the request,
 * log a WARN, increment {@code gateway.active_gate.cums_error}) — consistent with every other
 * is_active/role path in the platform (PreAuthentication Lambda, JIT provisioning,
 * PreTokenGeneration all degrade open on infra error). Failing closed would let a CUMS hiccup
 * lock out the whole platform.
 */
public class GatewayUserStatusException extends RuntimeException {

    public GatewayUserStatusException(String message, Throwable cause) {
        super(message, cause);
    }
}
