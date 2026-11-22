package ch.batbern.companyuser.dto;

import java.time.Instant;

/**
 * The 200 of {@code POST /internal/agent-tokens} ({@code AgentToken} in
 * {@code cums-internal.openapi.yml}).
 *
 * @param token     the signed agent token, a bearer credential
 * @param expiresAt its {@code exp}
 */
public record AgentTokenResponse(String token, Instant expiresAt) {

    /** Never prints the token. */
    @Override
    public String toString() {
        return "AgentTokenResponse[expiresAt=" + expiresAt + "]";
    }
}
