package ch.batbern.shared.security;

/**
 * The kinds of bearer token {@link BatbernJwtDecoder} can accept. Each service enables a set of them
 * (service default in code, {@code batbern.security.token-kinds} overrides).
 */
public enum TokenKind {

    /** AWS Cognito ID or access token, RS256, verified against the user pool's JWK set. */
    COGNITO,

    /** Watch pairing token, {@code iss=batbern-watch}, HS256 with {@code watch.jwt.secret}. */
    WATCH,

    /**
     * Agent token of the agentic pilot, {@code iss=batbern-agent}, RS256, verified with
     * {@code agent.jwt.public-key}; at most 300 s lifetime, never carries a role claim.
     */
    AGENT
}
