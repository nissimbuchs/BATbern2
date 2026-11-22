---
title: 'agent-service runs dark in production behind the gateway'
type: 'feature'
ticket: '1'
created: '2026-11-22'
status: 'built'
baseline_revision: 'a2bb2d31191d4f5661e2176db41f52533609df64'
route: 'full'
route_source: 'auto'
risk: 'high'
review: 'quick'
review_source: 'pinned'
lenses_ran: ['quick']
review_loop_iteration: 0
context:
  - '{project-root}/docs/architecture/module-map.md'
  - '{project-root}/_bmad-output/initiative-agentic-interaction/tickets/epic-agent-runtime/epic-agent-runtime.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The agent runtime (epic R1) needs its own deployable Java service before any loop, gate or log can ship; today nothing builds, deploys, routes or runs `agent-service`.

**Approach:** Stand up `services/agent-service` from the PCS template with its own Flyway history, a Cognito-only production security chain and one read endpoint `GET /api/v1/agent/runs` (organizers, empty page), routed by the Spring gateway, deployed by its own CDK stack and the existing build/deploy pipeline, startable by `make dev-native-up`, shipped dark (`agent.enabled=false`).

## Boundaries & Constraints

**Always:** Java 21 / Spring Boot 4.0.9 as the other services. Decoder `BatbernJwtDecoder.fromEnvironment(env, EnumSet.of(COGNITO))` (agent tokens refused). `@PreAuthorize("hasRole('ORGANIZER')")` on the endpoint plus the chain matcher. Shared `ErrorResponse` and `PaginatedResponse` envelope. Flyway table `flyway_schema_history_agent`, `baseline-version: 0`. One PR deploy must succeed from an account where the ECR repo does not exist yet. Tests: Testcontainers, production chain, no own-code mocks. Every other Dockerfile keeps building with the new `settings.gradle` include (#1220).

**Never:** No CUMS caller secret wiring (story 4), no Anthropic secret (story 3), no loop or LlmPort (story 2), no frontend types or UI. No `/internal/**` calls. No read of another service's tables (no `JwtRolesConverter` DB fallback: it reads CUMS tables). No push, no PR. No `ecr:CreateRepository` from the workflow role (the role lacks it; checked by IAM simulation 2026-11-22).

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Organizer lists runs | Cognito JWT with `custom:role` ORGANIZER | 200 `{data: [], pagination: {page 0, ...}}` | none |
| Anonymous | no token | 401 | shared chain |
| Speaker | Cognito JWT role SPEAKER | 403 | shared chain + method security |
| Agent token | `iss batbern-agent` token (carries `act`) | 401 (kind not accepted) | decoder refuses |
| Gateway route | `/api/v1/agent/runs` | forwarded to `services.agent.url` | unknown → 404 as before |
| Gateway prefix trap | `/api/v1/agent-settings` | still CUMS | n/a |
| First deploy | repo `batbern/staging/agent-service` absent | image handed from build to deploy job, pushed after Layer 0 created the repo | deploy fails loudly if the artifact is missing |

</frozen-after-approval>

## Code Map

- `services/partner-coordination-service/{build.gradle,Dockerfile,src/main/resources/{application.yml,logback-spring.xml},.../config/SecurityConfig.java}` -- template; copy only what agent-service needs (no SES, no caches, no S2S clients, no Resilience4j).
- `shared-kernel/.../security/BatbernJwtDecoder.java`, `JwtRolesConverter.java` (null DataSource = no DB fallback), `dto/PaginatedResponse`, `api/PaginationMetadata`, testFixtures `AbstractIntegrationTest`, `EndpointAuthorizationGuard`, `security/TestTokens`, `LogAddressGuard`, `LogbackConfigUnderTest`.
- `settings.gradle` include; root `build.gradle` configures every `:services:*` automatically; `gradle/response-conformance.gradle`.
- Dockerfiles with the `mkdir -p` project list: `api-gateway/Dockerfile`, `services/{event-management,partner-coordination,company-user-management}-service/Dockerfile`.
- Gateway: `api-gateway/.../routing/DomainRouter.java` (determineTargetService, getServiceUrl), `src/main/resources/application.yml` `services.*`, tests `routing/DomainRouterTest.java`.
- CDK: `infrastructure/lib/constructs/domain-service-construct.ts` (`createDomainService`, image `batbern/<env>/<serviceName>-service` via `fromRepositoryName`), `lib/stacks/partner-coordination-stack.ts` (template), `lib/stacks/cicd-stack.ts` (ECR list), `lib/stacks/api-gateway-service-stack.ts` (~148 URLs), `bin/batbern-infrastructure.ts` (~227, ~332-358), `package.json` deploy scripts, tests `cicd-stack.test.ts`, `cost-footprint.test.ts`, `domain-event-listener-alarm.test.ts`, `rollback-covers-promoted-services.test.ts`, `deploy-latency-alarm-silence.test.ts`.
- CI: `.github/workflows/build.yml` (matrix ~153, push ~370, sonar ~1026-1130), `.github/workflows/deploy-staging.yml` (change detection ~175, fast path ~420, validation ~490, stacks ~545, Layer 0 ~595, Layer 4.x ~690-860, selective ~905-960), `deploy-code-staging.yml` case list, `scripts/ci/{ensure-services-run-image,wait-ecs-services-stable,promote-ecr-tag,rollback-deployment,smoke-tests,setup-ecr-repositories}.sh`, `scripts/ci/test-ensure-services-run-image.sh`, `infrastructure/lambda/github-issues-integration/index.ts` (alarm → log group), `.githooks/pre-push`, `sonar-project.properties`, `scripts/security-scan.sh`.
- Local dev: `scripts/dev/lib/native-services.sh`, `start-all-native.sh`, `restart-native.sh`, `status-native.sh`, `stop-all-native.sh`, `logs-native.sh`, `status-all-instances.sh`. Port BASE_PORT+3 is free (stub removed by 17.2).
- DAST: `scripts/ci/access-control-check.py` globs `docs/api/*.openapi.yml` (verified), so the new spec is probed automatically.
- Not touched: `secrets-stack.ts` caller secret, `docker-compose*.yml`, retired production workflows (`deploy-production.yml`, `promote-to-production.yml`, `validate-environment.sh`, `monitor-canary.sh`).

## Tasks & Acceptance

**Execution:**
- [x] `docs/api/agent-api.openapi.yml` -- `GET /agent/runs` (`listAgentRuns`, tag Agent Runs, `page`/`limit`, 200 `PaginatedAgentRunResponse` with `AgentRun {id uuid, status enum RUNNING|DONE|FAILED|HANDED_TO_ORGANIZERS, startedAt, endedAt?}`, 401/403 shared ErrorResponse) -- contract first.
- [x] `services/agent-service/**` -- build.gradle (openApiGenerate interfaceOnly, flyway table, response conformance on the agent spec), Dockerfile, application.yml (`agent.enabled: false`, Flyway history + baseline 0, local/test profiles), logback-spring.xml, `AgentServiceApplication`, `config/SecurityConfig` (prod chain `!local`, local permit-all chain, COGNITO-only decoder, converter without DB), `V1__agent_runs.sql` + `AgentRun` entity + repository + `AgentRunController` implementing the generated interface (reads the table, paged, newest first) -- the dark service.
- [x] `services/agent-service/src/test/**` -- `AgentRunControllerIntegrationTest` (organizer 200 empty page, anonymous 401, speaker 403, agent token 401 with positive control that the same token verifies with the agent kind), `EndpointAuthorizationGuardTest`, `FlywayBaselineIntegrationTest` (non-empty schema → baseline 0 then V1 applied in `flyway_schema_history_agent`), `AgentSwitchDefaultTest` (`agent.enabled` false by default), `LogAddressGuardTest`, `LogEncoderMaskingTest`, test resources.
- [x] `settings.gradle`, 4 Dockerfiles -- include project, add `services/agent-service` to `mkdir -p`.
- [x] `api-gateway` DomainRouter + application.yml + DomainRouterTest + a wire test (JDK HttpServer stub) -- route `/api/v1/agent/` prefix only.
- [x] `infrastructure` -- `lib/stacks/agent-service-stack.ts` (serviceName `agent`, 256/1024, 1 task, min1/max2, EcsServiceAlarms), bin wiring (deps like PCS minus SES), cicd-stack `agent-service` repo, gateway `AGENT_SERVICE_URL`, `deploy:staging:layer4-agent-service` (`--exclusively`) and add the stack to `layer4-services`/`microservices`; tests: new `agent-service-stack.test.ts`, extend cicd, cost-footprint, domain-event-listener tests.
- [x] CI workflows and scripts listed in Code Map -- add agent-service/`AgentService` everywhere a service list exists; first-push hand-over (Design Notes).
- [x] Local dev scripts -- service `agent`, port BASE_PORT+3, heap 320m, `AGENT_SERVICE_URL` in `.env.native.*`.
- [x] Docs (doc-drift mapped, same commit): `docs/architecture/module-map.md`, `06-backend-architecture.md`, `02-infrastructure-deployment.md`, `source-tree.md`, `docs/api/README.md`, `docs/deployment/cicd-pipeline-guide.md`, `docs/guides/local-development-setup.md`, `CLAUDE.md`, `_bmad-output/project-context.md` ports, `.github/doc-drift-mappings.yml` entry for `services/agent-service/`.

**Acceptance Criteria:**
- Given the module tests, when `./gradlew :services:agent-service:test` runs, then all pass including the guard, conformance and Flyway baseline tests.
- Given the gateway tests, when `:api-gateway:test` runs, then `/api/v1/agent/runs` reaches the agent URL and `/api/v1/agent-settings` stays CUMS.
- Given the CDK tests, when run, then the agent stack, ECR repo, gateway URL and every service-list consistency test pass.
- Given `make dev-native-up`, when it starts, then agent-service answers `/actuator/health` on 8003 (manual, see Verification).
- Given the first PR deploy, when the repo is missing, then the deploy pushes the build's image after Layer 0 and deploys `BATbern-staging-AgentService` (verified only after the owner opens the PR).

## Implementation Notes

- Gates (unattended build, owner go for the whole epic): plan is about 2,600 tokens, above 1,600; kept whole because the story is one shippable goal (C1: one deploy) and splitting CDK/CI from the service would ship an undeployable half. No open questions; checkpoint approved and continued without a human per the owner's go.
- Machine rules (rack, mandatory; it has crashed from violations): one heavy build at a time (never Gradle, Vitest, Playwright or jest suites in parallel, and none next to another agent's build); Gradle always with `--max-workers=4`, then `./gradlew --stop`; output via `set -o pipefail; ... 2>&1 | tee ~/.cache/batbern-test-logs/<name>.log` and grep the file; never `make test`; never `make dev-native-up` while a Gradle run is going. Do not commit, push or open a PR; never `git add -A` (untracked `*.mp4` under film-batbern61/ must stay untracked); never bare `git stash`. No secrets, no real PII.

## Plan Change Log

- 2026-11-22 (build): migration folder `db/agent-migration`, not `db/migration`. shared-kernel ships
  `db/migration/V1__Initial_baseline.sql` on every service's classpath (the other services shadow it
  with a file of the same name), so `V1__agent_runs.sql` there collides with version 1. The folder
  is set in application.yml, the test profile (`src/test/resources/application-test.yml` shadows the
  shared testFixtures file, which points Flyway at `db/migration`) and build.gradle. The deploy
  workflow's migration regex (backup, fast-path exclusion) now matches `db/<name>-migration/V*.sql`.
- 2026-11-22 (build): entity `AgentRunEntity`, so the generated DTO `AgentRun` is imported, not
  named in full.
- 2026-11-22 (build): a 400 for a bad `page`/`limit` with the shared `ErrorResponse` (spec + handler
  + test); without it a bean-validation failure answered with Spring's default body.
- 2026-11-22 (build): paging through `DeterministicPaging.pageRequest` (shared-kernel guard #1131
  fails a raw `PageRequest.of`).
- 2026-11-22 (build): `JwtRolesConverter` without a DataSource logs INFO, not a WARN claiming
  production must supply one (agent-service passes null on purpose). Log line and Javadoc only.
- 2026-11-22 (build): CI found beyond the Code Map: ZAP matrix (`security-scan.yml`, guarded by
  `check-zap-spec-coverage.py`), `test-ecr-validation.yml`, Makefile help text, infrastructure
  CLAUDE.md. Layer 4.5 (agent) deploys before 4.6 (gateway): a Service Connect client only resolves
  endpoints that existed when its tasks started. First-push login uses `amazon-ecr-login` (no new
  CLI action); `ecr:DescribeRepositories` comes from CdkDeploymentPolicy (same role), pinned by a test.

## Review Triage Log

Pass 1 (quick lens): high 0, medium 3, low 3, false 0, maybe-false 0.

| # | Finding | Verdict | Route | Evidence / action |
|---|---|---|---|---|
| 1 | FlywayBaselineIntegrationTest read test-profile Flyway values, so dropping `baseline-version: 0` from production yml would pass | medium | patch | Test yml repeats the values; now reads the production yml base document. Mutation run 2026-11-22 (deleted the line): test FAILED; restored, suite 16/16 green |
| 2 | First-deploy auto-rollback counts agent-service as ROLLBACK_FAILED (no staging-stable, no previous deployment) | low | rejected | Happens at most once, only when the first deploy's post-deploy gate already failed; fix needs a new branch in rollback-deployment.sh. Noted in the report |
| 3 | Added `ecr:DescribeRepositories` in WorkflowRuntimePolicy redundant; its test proved nothing | low | patch | CdkDeploymentPolicy already grants it (IAM simulation: allowed). Action removed; test now asserts the deployment policy grant (pre-push hook needs the string) |
| 4 | Selective-path image validation compares `agent-service` against `agent` in CHANGED_SERVICES, so it never checks | medium | defer | Pre-existing for EMS, PCS, CUMS (same short-name mismatch); fixing it changes deploy gating for every service |
| 5 | Stale port/service text: Makefile 9001/9002/9004, .env.example, deploy-staging comment; gate spike build-images.sh lacks agent-service | low | patch + defer | Text fixed; `scripts/gate/build-images.sh` belongs to the parked P5 spike, deferred |
| 6 | No automated organizer 200 / speaker 403 check against the deployed route (only DAST anonymous + smoke health) | medium | defer | Story 9 owns per-role read API tests; deferred so it adds a Bruno request for `/agent/runs` |

## Design Notes

Architecture note: modules touched — new agent-service, gateway routing, CDK, CI, local dev · new dependencies — gateway → agent-service (Service Connect `agent:8080`); agent-service depends on shared-kernel only · new endpoint `GET /api/v1/agent/runs`; nearest existing `GET /api/v1/partners` (PCS list) and CUMS `/api/v1/agent-settings`; neither can be extended: the runs belong to agent-service's own tables (AD-9) · reused — `createDomainService`, `EcsServiceAlarms`, `BatbernJwtDecoder`, `PaginatedResponse`, shared test fixtures · deviation — none from the map; the service scans only `ch.batbern.agent` (it needs no shared `EmailService`: the agent never sends mail itself, AD-5).

ECR first push (decided 2026-11-22): CDK stays the only owner of every ECR repo (`agent-service` joins the cicd-stack list, same lifecycle and RETAIN). Build creating the repo was rejected: the workflow role has no `ecr:CreateRepository`, and only the CloudFormation exec role does (IAM simulation), so the first build could not create it. Instead build.yml, when `describe-repositories` says the repo is missing, saves the built image (`docker save | gzip`) as artifact `first-push-image-<service>` instead of pushing; deploy-staging detects missing repos before validation, skips their image check, and after Layer 0 (which creates the repo) loads and pushes the artifact, then deploys Layer 4 as usual. Once the repo exists the path is dormant. No CloudFormation name collision, no manual pre-step.

V1 creates a minimal `agent_runs` table now, so the Flyway history is provable in tests and in production and the endpoint reads a real table; story 2 extends it with V2. Baseline 0 matters: the shared database is non-empty, so Flyway baselines first; with the default baseline 1, V1 would be skipped.

Epic 17 rule: a new service has no violations or archunit store (start and end size 0); test-ratchet baseline unchanged. Finding for the refactor sweep: CUMS already owns `agent_settings`, `agent_grants`, `agent_allowlist`, so "agent_* tables belong to agent-service" (AD-9) needs a sharper name rule.

## Verification

**Commands:**
- `set -o pipefail; ./gradlew --max-workers=4 :services:agent-service:test 2>&1 | tee ~/.cache/batbern-test-logs/agent-service-test.log` -- BUILD SUCCESSFUL
- `set -o pipefail; ./gradlew --max-workers=4 :api-gateway:test 2>&1 | tee ~/.cache/batbern-test-logs/api-gateway-test.log` -- BUILD SUCCESSFUL
- `cd infrastructure && npx jest test/unit/agent-service-stack.test.ts test/unit/cicd-stack.test.ts test/unit/cost-footprint.test.ts test/unit/domain-event-listener-alarm.test.ts test/unit/rollback-covers-promoted-services.test.ts test/unit/deploy-latency-alarm-silence.test.ts test/unit/workflow-run-scripts-parse.test.ts test/unit/dockerfile-gradle-version.test.ts` -- pass
- `python3 scripts/ci/access-control-check.py --list | grep agent` -- lists `GET /agent/runs`; `scripts/ci/test-ensure-services-run-image.sh`; `cd web-frontend && npm run lint:api`; `make check-test-ratchet`; `actionlint` if installed.

**Manual checks:**
- `make dev-native-up` then `curl localhost:8003/actuator/health` = UP and `curl -i localhost:8000/api/v1/agent/runs` = 401.
