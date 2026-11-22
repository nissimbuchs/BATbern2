package ch.batbern.companyuser.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The secrets that authenticate who calls {@code /internal/agent-grants} and
 * {@code /internal/agent-eligibility} (agent identity, story 5), the only {@code /internal}
 * operations that check their caller: the filter chain leaves {@code /internal/**} open (epic decision
 * 2026-11-21, {@code docs/architecture/module-map.md}).
 *
 * <p>Properties {@code agent.callers.ems.secret} and {@code agent.callers.agent-service.secret}
 * ({@code AGENT_CALLERS_EMS_SECRET}, {@code AGENT_CALLERS_AGENT_SERVICE_SECRET} in AWS). Only their
 * SHA-256 digests are kept; a presented secret is hashed and compared with
 * {@link MessageDigest#isEqual} (constant time, and equal lengths whatever was presented). A blank
 * configured secret refuses every call of that caller. No secret is ever logged.
 */
@Slf4j
@Component
public class AgentCallerSecrets {

    /** The two callers. */
    public enum Caller {
        /** event-management: the mail handoff and the eligibility question. */
        EMS,
        /** The agent service: organizer login, linked runs, scheduler jobs. */
        AGENT_SERVICE
    }

    private final Map<Caller, byte[]> digests = new EnumMap<>(Caller.class);

    public AgentCallerSecrets(
            @Value("${agent.callers.ems.secret:}") String emsSecret,
            @Value("${agent.callers.agent-service.secret:}") String agentServiceSecret) {
        put(Caller.EMS, emsSecret);
        put(Caller.AGENT_SERVICE, agentServiceSecret);
        List<Caller> missing = Arrays.stream(Caller.values())
                .filter(caller -> !digests.containsKey(caller))
                .toList();
        if (!missing.isEmpty()) {
            log.warn("Agent caller secrets not configured for {}: their agent grant and eligibility calls are refused",
                    missing);
        }
    }

    /** Whether {@code presented} is {@code caller}'s configured secret; never for a blank configuration. */
    public boolean matches(Caller caller, String presented) {
        byte[] expected = digests.get(caller);
        if (expected == null || presented == null || presented.isEmpty()) {
            return false;
        }
        return MessageDigest.isEqual(expected, sha256(presented));
    }

    private void put(Caller caller, String secret) {
        if (secret != null && !secret.isBlank()) {
            digests.put(caller, sha256(secret));
        }
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
