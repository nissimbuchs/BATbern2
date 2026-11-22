package ch.batbern.companyuser.service;

import ch.batbern.companyuser.domain.AgentGrant;
import ch.batbern.companyuser.domain.User;
import ch.batbern.companyuser.dto.AgentTokenRequest;
import ch.batbern.companyuser.dto.AgentTokenResponse;
import ch.batbern.companyuser.exception.AgentRequestRefusedException;
import ch.batbern.companyuser.repository.UserRepository;
import ch.batbern.companyuser.security.AgentCallerSecrets;
import ch.batbern.companyuser.security.AgentCallerSecrets.Caller;
import ch.batbern.shared.security.AgentTokenSigner;
import ch.batbern.shared.security.AgentTokenSigner.SignedAgentToken;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Locale;
import java.util.Optional;

import static ch.batbern.companyuser.exception.AgentRequestRefusedException.AGENT_SWITCHED_OFF;
import static ch.batbern.companyuser.exception.AgentRequestRefusedException.GRANT_INVALID;
import static ch.batbern.companyuser.exception.AgentRequestRefusedException.PRINCIPAL_NOT_ELIGIBLE;

/**
 * Delegated agent tokens (agentic interaction, epic agent identity, story 6),
 * {@code POST /internal/agent-tokens}: the agent service exchanges a run grant for a short-lived
 * token that every service accepts.
 *
 * <p>Checks in order, the first failure answers: caller secret, agent service only (401), body (400),
 * switch (403), grant ({@link AgentGrantService#findActiveGrant}, 403), principal still allowlisted
 * and an organizer with a Cognito {@code sub} (403), signing key (503). Switch and allowlist are
 * checked again on every mint, so switching the agent off stops every run within one token lifetime
 * (AD-10). The claim set is {@link AgentTokenSigner}'s; the token lives at most 300 s and never
 * outlives its grant. Nothing is stored. Logs carry run id, principal kind and code, never the token,
 * a secret or the key.
 */
@Slf4j
@Service
@Transactional(readOnly = true)
public class AgentTokenService {

    private static final int MAX_RUN_ID = 36;
    private static final int MAX_SECRET = 100;

    private final AgentGrantService agentGrantService;
    private final AgentSettingsService agentSettingsService;
    private final AgentCallerSecrets callerSecrets;
    private final UserRepository userRepository;
    private final Optional<AgentTokenSigner> signer;

    /**
     * Reads {@value AgentTokenSigner#AGENT_PRIVATE_KEY_PROPERTY}: unset, blank or {@code none} leaves
     * minting off (503); a value that is not an RSA private key fails startup.
     */
    public AgentTokenService(
            AgentGrantService agentGrantService,
            AgentSettingsService agentSettingsService,
            AgentCallerSecrets callerSecrets,
            UserRepository userRepository,
            @Value("${" + AgentTokenSigner.AGENT_PRIVATE_KEY_PROPERTY + ":}") String privateKey) {
        this.agentGrantService = agentGrantService;
        this.agentSettingsService = agentSettingsService;
        this.callerSecrets = callerSecrets;
        this.userRepository = userRepository;
        this.signer = AgentTokenSigner.fromKey(privateKey, Clock.systemUTC());
        if (signer.isEmpty()) {
            log.info("Agent token minting is off: {} is unset, blank or the placeholder",
                    AgentTokenSigner.AGENT_PRIVATE_KEY_PROPERTY);
        }
    }

    /**
     * Mints an agent token for the run of {@code request}.
     *
     * @param request               the body; may be {@code null} (400 after the caller check)
     * @param presentedCallerSecret the {@code X-Agent-Caller-Secret} header, may be {@code null}
     * @throws AgentRequestRefusedException with the status and code of the first failed check
     */
    public AgentTokenResponse mint(AgentTokenRequest request, String presentedCallerSecret) {
        if (!callerSecrets.matches(Caller.AGENT_SERVICE, presentedCallerSecret)) {
            throw refused(AgentRequestRefusedException.callerSecretInvalid(), null, null);
        }
        validate(request);
        String runId = request.runId();
        if (!agentSettingsService.isEnabled()) {
            throw refused(AgentRequestRefusedException.forbidden(AGENT_SWITCHED_OFF,
                    "The organizer agent is switched off"), runId, null);
        }
        AgentGrant grant = agentGrantService.findActiveGrant(runId, request.grantSecret())
                .orElseThrow(() -> refused(grantInvalid(), runId, null));
        String principalKind = grant.getPrincipalKind().name().toLowerCase(Locale.ROOT);
        if (!agentSettingsService.isAllowlisted(grant.getUsername())) {
            throw refused(AgentRequestRefusedException.forbidden(PRINCIPAL_NOT_ELIGIBLE,
                    "The principal is not on the agent allowlist or no longer an organizer"), runId, principalKind);
        }
        String sub = userRepository.findByUsername(grant.getUsername())
                .map(User::getCognitoUserId)
                .filter(value -> !value.isBlank())
                .orElseThrow(() -> refused(AgentRequestRefusedException.forbidden(PRINCIPAL_NOT_ELIGIBLE,
                        "The principal has no Cognito login"), runId, principalKind));
        AgentTokenSigner tokenSigner = signer.orElseThrow(
                () -> refused(AgentRequestRefusedException.signingKeyMissing(), runId, principalKind));

        SignedAgentToken signed;
        try {
            signed = tokenSigner.sign(sub, grant.getUsername(), runId, principalKind, grant.getExpiresAt());
        } catch (IllegalArgumentException e) {
            // The grant ended between the lookup and signing (less than a whole second left).
            throw refused(grantInvalid(), runId, principalKind);
        }
        log.info("Agent token minted: run={}, principalKind={}, expiresAt={}", runId, principalKind,
                signed.expiresAt());
        return new AgentTokenResponse(signed.token(), signed.expiresAt());
    }

    private static void validate(AgentTokenRequest request) {
        if (request == null) {
            throw refused(AgentRequestRefusedException.badRequest("Request body required"), null, null);
        }
        if (!request.unknownFields().isEmpty()) {
            throw refused(AgentRequestRefusedException.badRequest(
                    "Unknown fields: " + request.unknownFields().keySet()), null, null);
        }
        field("runId", request.runId(), MAX_RUN_ID);
        field("grantSecret", request.grantSecret(), MAX_SECRET);
    }

    private static void field(String name, String value, int maxLength) {
        if (value == null || value.isBlank()) {
            throw refused(AgentRequestRefusedException.badRequest(name + " is required"), null, null);
        }
        if (value.length() > maxLength) {
            throw refused(AgentRequestRefusedException.badRequest(name + " is longer than " + maxLength), null, null);
        }
    }

    private static AgentRequestRefusedException grantInvalid() {
        return AgentRequestRefusedException.forbidden(GRANT_INVALID, "No active grant for this run and secret");
    }

    /** Logs the refusal at INFO (run id, principal kind, code; nothing secret) and returns it. */
    private static AgentRequestRefusedException refused(AgentRequestRefusedException e, String runId,
            String principalKind) {
        log.info("Agent token refused: run={}, principalKind={}, code={}", runId, principalKind, e.getCode());
        return e;
    }
}
