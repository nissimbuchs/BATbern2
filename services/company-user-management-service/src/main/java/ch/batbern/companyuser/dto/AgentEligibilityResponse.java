package ch.batbern.companyuser.dto;

/**
 * The 200 of {@code GET /internal/agent-eligibility} ({@code AgentEligibility} in
 * {@code cums-internal.openapi.yml}); {@code eligible} is always {@code true}, anything else is 403.
 */
public record AgentEligibilityResponse(String username, boolean eligible) {

    /** The only answer there is. */
    public static AgentEligibilityResponse eligible(String username) {
        return new AgentEligibilityResponse(username, true);
    }
}
