package ch.batbern.companyuser.service;

import ch.batbern.companyuser.domain.AgentGrant;
import ch.batbern.companyuser.domain.AgentGrantTrigger;
import ch.batbern.companyuser.domain.AgentPrincipalKind;
import ch.batbern.companyuser.dto.AgentEligibilityResponse;
import ch.batbern.companyuser.dto.AgentGrantRequest;
import ch.batbern.companyuser.dto.AgentGrantResponse;
import ch.batbern.companyuser.exception.AgentRequestRefusedException;
import ch.batbern.companyuser.repository.AgentGrantRepository;
import ch.batbern.companyuser.security.AgentCallerSecrets;
import ch.batbern.companyuser.security.AgentCallerSecrets.Caller;
import ch.batbern.companyuser.security.SecurityContextHelper;
import ch.batbern.shared.security.Actors;
import ch.batbern.shared.security.BatbernJwtDecoder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static ch.batbern.companyuser.exception.AgentRequestRefusedException.AGENT_SWITCHED_OFF;
import static ch.batbern.companyuser.exception.AgentRequestRefusedException.LINKED_RUN_INVALID;
import static ch.batbern.companyuser.exception.AgentRequestRefusedException.ORGANIZER_LOGIN_REQUIRED;
import static ch.batbern.companyuser.exception.AgentRequestRefusedException.PRINCIPAL_NOT_ELIGIBLE;
import static ch.batbern.companyuser.exception.AgentRequestRefusedException.SCHEDULER_JOB_UNKNOWN;

/**
 * Agent run grants and agent eligibility (agentic interaction, epic agent identity, story 5),
 * {@code POST /internal/agent-grants} and {@code GET /internal/agent-eligibility}.
 *
 * <p>The trigger decides the caller and the principal, never the body (table in
 * {@code cums-internal.openapi.yml}). Checks in order, the first failure answers: caller secret
 * (401), body (400), organizer login (401), switch (403), linked run or job (403), principal (403).
 * Every principal, the delegate included, must be on the allowlist and still hold ORGANIZER
 * ({@link AgentSettingsService#isAllowlisted}, R5). A grant is one run for one principal, one hour;
 * its secret is returned once and stored only as a hex SHA-256.
 */
@Slf4j
@Service
@Transactional
public class AgentGrantService {

    /** Lifetime of a grant; V42 refuses a longer one. */
    public static final Duration GRANT_LIFETIME = Duration.ofHours(1);
    /** Actor of an EMS handoff grant ({@code system:ems-agent-handoff}). */
    static final String EMS_HANDOFF_JOB = "ems-agent-handoff";
    /** Actor of a linked-run or scheduler-job grant ({@code system:agent-service}). */
    static final String AGENT_SERVICE_JOB = "agent-service";

    private static final int SECRET_BYTES = 32;
    private static final int MAX_USERNAME = 100;
    private static final int MAX_RUN_ID = 36;
    private static final int MAX_SECRET = 100;
    private static final int MAX_JOB_NAME = 100;

    private final AgentGrantRepository grantRepository;
    private final AgentSettingsService agentSettingsService;
    private final AgentCallerSecrets callerSecrets;
    private final SecurityContextHelper securityContextHelper;
    private final Set<String> schedulerJobs;
    private final SecureRandom random = new SecureRandom();

    public AgentGrantService(
            AgentGrantRepository grantRepository,
            AgentSettingsService agentSettingsService,
            AgentCallerSecrets callerSecrets,
            SecurityContextHelper securityContextHelper,
            @Value("${agent.grants.scheduler-jobs:}") String schedulerJobs) {
        this.grantRepository = grantRepository;
        this.agentSettingsService = agentSettingsService;
        this.callerSecrets = callerSecrets;
        this.securityContextHelper = securityContextHelper;
        this.schedulerJobs = Arrays.stream(schedulerJobs == null ? new String[0] : schedulerJobs.split(","))
                .map(String::trim)
                .filter(job -> !job.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Issues a run grant.
     *
     * @param request               the body; may be {@code null} (400 after the caller check)
     * @param presentedCallerSecret the {@code X-Agent-Caller-Secret} header, may be {@code null}
     * @return the grant with its secret, which is never readable again
     * @throws AgentRequestRefusedException with the status and code of the first failed check
     */
    public AgentGrantResponse issue(AgentGrantRequest request, String presentedCallerSecret) {
        if (!callerSecrets.matches(Caller.EMS, presentedCallerSecret)
                && !callerSecrets.matches(Caller.AGENT_SERVICE, presentedCallerSecret)) {
            throw AgentRequestRefusedException.callerSecretInvalid();
        }
        if (request == null) {
            throw AgentRequestRefusedException.badRequest("Request body required");
        }
        AgentGrantTrigger trigger = AgentGrantTrigger.fromApi(request.trigger())
                .orElseThrow(() -> AgentRequestRefusedException.badRequest(
                        "trigger must be one of " + Arrays.toString(AgentGrantTrigger.values())));
        if (!callerSecrets.matches(callerOf(trigger), presentedCallerSecret)) {
            throw AgentRequestRefusedException.callerSecretInvalid();
        }
        validateFields(trigger, request);

        String organizer = null;
        if (trigger == AgentGrantTrigger.ORGANIZER_LOGIN) {
            organizer = organizerLoginUsername().orElseThrow(() -> new AgentRequestRefusedException(
                    HttpStatus.UNAUTHORIZED, ORGANIZER_LOGIN_REQUIRED,
                    "ORGANIZER_LOGIN needs the organizer's Cognito login"));
        }
        requireSwitchedOn();
        if (trigger == AgentGrantTrigger.LINKED_RUN) {
            findActiveGrant(request.linkedRunId(), request.linkedRunGrantSecret())
                    .filter(parent -> parent.getPrincipalKind() == AgentPrincipalKind.REQUESTER)
                    .orElseThrow(() -> AgentRequestRefusedException.forbidden(LINKED_RUN_INVALID,
                            "The linked run is not an active requester grant"));
        }
        if (trigger == AgentGrantTrigger.SCHEDULER_JOB && !schedulerJobs.contains(request.jobName())) {
            throw AgentRequestRefusedException.forbidden(SCHEDULER_JOB_UNKNOWN,
                    "Scheduler job not listed in agent.grants.scheduler-jobs");
        }

        String principal = switch (trigger) {
            case EMS_HANDOFF -> request.username();
            case ORGANIZER_LOGIN -> organizer;
            case LINKED_RUN, SCHEDULER_JOB -> agentSettingsService.delegateUsername()
                    .orElseThrow(() -> AgentRequestRefusedException.forbidden(PRINCIPAL_NOT_ELIGIBLE,
                            "No organizer delegate is set"));
        };
        requireAllowlisted(principal);

        String secret = newSecret();
        Instant issuedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        AgentGrant grant = grantRepository.saveAndFlush(AgentGrant.builder()
                .runId(UUID.randomUUID().toString())
                .principalKind(trigger.principalKind())
                .username(principal)
                .trigger(trigger)
                .linkedRunId(trigger == AgentGrantTrigger.LINKED_RUN ? request.linkedRunId() : null)
                .jobName(trigger == AgentGrantTrigger.SCHEDULER_JOB ? request.jobName() : null)
                .secretHash(sha256Hex(secret))
                .requestedBy(requestedBy(trigger, principal))
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(GRANT_LIFETIME))
                .build());
        log.info("Agent grant issued: run={}, trigger={}, principalKind={}, username={}, requestedBy={}",
                grant.getRunId(), trigger, grant.getPrincipalKind(), principal, grant.getRequestedBy());
        return AgentGrantResponse.of(grant, secret);
    }

    /**
     * Whether the agent may act for {@code username}: switched on, allowlisted, still ORGANIZER. EMS
     * caller secret only. Not eligible is a 403, never {@code eligible: false} (owner decision, R5).
     *
     * @throws AgentRequestRefusedException 401 bad secret, 400 no or too long username, 403 switch off or not listed
     */
    @Transactional(readOnly = true)
    public AgentEligibilityResponse confirmEligible(String username, String presentedCallerSecret) {
        if (!callerSecrets.matches(Caller.EMS, presentedCallerSecret)) {
            throw AgentRequestRefusedException.callerSecretInvalid();
        }
        if (username == null || username.isBlank()) {
            throw AgentRequestRefusedException.badRequest("username is required");
        }
        if (username.length() > MAX_USERNAME) {
            throw AgentRequestRefusedException.badRequest("username is longer than " + MAX_USERNAME);
        }
        requireSwitchedOn();
        requireAllowlisted(username);
        return AgentEligibilityResponse.eligible(username);
    }

    /**
     * The seam story 6 mints agent tokens against: the grant of {@code runId} when it has not expired
     * and {@code grantSecret} is its secret (hash compared in constant time); empty otherwise, also for
     * {@code null} arguments. It does not re-check the switch or the allowlist: minting must do that
     * itself ({@link AgentSettingsService#isEnabled()}, {@link AgentSettingsService#isAllowlisted}),
     * since either may have changed since the grant was issued.
     */
    @Transactional(readOnly = true)
    public Optional<AgentGrant> findActiveGrant(String runId, String grantSecret) {
        if (runId == null || grantSecret == null || grantSecret.isEmpty()) {
            return Optional.empty();
        }
        byte[] presented = sha256Hex(grantSecret).getBytes(StandardCharsets.US_ASCII);
        return grantRepository.findByRunIdAndExpiresAtAfter(runId, Instant.now())
                .filter(grant -> MessageDigest.isEqual(
                        grant.getSecretHash().getBytes(StandardCharsets.US_ASCII), presented));
    }

    private static Caller callerOf(AgentGrantTrigger trigger) {
        return trigger == AgentGrantTrigger.EMS_HANDOFF ? Caller.EMS : Caller.AGENT_SERVICE;
    }

    /** Exactly the fields of the trigger, within the spec's lengths; anything else is 400. */
    private static void validateFields(AgentGrantTrigger trigger, AgentGrantRequest request) {
        if (!request.unknownFields().isEmpty()) {
            throw AgentRequestRefusedException.badRequest("Unknown fields: " + request.unknownFields().keySet());
        }
        boolean wantsUsername = trigger == AgentGrantTrigger.EMS_HANDOFF;
        boolean wantsLink = trigger == AgentGrantTrigger.LINKED_RUN;
        boolean wantsJob = trigger == AgentGrantTrigger.SCHEDULER_JOB;
        field(trigger, "username", request.username(), wantsUsername, MAX_USERNAME);
        field(trigger, "linkedRunId", request.linkedRunId(), wantsLink, MAX_RUN_ID);
        field(trigger, "linkedRunGrantSecret", request.linkedRunGrantSecret(), wantsLink, MAX_SECRET);
        field(trigger, "jobName", request.jobName(), wantsJob, MAX_JOB_NAME);
    }

    private static void field(AgentGrantTrigger trigger, String name, String value, boolean wanted, int maxLength) {
        if (wanted && (value == null || value.isBlank())) {
            throw AgentRequestRefusedException.badRequest(trigger + " needs " + name);
        }
        if (!wanted && value != null) {
            throw AgentRequestRefusedException.badRequest(trigger + " takes no " + name);
        }
        if (value != null && value.length() > maxLength) {
            throw AgentRequestRefusedException.badRequest(name + " is longer than " + maxLength);
        }
    }

    /**
     * The username of a forwarded Cognito login: a JWT that carries no {@code act} and is neither a
     * Watch nor an agent token; {@code custom:username}, else {@code sub}.
     */
    private Optional<String> organizerLoginUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            return Optional.empty();
        }
        String issuer = jwt.getClaimAsString(JwtClaimNames.ISS);
        if (Actors.carriesAct(authentication)
                || BatbernJwtDecoder.WATCH_ISSUER.equals(issuer)
                || BatbernJwtDecoder.AGENT_ISSUER.equals(issuer)) {
            return Optional.empty();
        }
        String username = securityContextHelper.getCurrentUsername();
        return username == null || username.isBlank() ? Optional.empty() : Optional.of(username);
    }

    private void requireSwitchedOn() {
        if (!agentSettingsService.isEnabled()) {
            throw AgentRequestRefusedException.forbidden(AGENT_SWITCHED_OFF, "The organizer agent is switched off");
        }
    }

    private void requireAllowlisted(String username) {
        if (!agentSettingsService.isAllowlisted(username)) {
            throw AgentRequestRefusedException.forbidden(PRINCIPAL_NOT_ELIGIBLE,
                    "The principal is not on the agent allowlist or no longer an organizer");
        }
    }

    private static String requestedBy(AgentGrantTrigger trigger, String principal) {
        return switch (trigger) {
            case EMS_HANDOFF -> Actors.system(EMS_HANDOFF_JOB);
            case ORGANIZER_LOGIN -> Actors.forUsername(principal);
            case LINKED_RUN, SCHEDULER_JOB -> Actors.system(AGENT_SERVICE_JOB);
        };
    }

    private String newSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256Hex(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
