package ch.batbern.companyuser.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Body of {@code POST /internal/agent-tokens} ({@code AgentTokenRequest} in
 * {@code cums-internal.openapi.yml}). Loose like {@link AgentGrantRequest}: unknown fields are
 * collected, so the caller secret is checked before the body is judged. {@code AgentTokenService}
 * validates it.
 *
 * @param runId         the run of an active grant
 * @param grantSecret   that grant's secret
 * @param unknownFields any other field, refused with 400
 */
public record AgentTokenRequest(
        String runId,
        String grantSecret,
        @JsonAnySetter Map<String, Object> unknownFields) {

    public AgentTokenRequest {
        unknownFields = unknownFields == null ? new LinkedHashMap<>() : unknownFields;
    }

    /** Never prints the grant secret. */
    @Override
    public String toString() {
        return "AgentTokenRequest[runId=" + runId + ", unknownFields=" + unknownFields.keySet() + "]";
    }
}
