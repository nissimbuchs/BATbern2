package ch.batbern.agent.identity;

import java.time.Instant;
import java.util.Objects;

/**
 * A delegated agent token minted by CUMS: a bearer credential, never logged. {@code toString} shows
 * only its expiry.
 *
 * @param token     the compact JWS
 * @param expiresAt its {@code exp}
 */
public record AgentToken(String token, Instant expiresAt) {

    public AgentToken {
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(expiresAt, "expiresAt");
    }

    @Override
    public String toString() {
        return "AgentToken[token=****, expiresAt=" + expiresAt + "]";
    }
}
