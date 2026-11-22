package ch.batbern.companyuser.controller;

import ch.batbern.companyuser.dto.AgentEligibilityResponse;
import ch.batbern.companyuser.dto.AgentGrantRequest;
import ch.batbern.companyuser.dto.AgentGrantResponse;
import ch.batbern.companyuser.dto.AgentTokenRequest;
import ch.batbern.companyuser.dto.AgentTokenResponse;
import ch.batbern.companyuser.service.AgentGrantService;
import ch.batbern.companyuser.service.AgentTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agent run grants and agent eligibility for EMS and the agent service (agentic interaction, epic
 * agent identity, story 5), and delegated agent tokens for the agent service (story 6), specified in
 * {@code docs/api/internal/cums-internal.openapi.yml}.
 *
 * <p>The only {@code /internal} operations that authenticate their caller: the filter chain leaves
 * {@code /internal/**} open, so {@link AgentGrantService} and {@link AgentTokenService} check the
 * {@value #CALLER_SECRET_HEADER} header first (epic decision 2026-11-21,
 * {@code docs/architecture/module-map.md}). The header, the parameters and the bodies are optional
 * here on purpose: a missing secret must answer 401 before anything else, not Spring's 400. No
 * {@code @PreAuthorize}: these are not user endpoints ({@code EndpointAuthorizationGuard} excludes
 * {@code /internal/}).
 */
@RestController
@RequiredArgsConstructor
public class InternalAgentController {

    /** The caller secret header. */
    public static final String CALLER_SECRET_HEADER = "X-Agent-Caller-Secret";

    private final AgentGrantService agentGrantService;
    private final AgentTokenService agentTokenService;

    /** Issues a run grant; 201 with the grant secret, which is in this answer only. */
    @PostMapping("/internal/agent-grants")
    public ResponseEntity<AgentGrantResponse> issueAgentGrant(
            @RequestHeader(name = CALLER_SECRET_HEADER, required = false) String callerSecret,
            @RequestBody(required = false) AgentGrantRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(agentGrantService.issue(request, callerSecret));
    }

    /** 200 {@code eligible: true}, or a 403 with the reason in {@code details.code}. */
    @GetMapping("/internal/agent-eligibility")
    public ResponseEntity<AgentEligibilityResponse> getAgentEligibility(
            @RequestHeader(name = CALLER_SECRET_HEADER, required = false) String callerSecret,
            @RequestParam(required = false) String username) {
        return ResponseEntity.ok(agentGrantService.confirmEligible(username, callerSecret));
    }

    /** Mints a delegated agent token for an active run grant; agent service only. */
    @PostMapping("/internal/agent-tokens")
    public ResponseEntity<AgentTokenResponse> mintAgentToken(
            @RequestHeader(name = CALLER_SECRET_HEADER, required = false) String callerSecret,
            @RequestBody(required = false) AgentTokenRequest request) {
        return ResponseEntity.ok(agentTokenService.mint(request, callerSecret));
    }
}
