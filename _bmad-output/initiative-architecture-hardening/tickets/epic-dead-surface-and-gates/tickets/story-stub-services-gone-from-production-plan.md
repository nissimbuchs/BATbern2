---
title: 'Stub services gone from production (17.2 part A)'
type: 'refactor'
ticket: '2'
created: '2026-11-07'
status: 'done'
baseline_revision: '061b3411d2d99001f76f2dba545b78f1b889925c'
route: 'full'
route_source: 'auto'
risk: 'high'
review: 'quick'
review_source: 'pinned'
lenses_ran: ['quick']
review_loop_iteration: 0
context:
  - '{project-root}/docs/architecture/ADR-014-backend-service-consolidation.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** ADR-014 made speaker-coordination (SCS) and attendee-experience (AES) dormant, yet production still runs SCS as one Fargate task serving no route, keeps AES parked at 0 with the gateway routing `/api/v1/content/**` to it, and every build, deploy, dev-native start and required check still carries both (#1216).

**Approach:** Delete both modules and every reference to them: Gradle, CDK stacks and ECR repos, CI matrix and deploy steps, scripts, compose, hooks, the gateway route, docs. Production cleanup outside the PR, each step with the owner's go at the time: empty the two ECR repos before the PR (so the CICD stack can delete them), drop the two required checks before merge, `delete-stack` the two orphaned stacks after the deploy.

## Boundaries & Constraints

**Always:** grep every reference before deleting (inventory in Code Map); the remaining services build, test and start unchanged; CDK synth for staging has no SCS/AES stack and no other stack loses or gains anything except the gateway env vars and the CICD repos; the deploy is clean (no CloudFormation export or resource collision).

**Never:** no EventBridge change (part B, entry 2b); no edit to `deploy-production.yml`, `promote-to-production.yml` or `*.disabled` workflows (owner keeps them); no Flyway migration or DB change (`flyway_schema_history_speaker_coordination` stays); no edit of `docs/plans/**`, `docs/prd/**` or archived stories (history); no production command from the implementer.

</frozen-after-approval>

## Code Map

Inventory (checked 2026-11-07); "rm" = delete the lines/entries for SCS and AES.
- `services/speaker-coordination-service/`, `services/attendee-experience-service/` -- delete; nothing else uses their code. `shared-kernel/.../types/SpeakerWorkflowState.java:30` stale `@see` -- drop.
- Gradle/Sonar: `settings.gradle:27,30`, `build.gradle:125` (comment), `sonar-project.properties:17,90-96,132-141`, `.github/dependabot.yml:40` (comment).
- CDK: `infrastructure/lib/stacks/{speaker-coordination,attendee-experience}-stack.ts` delete; `bin/batbern-infrastructure.ts:19,21,238,240,333-356,387-406`; `lib/stacks/cicd-stack.ts:60,62` (ECR list); `lib/stacks/api-gateway-service-stack.ts:121,123` (service URL env vars); `lambda/github-issues-integration/index.ts:354,356,601`; `deploy-selective.sh:80,82`; `package.json:27,37,39,41`; `infrastructure/CLAUDE.md:266`; `test/unit/cost-footprint.test.ts:55-73,79`. Exports of both stacks are imported by no stack (checked live).
- CI: `build.yml:8,149,151,887,926-938,953,999`; `deploy-staging.yml:176-188,426-433,494,549,560-572,775-811,851-887,929,931,1002-1017,1036-1051,1110,1112`; `deploy-code-staging.yml:57-64`; `test-ecr-validation.yml:37`; `.github/doc-drift-mappings.yml:27,55`; `.githooks/pre-push:77-79,97-99,132`.
- Scripts: `scripts/security-scan.sh:32,34`; `scripts/ci/{setup-ecr-repositories,promote-ecr-tag,rollback-deployment,validate-environment,monitor-canary,test-parallel-builds,smoke-tests}.sh`; `scripts/dev/{start-all-native,status-native,restart-native,logs-native,stop-all-native,status-all-instances}.sh`, `scripts/dev/lib/native-services.sh`; ports BASE+3/BASE+5 simply stay unused. `scripts/dev/doc-audit-retrospective*.py` (SCS test paths).
- Compose: `docker-compose.yml:44,46,183-224,267-308,316,320` (+ their `EVENT_BUS_*` env lines go with the services).
- Gateway: `DomainRouter.java:38-39,44-45,76,116-117,146,148`; `application.yml:37-42`; tests `DomainRouterTest.java:41,43,91,154-165`, `RequestRoutingIntegrationTest.java:42,44`, `src/test/resources/application-test.yml:67-87`. No frontend or Bruno caller of `/api/v1/content`.
- Other: `apps/projectdoc/src/config/reports-config.js:32,34,136,139`. Leave `web-frontend/e2e/.../capture-user-guide.spec.ts:540` and EMS `SessionUser.java:37` (unrelated wording).
- Docs: `CLAUDE.md` service lists/ports/log groups; `docs/architecture/{01,02,03,04-api-core,04-api-design,06,06c,07,index,source-tree}.md`; ADR-014 (operational-state note: removed 2026-11); `docs/deployment/{cicd-pipeline-guide,cicd-setup-guide,native-execution}.md`; `docs/operations/runbooks.md`; `docs/presentation/diagrams/02-*,03-*` if they list services.

## Tasks & Acceptance

**Execution:**
- [x] Modules, Gradle, Sonar, shared-kernel Javadoc -- delete/rm as mapped.
- [x] CDK bin, stacks, CICD ECR list, gateway env vars, lambda map, scripts, package.json, cost-footprint test -- rm; `cdk synth` must still pass.
- [x] Gateway `DomainRouter` + `application.yml` + tests -- remove the AES/SCS URLs and the `/api/v1/content` route; a test asserts `/api/v1/content/x` is no longer routed (the router's existing not-found behaviour).
- [x] CI workflows, doc-drift mappings, pre-push hook -- rm; YAML stays valid, `check-skip-propagation.py` OK.
- [x] Dev scripts, compose, CI scripts, projectdoc config -- rm.
- [x] Docs as mapped -- three backend services (EMS, CUMS, PCS) plus the gateway.
- [x] `web-frontend/scripts/test-ratchet-baseline.txt` -- `--update` only if lines named the removed modules (expected none).

**Acceptance Criteria:**
- Given the branch, when grepping for `speaker-coordination|attendee-experience|SpeakerCoordination|AttendeeExperience` outside `docs/plans`, `docs/prd`, `_bmad-output/implementation-artifacts`, `docs/stories/archived`, the two kept workflows and `*.disabled`, then only deliberate historical mentions remain (each listed in Implementation Notes).
- Given `cdk synth --context environment=staging`, then no SCS/AES stack is produced and the template diff of the other stacks shows only the gateway env vars and the two ECR repos removed.
- Given `make dev-native-up-instance BASE_PORT=9000`, then gateway, EMS, CUMS, PCS and the frontend start and report healthy.

## Design Notes

Architecture note: modules: services (2 removed), api-gateway routing, infrastructure (CDK, CI, scripts) · new dependencies: none · endpoints: gateway route `/api/v1/content/**` removed (no replacement; nothing calls it) · reused: none · deviations: none.

Deploy order (owner steps, not the implementer's): empty ECR repos → PR deploys (CICD stack deletes the empty repos; stub stacks are no longer in the app, so the pipeline never touches them) → required checks dropped → merge → `aws cloudformation delete-stack` for `BATbern-staging-SpeakerCoordination` and `-AttendeeExperience` → AC1 checked live.

## Verification

Rack rules for every run, including any implementation subagent: one heavy job at a time; Gradle `--max-workers=4`, and `./gradlew --stop` before Node work; Vitest `--maxWorkers=4`; logs to `~/.cache/batbern-test-logs/` (never `/tmp`); before each run check `grep anon /sys/fs/cgroup/dev.slice/memory.stat` and stop above 12 GB.

**Commands:**
- `set -o pipefail; ./gradlew --max-workers=4 :api-gateway:test 2>&1 | tee ~/.cache/batbern-test-logs/2a-gateway.log` -- expected: BUILD SUCCESSFUL.
- `set -o pipefail; ./gradlew --max-workers=4 projects build -x test 2>&1 | tee ~/.cache/batbern-test-logs/2a-build.log` -- expected: success, no SCS/AES project.
- `./gradlew --stop; cd infrastructure && npx jest --maxWorkers=4 2>&1 | tee ~/.cache/batbern-test-logs/2a-cdk.log` -- expected: all pass.
- `cd infrastructure && npx cdk synth --context environment=staging -q` -- expected: success; `cdk list` shows no SCS/AES stack.
- `cd web-frontend && npm run check:test-ratchet` -- expected: exit 0.
- `bash -n` on every edited shell script; YAML parse of edited workflows -- expected: clean.
- `make dev-native-up-instance BASE_PORT=9000`, then `make dev-native-status-all`, then `make dev-native-down-instance BASE_PORT=9000` -- expected: remaining services healthy.

## Implementation Notes

Implemented 2026-11-07 on `feature/17-0a-17-2-rules-and-stubs` (uncommitted at hand-off).

**Finding that changes the owner's deploy order.** The two ECR repositories carry
`DeletionPolicy: Retain` / `UpdateReplacePolicy: Retain` in the synthesized CICD template
(`cicd-stack.ts` sets `RemovalPolicy.RETAIN` when `isProduction`, and the staging config is
`isProduction: true`). Removing them from the template therefore does not delete them: the
deploy succeeds whether or not they are empty, and both repositories stay in the account. Emptying
them first is not needed; deleting them is a manual owner step after the deploy
(`aws ecr delete-repository --force` for `batbern/staging/speaker-coordination-service` and
`batbern/staging/attendee-experience-service`). Recorded in ADR-014 "Operational state (2026-11)".

**Additions beyond the Code Map** (found by the grep): `ServiceHealthController` Javadoc example,
`VpcInternalAuthorizationManager` Javadoc use case, `.env.example` port comments,
`infrastructure/lib/constructs/domain-service-construct.ts` Javadoc, `docs/prd-enhanced.md`
(current-state sentence), `docs/architecture/04-api-attendee-experience.md` (note that
`/content/search` is neither implemented nor routed). `package.json` `--concurrency=6` lowered to 4
on the two multi-stack deploy scripts (four stacks left).

**Deliberate historical mentions left** (AC1 grep, outside the excluded paths):
- `services/event-management-service/.../db/migration/V16__*.sql`, `V23__*.sql`: applied Flyway
  migrations, frozen (checksum).
- `SessionUser.java:37`, `capture-user-guide.spec.ts:540`: per the Code Map, unrelated wording.
- `DomainRouterTest.java:152`: comment explaining why `/api/v1/content/**` is no longer routed.
- `docs/architecture/04-api-speaker-coordination.md`, `04-api-attendee-experience.md` and the links
  to them (`04-api-core.md`, `04-api-design.md`, `index.md`, `epic-6-speaker-onboarding-plan.md`):
  domain API narratives whose endpoints are served by EMS; the file names are domain names.
- ADR-002, ADR-004, ADR-009, ADR-013, ADR-014 (decision record and the 2026-11-04 table): history.
- `docs/assessment/system-quality-assessment.md`, `docs/implementation-readiness-report-*.md`,
  `docs/qa/**`, `docs/sprint-change-proposal-2026-06-11.md`, `docs/wireframes/sitemap.md`,
  `docs/user-guide/appendix/{changelog,feature-status,glossary}.md`,
  `docs/user-guide/entity-management/speakers.md`: dated reports and changelog entries.
- `docs/user-guide/attendee-experience/**` and the links to it: the user-guide folder for Epic 7,
  not the service.
- `docs/presentation/diagrams/02-*`, `03-*` (`.excalidraw` + `.svg`): presentation snapshot, already
  stale in other places (it still shows FARGATE_SPOT 70%); removing two boxes means re-laying out
  the drawing in Excalidraw. Not done, left for a deliberate redraw.
- `_bmad-output/**` planning files (initiative, epic, tickets, notes, brainstorming): planning
  history.
- `scripts/ci/regression-suite.sh` ("Speaker Coordination API Health" etc.): dead script, no workflow
  calls it (`grep -r regression-suite .github` finds nothing); its paths (`/api/speakers`,
  `/api/content`) never existed on the current gateway. Not touched.

**Review fixes (second pass).** Phantom TASK_SC/TASK_AE edges in the 02 production diagram;
SCS/AES node, `/content` edge, size note and routing-table entry in 01; 01 item 7 and both
"removed" callouts now say the ECR repositories are retained until the owner deletes them by hand
(consistent with ADR-014); SCS dropped from the exception-handler sentence in 06; SLA entries for
both services removed from 08; `local-development-setup.md` and `README.md` list the four processes and their real ports
(8000/8001/8002/8004, frontend 8100; heap total 1920m derived from `service_heap`), and the
"All six services" sentence in the `start-all-native.sh` header is gone; the invalid
`stop-all-native.sh <service>` examples in `native-execution.md` replaced by `DEV_SERVICES=...
make dev-native-up`; `DomainRouterTest` uses imported exception names; SCS removed as an event
consumer in `04-api-user-management.md` and as a future client in
`guides/microservices-http-clients.md`.

Left after the case-insensitive sweep (`docs/architecture`, `docs/guides`, `docs/deployment`,
`docs/operations`, `README.md`, `CLAUDE.md`), none of them a runtime claim:
- ADR-0xx files: decision history.
- `02-infrastructure-deployment.md:7-8`: dated live check (2026-11-04) with the 17.2 note.
- `01-system-overview.md` planned-2024 diagram nodes and the 2026-11-04 change-log row: history.
- `04-api-speaker-coordination.md`, `04-api-attendee-experience.md` and links to them: domain API
  narratives (file names); the attendee sequence diagram documents the never-built search and sits
  under the "Not implemented, not routed" note's document.
- `09-aiml-architecture.md`: the "Historical vision (2025, not implemented)" part.
- "speaker coordination" / "Attendee Experience" as activity or epic names (`06a`, `05`, `06`
  index line, `epic-6-speaker-onboarding-plan.md`, README epic tables, `CLAUDE.md` Epic 7 lines).
- `03-data-architecture.md`, `01-system-overview.md:536`: the kept Flyway history table name.
- Not fixed, pre-existing: the `start-all-native.sh` header still lists ports 8080-8085 / frontend
  3000 for instance 1 (actual 8000-8004 / 8100), and `status-all-instances.sh` prints the same
  wrong endpoints.

**Verification run** (logs in `~/.cache/batbern-test-logs/2a-*.log`):
- `:api-gateway:test`: red first with the new `should_notRoute_when_contentEndpointCalled`
  (1 of 39 failed), then BUILD SUCCESSFUL, 328 tests, 0 failures.
- `projects build -x test`: BUILD SUCCESSFUL; hierarchy shows CUMS, EMS, PCS only.
- `infrastructure` jest: 35 suites passed (2 skipped), 499 tests passed.
- `cdk synth --context environment=staging` before (git archive of the baseline) and after:
  SpeakerCoordination and AttendeeExperience templates gone; semantic template diff of the other
  stacks shows only the two gateway env vars, the two ECR repositories and their two outputs, plus
  the GitHub-issues Lambda code hash (its source changed). The Docker image and edge-Lambda asset
  hashes also differ, because the baseline was synthesized from a different directory (the Docker
  context is the repository root); a second synth of the branch was byte-identical to the first.
- `check:test-ratchet`: exit 0; the baseline named no removed module, no `--update`.
- `bash -n` on every edited shell script, YAML parse of the edited workflows and compose,
  `check-skip-propagation.py` on the four edited workflows: clean.
- `make dev-native-up-instance BASE_PORT=9000`: gateway, CUMS, EMS, PCS and the frontend started
  healthy; with an organizer token `/api/v1/content/search` returned 404 and `/api/v1/events` 200;
  instance stopped afterwards.

Not run: the EMS, CUMS and PCS test suites (their only edits are a Javadoc line in CUMS), Bruno and
Playwright (no API or UI change besides the removed unrouted prefix).

## Plan Change Log

## Review Triage Log

Pass 1 (quick lens), 2026-11-07: high 0, medium 4, low 5, false 0, maybe-false 0. All patched; no loopback.

| # | Finding | Verdict | Route | Evidence / action |
|---|---|---|---|---|
| 1 | Mermaid edges to deleted TASK_SC/TASK_AE in 02 | medium | patch | Phantom nodes confirmed; edges removed. |
| 2 | 01 runtime diagram, sizes, routing still show SCS/AES | medium | patch | Removed. |
| 3 | 01 claims ECR repos removed | medium | patch | Live CICD template: all repos `DeletionPolicy: Retain`; text now says retained until owner deletes. |
| 4 | 06 lists SCS among exception handlers | low | patch | Fixed. |
| 5 | local-development-setup and README list six services/old ports | medium | patch | Four processes and real ports from the scripts. |
| 6 | native-execution examples call `stop-all-native.sh <service>` (invalid) | low | patch | Replaced with `DEV_SERVICES=... make dev-native-up`. |
| 7 | 08 SLA targets for removed services | low | patch | Removed. |
| 8 | FQN `RouteNotFoundException` in DomainRouterTest | low | patch | Import added; 39 tests pass. |
| 9 | AC1 grep too literal (SCS/AES, spaced names) | low | patch | Case-insensitive sweep done; deliberate leftovers listed in Implementation Notes. |

Coordinator: fixed the pre-existing wrong endpoint ports in `status-all-instances.sh` and the `start-all-native.sh` header (checked against the port variables).

## Production verification

2026-11-10, owner go: orphaned stacks `BATbern-staging-SpeakerCoordination`, `-AttendeeExperience` and `-EventBus` deleted (no stack imported their exports; the bus had no rules or archives), and the two retained stub ECR repositories deleted. Checked live afterwards: `aws ecs list-services` lists the gateway, EMS, CUMS and PCS only; `aws events list-event-buses` lists `default` only; no leftover log group, alarm or Cloud Map service. The CDK guard `infrastructure/test/unit/no-event-bus.test.ts` now also fails on a stub stack or repository.
