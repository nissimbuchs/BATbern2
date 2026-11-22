package ch.batbern.companyuser.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Body of {@code POST /internal/agent-grants} ({@code AgentGrantRequest} in
 * {@code cums-internal.openapi.yml}). Deliberately loose: {@code trigger} is a string and unknown
 * fields are collected, so the caller secret is checked before the body is judged (an unknown caller
 * learns nothing, not even that the body is wrong). {@code AgentGrantService} validates it.
 *
 * @param trigger              {@code EMS_HANDOFF}, {@code ORGANIZER_LOGIN}, {@code LINKED_RUN} or
 *                             {@code SCHEDULER_JOB}
 * @param username             {@code EMS_HANDOFF}: the requester
 * @param linkedRunId          {@code LINKED_RUN}: the active requester run
 * @param linkedRunGrantSecret {@code LINKED_RUN}: that run's grant secret
 * @param jobName              {@code SCHEDULER_JOB}: a listed job
 * @param unknownFields        any other field, refused with 400
 */
public record AgentGrantRequest(
        String trigger,
        String username,
        String linkedRunId,
        String linkedRunGrantSecret,
        String jobName,
        @JsonAnySetter Map<String, Object> unknownFields) {

    public AgentGrantRequest {
        unknownFields = unknownFields == null ? new LinkedHashMap<>() : unknownFields;
    }

    /** Never prints the linked grant secret. */
    @Override
    public String toString() {
        return "AgentGrantRequest[trigger=" + trigger + ", username=" + username + ", linkedRunId=" + linkedRunId
                + ", jobName=" + jobName + ", unknownFields=" + unknownFields.keySet() + "]";
    }
}
