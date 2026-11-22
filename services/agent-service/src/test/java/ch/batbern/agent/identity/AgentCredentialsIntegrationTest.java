package ch.batbern.agent.identity;

import ch.batbern.agent.config.AgentProperties;
import ch.batbern.agent.gate.AutonomyGate;
import ch.batbern.agent.gateway.ApiResponse;
import ch.batbern.agent.log.DecisionLog;
import ch.batbern.agent.loop.AgentLoop;
import ch.batbern.agent.loop.AgentRunRequest;
import ch.batbern.agent.loop.AgentRunResult;
import ch.batbern.agent.loop.LlmContent;
import ch.batbern.agent.loop.LlmResponse;
import ch.batbern.agent.loop.LlmStopReason;
import ch.batbern.agent.loop.LlmUsage;
import ch.batbern.agent.loop.ScriptedLlm;
import ch.batbern.agent.loop.TestTools;
import ch.batbern.agent.loop.TestTools.RecordingTool;
import ch.batbern.agent.run.AgentRunStatus;
import ch.batbern.agent.run.PrincipalKind;
import ch.batbern.agent.run.RunEndReason;
import ch.batbern.agent.tools.AgentTool;
import ch.batbern.agent.tools.ToolOutcome;
import ch.batbern.agent.gateway.Operation;
import ch.batbern.agent.tools.OperationClass;
import ch.batbern.agent.tools.OperationClassTable;
import ch.batbern.agent.tools.TestToolRegistry;
import ch.batbern.agent.tools.ToolRegistry;
import ch.batbern.shared.test.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.web.client.RestTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Story 4 (R2, AD-3, AD-15): a run acts only with a CUMS-minted token, minted again before every
 * write, and a refused grant or mint hands the run to the organizers with the CUMS code. Every row
 * of the story's I/O matrix, against CUMS at the wire ({@link CumsAtTheWire}: every grant, mint and
 * gateway request validated against the CUMS internal and companies specs), the real loop, decision
 * log and PostgreSQL; only the model is scripted. Rows are read through {@link JdbcTemplate}.
 */
@DisplayName("A run acts with a CUMS-minted token, minted again before every write")
@TestPropertySource(properties = {"agent.enabled=true", "agent.loop.max-steps=4"})
@ExtendWith(OutputCaptureExtension.class)
class AgentCredentialsIntegrationTest extends AbstractIntegrationTest {

    private static final String SYSTEM = "You keep the BATbern company list tidy.";
    private static final LlmUsage TOKENS = new LlmUsage(1, 1, 0, 0);
    private static final String COMPANIES = "/api/v1/companies";
    private static final ToolRegistry NO_TOOLS = new ToolRegistry(List.of(), OperationClassTable.standard());

    @Autowired
    private AgentLoop loop;

    @Autowired
    private ScriptedLlm model;

    @Autowired
    private TestToolRegistry toolRegistry;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private DecisionLog decisionLog;

    @Autowired
    private AgentProperties properties;

    @Autowired
    private Clock clock;

    @Autowired
    private AutonomyGate gate;

    private CumsAtTheWire cums;

    /** The Authorization header of every gateway request, in order. */
    private final List<String> gatewayAuthorizations = Collections.synchronizedList(new ArrayList<>());

    @BeforeEach
    @AfterEach
    void clean() {
        model.reset();
        toolRegistry.reset();
        jdbc.execute("TRUNCATE agent_runs CASCADE");
        gatewayAuthorizations.clear();
    }

    @BeforeEach
    void bindCums() {
        cums = CumsAtTheWire.bindTo(restTemplate);
    }

    @AfterEach
    void unbindCums() {
        cums.close();
    }

    // ── Reads and writes ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("read: the grant and first mint precede the model; the GET carries the minted token; DONE, grant_run_id set")
    void should_readWithMintedToken_when_schedulerGrantIssued() {
        cums.grants().mints();
        gatewayExpects(HttpMethod.GET, ExpectedCount.once());
        RecordingTool read = reading();
        model.then(useTool("listCompanies")).then(endTurn());

        AgentRunResult result = run(new GrantSource.SchedulerJob(CumsAtTheWire.JOB), read);

        assertThat(result.status()).isEqualTo(AgentRunStatus.DONE);
        assertThat(result.detail()).isNull();
        cums.verify();
        // The grant: exactly the SCHEDULER_JOB fields, the caller secret, no Authorization
        assertThat(cums.grantRequests()).singleElement().satisfies(grant -> {
            assertThat(grant.body().toString()).isEqualTo("{\"trigger\":\"SCHEDULER_JOB\",\"jobName\":\"morning-brief\"}");
            assertThat(grant.headers().getFirst("X-Agent-Caller-Secret")).isEqualTo(CumsAtTheWire.CALLER_SECRET);
            assertThat(grant.headers().containsHeader(HttpHeaders.AUTHORIZATION)).isFalse();
        });
        // One mint (before the model), with the grant's run id and secret, no Authorization
        Map<String, Object> run = runRow(result.runId());
        assertThat(cums.mintRequests()).singleElement().satisfies(mint -> {
            assertThat(mint.body().size()).isEqualTo(2);
            assertThat(mint.body().path("runId").asText()).isEqualTo(run.get("grant_run_id").toString());
            assertThat(mint.body().path("grantSecret").asText()).isEqualTo(cums.grantSecrets().get(0));
            assertThat(mint.headers().getFirst("X-Agent-Caller-Secret")).isEqualTo(CumsAtTheWire.CALLER_SECRET);
            assertThat(mint.headers().containsHeader(HttpHeaders.AUTHORIZATION)).isFalse();
        });
        // The read reuses the token minted at the start
        assertThat(gatewayAuthorizations).containsExactly("Bearer " + cums.tokens().get(0));
        assertThat(run).containsEntry("status", "DONE").containsEntry("trigger", "SCHEDULED")
                .containsEntry("principal_kind", "ORGANIZER_DELEGATE")
                .containsEntry("end_reason", "COMPLETED").containsEntry("end_detail", null);
        assertThat(run.get("grant_run_id")).isNotNull();
        assertThat(toolCallStatuses(result.runId())).containsExactly("SUCCEEDED");
        assertThat(model.requests()).hasSize(2);
    }

    @Test
    @DisplayName("two writes: three mints (start and one per write), each write with its own token")
    void should_mintBeforeEveryWrite_when_toolWritesTwice() {
        cums.grants().mints();
        gatewayExpects(HttpMethod.POST, ExpectedCount.times(2));
        clearWritesOfScheduledRuns();
        RecordingTool write = writing(false);
        model.then(useTool("createCompany", "createCompany")).then(endTurn());

        AgentRunResult result = run(new GrantSource.SchedulerJob(CumsAtTheWire.JOB), write);

        assertThat(result.status()).isEqualTo(AgentRunStatus.DONE);
        cums.verify();
        List<String> tokens = cums.tokens();
        assertThat(tokens).hasSize(3).doesNotHaveDuplicates();
        assertThat(cums.mintRequests()).hasSize(3);
        assertThat(gatewayAuthorizations).containsExactly("Bearer " + tokens.get(1), "Bearer " + tokens.get(2));
        assertThat(toolCallStatuses(result.runId())).containsExactly("SUCCEEDED", "SUCCEEDED");
    }

    @Test
    @DisplayName("read near expiry: a token expiring within 60 s is minted again before the read")
    void should_mintBeforeRead_when_tokenExpiresWithinAMinute() {
        cums.grants().mints(Duration.ofSeconds(30));
        gatewayExpects(HttpMethod.GET, ExpectedCount.once());
        model.then(useTool("listCompanies")).then(endTurn());

        AgentRunResult result = run(new GrantSource.SchedulerJob(CumsAtTheWire.JOB), reading());

        assertThat(result.status()).isEqualTo(AgentRunStatus.DONE);
        assertThat(cums.tokens()).hasSize(2);
        assertThat(gatewayAuthorizations).containsExactly("Bearer " + cums.tokens().get(1));
    }

    @Test
    @DisplayName("an HTTP error of the public API goes back to the tool; the run goes on")
    void should_returnApiErrorToTool_when_gatewayAnswers403() {
        cums.grants().mints();
        cums.server().expect(HttpMethod.POST, COMPANIES).andExpect(r -> gatewayAuthorizations
                .add(r.getHeaders().getFirst(HttpHeaders.AUTHORIZATION))).respondWithStatus(HttpStatus.FORBIDDEN);
        clearWritesOfScheduledRuns();
        model.then(useTool("createCompany")).then(endTurn());

        AgentRunResult result = run(new GrantSource.SchedulerJob(CumsAtTheWire.JOB), writing(false));

        assertThat(result.status()).isEqualTo(AgentRunStatus.DONE);
        assertThat(jdbc.queryForObject("SELECT output FROM agent_tool_calls WHERE run_id = ?", String.class,
                result.runId())).isEqualTo("403");
    }

    // ── Grant sources ────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("organizer login: the grant call carries the Cognito JWT as Bearer, the mint none; trigger ORGANIZER")
    void should_forwardCognitoJwtOnGrantOnly_when_organizerLogin() {
        cums.grants().mints();
        model.then(endTurn());

        AgentRunResult result = run(new GrantSource.OrganizerLogin("cognito.jwt.of-the-organizer"));

        assertThat(result.status()).isEqualTo(AgentRunStatus.DONE);
        assertThat(cums.grantRequests()).singleElement().satisfies(grant -> {
            assertThat(grant.body().toString()).isEqualTo("{\"trigger\":\"ORGANIZER_LOGIN\"}");
            assertThat(grant.headers().getFirst(HttpHeaders.AUTHORIZATION))
                    .isEqualTo("Bearer cognito.jwt.of-the-organizer");
        });
        assertThat(cums.mintRequests()).singleElement()
                .satisfies(mint -> assertThat(mint.headers().containsHeader(HttpHeaders.AUTHORIZATION)).isFalse());
        assertThat(runRow(result.runId())).containsEntry("trigger", "ORGANIZER")
                .containsEntry("principal_kind", "ORGANIZER");
    }

    @Test
    @DisplayName("linked run: the grant call sends exactly linkedRunId and linkedRunGrantSecret; trigger LINKED_RUN")
    void should_sendLinkFields_when_linkedRun() {
        cums.grants().mints();
        model.then(endTurn());
        String parentRun = UUID.randomUUID().toString();
        String parentSecret = CumsAtTheWire.grantSecret();

        AgentRunResult result = run(new GrantSource.LinkedRun(parentRun, parentSecret));

        assertThat(result.status()).isEqualTo(AgentRunStatus.DONE);
        assertThat(cums.grantRequests()).singleElement().satisfies(grant -> assertThat(grant.body().toString())
                .isEqualTo("{\"trigger\":\"LINKED_RUN\",\"linkedRunId\":\"" + parentRun
                        + "\",\"linkedRunGrantSecret\":\"" + parentSecret + "\"}"));
        assertThat(runRow(result.runId())).containsEntry("trigger", "LINKED_RUN")
                .containsEntry("principal_kind", "ORGANIZER_DELEGATE");
    }

    @Test
    @DisplayName("handed-over grant: no grant call, the mint uses EMS's run id and secret; trigger INBOUND_MAIL")
    void should_mintWithoutGrantCall_when_grantHandedOverByEms() {
        cums.mints();
        model.then(endTurn());
        UUID emsRun = UUID.randomUUID();
        String emsSecret = CumsAtTheWire.grantSecret();

        AgentRunResult result = run(new GrantSource.HandedOver(emsRun, emsSecret));

        assertThat(result.status()).isEqualTo(AgentRunStatus.DONE);
        assertThat(cums.grantRequests()).isEmpty();
        assertThat(cums.mintRequests()).singleElement().satisfies(mint -> assertThat(mint.body().toString())
                .isEqualTo("{\"runId\":\"" + emsRun + "\",\"grantSecret\":\"" + emsSecret + "\"}"));
        // CUMS issues EMS_HANDOFF grants for the requester only
        assertThat(runRow(result.runId())).containsEntry("trigger", "INBOUND_MAIL").containsEntry("grant_run_id", emsRun)
                .containsEntry("principal_kind", "REQUESTER");
    }

    // ── Refusals ─────────────────────────────────────────────────────────────────────────────────

    @ParameterizedTest(name = "{0} {1}")
    @CsvSource({"FORBIDDEN, PRINCIPAL_NOT_ELIGIBLE", "UNAUTHORIZED, CALLER_SECRET_INVALID",
        "BAD_REQUEST, INVALID_AGENT_REQUEST", "FORBIDDEN, AGENT_SWITCHED_OFF"})
    @DisplayName("grant refused: HANDED_TO_ORGANIZERS, GRANT_REFUSED with the CUMS code, no mint, no model call")
    void should_handToOrganizers_when_grantRefused(HttpStatus status, String code, CapturedOutput output) {
        cums.refusesGrant(status, code);

        AgentRunResult result = run(new GrantSource.SchedulerJob(CumsAtTheWire.JOB), reading());

        assertRefusedBeforeModel(result, RunEndReason.GRANT_REFUSED, code);
        cums.verify();
        assertThat(cums.mintRequests()).isEmpty();
        assertThat(runRow(result.runId())).containsEntry("grant_run_id", null).containsEntry("principal_kind", null);
        assertThat(output.getAll()).contains("Agent run " + result.runId() + " handed to the organizers: GRANT_REFUSED "
                + code);
    }

    @ParameterizedTest(name = "{0} {1}")
    @CsvSource({"FORBIDDEN, GRANT_INVALID", "SERVICE_UNAVAILABLE, AGENT_SIGNING_KEY_MISSING",
        "FORBIDDEN, PRINCIPAL_NOT_ELIGIBLE"})
    @DisplayName("first mint refused: HANDED_TO_ORGANIZERS, TOKEN_REFUSED with the code, grant_run_id kept, no model call")
    void should_handToOrganizers_when_firstMintRefused(HttpStatus status, String code, CapturedOutput output) {
        cums.grants().mintsThenRefuses(0, status, code);

        AgentRunResult result = run(new GrantSource.SchedulerJob(CumsAtTheWire.JOB), reading());

        assertRefusedBeforeModel(result, RunEndReason.TOKEN_REFUSED, code);
        assertThat(cums.mintRequests()).as("no retry").hasSize(1);
        assertThat(runRow(result.runId()).get("grant_run_id")).isNotNull();
        // The refusal line: run id and code (positive control), never the principal or a secret
        String out = output.getAll();
        assertThat(out).contains("Agent run " + result.runId() + " handed to the organizers: TOKEN_REFUSED " + code);
        assertThat(out).doesNotContain(CumsAtTheWire.PRINCIPAL_USERNAME)
                .doesNotContain(CumsAtTheWire.CALLER_SECRET)
                .doesNotContain(cums.grantSecrets().get(0));
    }

    @ParameterizedTest(name = "tool swallows the refusal: {0}")
    @ValueSource(booleans = {false, true})
    @DisplayName("mint before a write refused: the write is not sent, the tool call FAILED, TOKEN_REFUSED, no further model call")
    void should_handToOrganizers_when_mintBeforeWriteRefused(boolean swallow, CapturedOutput output) {
        cums.grants().mintsThenRefuses(1, HttpStatus.FORBIDDEN, "GRANT_INVALID");
        // No gateway expectation: a POST to /api/v1/companies would fail the test
        clearWritesOfScheduledRuns();
        RecordingTool write = writing(swallow);
        RecordingTool second = reading();
        model.then(useTool("createCompany", "listCompanies"));

        AgentRunResult result = run(new GrantSource.SchedulerJob(CumsAtTheWire.JOB), write, second);

        assertRefused(result, RunEndReason.TOKEN_REFUSED, "GRANT_INVALID");
        cums.verify();
        assertThat(gatewayAuthorizations).as("the write was not sent").isEmpty();
        assertThat(cums.mintRequests()).hasSize(2);
        assertThat(model.requests()).as("no model call after the refusal").hasSize(1);
        assertThat(write.inputs()).hasSize(1);
        assertThat(second.inputs()).as("the rest of the batch does not run").isEmpty();
        Map<String, Object> call = jdbc.queryForMap("SELECT status, output, error FROM agent_tool_calls WHERE run_id = ?",
                result.runId());
        assertThat(call).containsEntry("status", "FAILED").containsEntry("output", null);
        assertThat((String) call.get("error")).contains("TOKEN_REFUSED (GRANT_INVALID)")
                .doesNotContain(cums.grantSecrets().get(0)).doesNotContain(CumsAtTheWire.CALLER_SECRET);
        assertThat(output.getAll()).contains("Agent run " + result.runId()
                + " handed to the organizers: TOKEN_REFUSED GRANT_INVALID");
    }

    @Test
    @DisplayName("CUMS unreachable on the grant: GRANT_REFUSED, CUMS_UNREACHABLE, no retry")
    void should_handToOrganizers_when_cumsUnreachable() {
        cums.grantUnreachable();

        AgentRunResult result = run(new GrantSource.SchedulerJob(CumsAtTheWire.JOB));

        assertRefusedBeforeModel(result, RunEndReason.GRANT_REFUSED, "CUMS_UNREACHABLE");
        cums.verify();
        assertThat(cums.grantRequests()).hasSize(1);
    }

    @Test
    @DisplayName("CUMS unreachable on the first mint: TOKEN_REFUSED, CUMS_UNREACHABLE, no retry")
    void should_handToOrganizers_when_cumsUnreachableOnMint() {
        cums.grants().mintsThenUnreachable(0);

        AgentRunResult result = run(new GrantSource.SchedulerJob(CumsAtTheWire.JOB));

        assertRefusedBeforeModel(result, RunEndReason.TOKEN_REFUSED, "CUMS_UNREACHABLE");
        assertThat(cums.mintRequests()).as("no retry").hasSize(1);
        assertThat(runRow(result.runId()).get("grant_run_id")).isNotNull();
    }

    @Test
    @DisplayName("a 5xx without a code on the grant: GRANT_REFUSED, HTTP_502, no mint")
    void should_useHttpStatus_when_grantRefusalHasNoCode() {
        cums.grantAnswersWithoutCode(HttpStatus.BAD_GATEWAY);

        AgentRunResult result = run(new GrantSource.SchedulerJob(CumsAtTheWire.JOB));

        assertRefusedBeforeModel(result, RunEndReason.GRANT_REFUSED, "HTTP_502");
        cums.verify();
        assertThat(cums.mintRequests()).isEmpty();
    }

    @Test
    @DisplayName("the same handed-over grant twice: the second run is GRANT_REFUSED, GRANT_ALREADY_USED, no mint")
    void should_refuseSecondRun_when_handedOverGrantDeliveredTwice() {
        cums.mints();
        model.then(endTurn());
        GrantSource.HandedOver grant = new GrantSource.HandedOver(UUID.randomUUID(), CumsAtTheWire.grantSecret());

        AgentRunResult first = run(grant);
        AgentRunResult second = run(grant);

        assertThat(first.status()).isEqualTo(AgentRunStatus.DONE);
        assertThat(model.requests()).as("only the first run reached the model").hasSize(1);
        assertThat(second.status()).isEqualTo(AgentRunStatus.HANDED_TO_ORGANIZERS);
        assertThat(second.reason()).isEqualTo(RunEndReason.GRANT_REFUSED);
        assertThat(second.detail()).isEqualTo("GRANT_ALREADY_USED");
        assertThat(second.steps()).isZero();
        assertThat(runRow(second.runId())).containsEntry("end_reason", "GRANT_REFUSED")
                .containsEntry("end_detail", "GRANT_ALREADY_USED").containsEntry("grant_run_id", null);
        assertThat(runRow(first.runId())).containsEntry("grant_run_id", grant.runId());
        assertThat(cums.mintRequests()).as("the second run minted nothing").hasSize(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM agent_model_calls WHERE run_id = ?", Integer.class,
                second.runId())).isZero();
    }

    @Test
    @DisplayName("mint before a mid-run read refused: the GET is not sent, TOKEN_REFUSED, no further model call")
    void should_handToOrganizers_when_mintBeforeReadRefused() {
        // The first token expires within the margin, so the read mints again, and that mint is refused
        cums.grants().mintsThenRefuses(1, HttpStatus.FORBIDDEN, "GRANT_INVALID", Duration.ofSeconds(30));
        // No gateway expectation: a GET to /api/v1/companies would fail the test
        RecordingTool read = reading();
        model.then(useTool("listCompanies"));

        AgentRunResult result = run(new GrantSource.SchedulerJob(CumsAtTheWire.JOB), read);

        assertRefused(result, RunEndReason.TOKEN_REFUSED, "GRANT_INVALID");
        cums.verify();
        assertThat(gatewayAuthorizations).as("the read was not sent").isEmpty();
        assertThat(cums.mintRequests()).hasSize(2);
        assertThat(model.requests()).as("no model call after the refusal").hasSize(1);
        assertThat(read.inputs()).hasSize(1);
        assertThat(toolCallStatuses(result.runId())).containsExactly("FAILED");
    }

    @Test
    @DisplayName("a 5xx without a code on the mint: TOKEN_REFUSED, HTTP_503")
    void should_useHttpStatus_when_refusalHasNoCode() {
        cums.grants().mintsThenRefuses(0, HttpStatus.SERVICE_UNAVAILABLE, null);

        AgentRunResult result = run(new GrantSource.SchedulerJob(CumsAtTheWire.JOB));

        assertRefusedBeforeModel(result, RunEndReason.TOKEN_REFUSED, "HTTP_503");
        assertThat(cums.mintRequests()).hasSize(1);
    }

    @Test
    @DisplayName("a code that is not a code is stored as UNKNOWN_CODE")
    void should_storeUnknownCode_when_cumsCodeIsFreeText() {
        cums.refusesGrant(HttpStatus.FORBIDDEN, "not a code; anna.meier@example.com");

        AgentRunResult result = run(new GrantSource.SchedulerJob(CumsAtTheWire.JOB));

        assertRefusedBeforeModel(result, RunEndReason.GRANT_REFUSED, "UNKNOWN_CODE");
    }

    @Test
    @DisplayName("no caller secret: GRANT_REFUSED, CALLER_SECRET_MISSING, CUMS not called; a startup warning says so")
    void should_refuseWithoutCallingCums_when_noCallerSecret(CapturedOutput output) {
        for (String missing : new String[] {"", "  ", "none", "NONE"}) {
            AgentProperties withoutSecret = new AgentProperties(true, properties.loop(), properties.anthropic(),
                    new AgentProperties.Cums(properties.cums().baseUrl(), missing), properties.gateway());
            CumsAgentClient client = new CumsAgentClient(restTemplate, withoutSecret);
            AgentLoop loopWithoutSecret = new AgentLoop(model, decisionLog, withoutSecret, client, restTemplate,
                    NO_TOOLS, gate, clock);

            // No expectation is registered: any request to CUMS fails the test
            AgentRunResult result = loopWithoutSecret.run(new AgentRunRequest(
                    new GrantSource.SchedulerJob(CumsAtTheWire.JOB), SYSTEM, "Tidy up"));

            assertRefusedBeforeModel(result, RunEndReason.GRANT_REFUSED, "CALLER_SECRET_MISSING");
            client.warnWithoutCallerSecret();
        }
        // Even a handed-over grant: no mint without the secret
        AgentProperties withoutSecret = new AgentProperties(true, properties.loop(), properties.anthropic(),
                new AgentProperties.Cums(properties.cums().baseUrl(), ""), properties.gateway());
        CumsAgentClient client = new CumsAgentClient(restTemplate, withoutSecret);
        AgentRunResult handedOver = new AgentLoop(model, decisionLog, withoutSecret, client, restTemplate, NO_TOOLS, gate,
                clock)
                .run(new AgentRunRequest(new GrantSource.HandedOver(UUID.randomUUID(), CumsAtTheWire.grantSecret()),
                        SYSTEM, "Tidy up"));
        assertRefusedBeforeModel(handedOver, RunEndReason.GRANT_REFUSED, "CALLER_SECRET_MISSING");
        cums.verify();
        assertThat(cums.grantRequests()).isEmpty();
        assertThat(cums.mintRequests()).isEmpty();
        assertThat(output.getAll()).contains("No CUMS caller secret");
        // Positive control: the context's client has the test secret and warns not
        assertThat(new CumsAgentClient(restTemplate, properties).hasCallerSecret()).isTrue();
    }

    @Test
    @DisplayName("the credential types never show a token, grant secret, JWT or caller secret in toString")
    void should_maskSecrets_when_toString() {
        String secret = CumsAtTheWire.grantSecret();
        assertThat(new RunGrant(UUID.randomUUID(), secret, PrincipalKind.ORGANIZER).toString())
                .doesNotContain(secret).contains("principalKind=ORGANIZER");
        assertThat(new AgentToken("eyJ.secret-token.sig", Instant.EPOCH).toString())
                .doesNotContain("secret-token");
        assertThat(new GrantSource.OrganizerLogin("cognito.jwt.value").toString()).doesNotContain("cognito.jwt.value");
        assertThat(new GrantSource.LinkedRun("run-1", secret).toString()).doesNotContain(secret);
        assertThat(new GrantSource.HandedOver(UUID.randomUUID(), secret).toString()).doesNotContain(secret);
        assertThat(properties.cums().toString()).doesNotContain(CumsAtTheWire.CALLER_SECRET).contains("callerSecret=****");
        assertThat(properties.toString()).doesNotContain(CumsAtTheWire.CALLER_SECRET);
        assertThat(new AgentRunRequest(new GrantSource.OrganizerLogin("cognito.jwt.value"), SYSTEM, "u")
                .toString()).doesNotContain("cognito.jwt.value");
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────────────────────

    private AgentRunResult run(GrantSource grant, AgentTool... tools) {
        return loop.run(toolRegistry.request(grant, SYSTEM, "Tidy up the company list", List.of(tools)));
    }

    private void assertRefused(AgentRunResult result, RunEndReason reason, String code) {
        assertThat(result.status()).isEqualTo(AgentRunStatus.HANDED_TO_ORGANIZERS);
        assertThat(result.reason()).isEqualTo(reason);
        assertThat(result.detail()).isEqualTo(code);
        assertThat(runRow(result.runId())).containsEntry("status", "HANDED_TO_ORGANIZERS")
                .containsEntry("end_reason", reason.name()).containsEntry("end_detail", code);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM agent_model_calls WHERE run_id = ?", Integer.class,
                result.runId())).isEqualTo(model.requests().size());
    }

    /** A refusal before the first model call: no model call at all. */
    private void assertRefusedBeforeModel(AgentRunResult result, RunEndReason reason, String code) {
        assertRefused(result, reason, code);
        assertThat(model.requests()).as("no model call").isEmpty();
        assertThat(result.steps()).isZero();
    }

    private Map<String, Object> runRow(UUID runId) {
        return jdbc.queryForMap("SELECT status, trigger, end_reason, end_detail, grant_run_id, principal_kind FROM agent_runs "
                + "WHERE id = ?", runId);
    }

    private List<String> toolCallStatuses(UUID runId) {
        return jdbc.queryForList("SELECT status FROM agent_tool_calls WHERE run_id = ? ORDER BY started_at, tool_use_id",
                String.class, runId);
    }

    private void gatewayExpects(HttpMethod method, ExpectedCount count) {
        cums.server().expect(method, COMPANIES).times(count)
                .andExpect(r -> gatewayAuthorizations.add(r.getHeaders().getFirst(HttpHeaders.AUTHORIZATION)))
                .respondWithExample();
    }

    /** GET /companies; answers the status. */
    private static RecordingTool reading() {
        return TestTools.calling("listCompanies", OperationClass.READ, Set.of(Operation.of("GET /companies")),
                (input, context) -> {
                    ApiResponse response = context.api().get("/companies");
                    return ToolOutcome.ok(String.valueOf(response.status()));
                });
    }

    /**
     * The autonomy gate (story 7) lets the binding {@code createCompany} run alone only with two
     * approved precedents: two earlier calls of it by scheduler runs (ORGANIZER_DELEGATE), approved.
     */
    private void clearWritesOfScheduledRuns() {
        for (int i = 0; i < 2; i++) {
            UUID run = UUID.randomUUID();
            UUID step = UUID.randomUUID();
            UUID call = UUID.randomUUID();
            UUID verdict = UUID.randomUUID();
            jdbc.update("INSERT INTO agent_runs (id, status, started_at, ended_at, trigger, end_reason, grant_run_id, "
                    + "principal_kind) VALUES (?, 'DONE', now(), now(), 'SCHEDULED', 'COMPLETED', ?, "
                    + "'ORGANIZER_DELEGATE')", run, UUID.randomUUID());
            jdbc.update("INSERT INTO agent_steps (id, run_id, step_number, started_at) VALUES (?, ?, 1, now())",
                    step, run);
            jdbc.update("INSERT INTO agent_tool_calls (id, run_id, step_id, tool_use_id, tool_name, input, status, "
                    + "started_at) VALUES (?, ?, ?, 'toolu_seed', 'createCompany', '{}'::jsonb, 'PENDING_APPROVAL', "
                    + "now())", call, run, step);
            jdbc.update("INSERT INTO agent_verdicts (id, run_id, tool_call_id, operation_class, outcome, inputs, "
                    + "cited_precedents, decided_at) VALUES (?, ?, ?, 'BINDING', 'PENDING_APPROVAL', '{}'::jsonb, "
                    + "'[]'::jsonb, now())", verdict, run, call);
            jdbc.update("INSERT INTO agent_approvals (id, run_id, verdict_id, state, decided_by, created_at, "
                    + "decided_at) VALUES (?, ?, ?, 'APPROVED', 'organizer.test', now(), now())", UUID.randomUUID(),
                    run, verdict);
        }
    }

    /** POST /companies; with {@code swallow} it hides a refused mint and claims success. */
    private static RecordingTool writing(boolean swallow) {
        return TestTools.calling("createCompany", OperationClass.BINDING, Set.of(Operation.of("POST /companies")),
                (input, context) -> {
                    try {
                        ApiResponse response = context.api().post("/companies",
                                TestTools.input("name", "Agent Test AG"));
                        return ToolOutcome.ok(String.valueOf(response.status()));
                    } catch (AgentCredentialsRefusedException e) {
                        if (swallow) {
                            return ToolOutcome.ok("created, honestly");
                        }
                        throw e;
                    }
                });
    }

    private LlmResponse useTool(String... names) {
        LlmContent[] uses = new LlmContent[names.length];
        for (int i = 0; i < names.length; i++) {
            uses[i] = new LlmContent.ToolUse("tu_" + (i + 1), names[i], TestTools.input("query", "all"));
        }
        return model.response(LlmStopReason.TOOL_USE, TOKENS, uses);
    }

    private LlmResponse endTurn() {
        return model.response(LlmStopReason.END_TURN, TOKENS, new LlmContent.Text("Done."));
    }
}
