---
title: 'Pre-deploy gate (P5): local spike on rack'
type: 'chore'
ticket: '5'
created: '2026-11-15'
status: 'done'
baseline_revision: '9e9ecdaab08190d76cc63d9691215bb86f82f0d4'
route: 'full'
route_source: 'auto'
risk: 'high'
review: ''
review_source: ''
lenses_ran: []
review_loop_iteration: 0
followup_review_recommended: false
context:
  - '{project-root}/_bmad-output/backlog/spike-pre-deploy-gate.md'
  - '{project-root}/docs/plans/pre-deploy-gate-plan.md'
  - '{project-root}/CLAUDE.md'
warnings: ['oversized']
deferred: []
---

<intent-contract>

## Intent

**Problem:** Bruno and Playwright are the only layers that run real code end to end, and they run after the deploy, against production. Mail-sending and destructive flows are therefore never tested end to end. Step 1 of `docs/plans/pre-deploy-gate-plan.md` must find out, on rack, whether the shipped images can run in the production profile in a sealed local stack, what hidden AWS dependencies block that, and what a run costs.

**Approach:** Build the four production images (amd64) on rack. Start them with `docker-compose.gate.yml` in the production profile (`SPRING_PROFILES_ACTIVE=staging`, as ECS sets it) next to Postgres, MinIO and a fake SES, overriding only external endpoints. Run with no AWS credentials, record every startup failure and add small, production-inert overrides. Run Bruno and one mail flow against it, measure, and write the result into the plan doc.

## Boundaries & Constraints

**Always:**
- Containers get NO AWS credentials: run compose under `env -u AWS_PROFILE -u AWS_ACCESS_KEY_ID -u AWS_SECRET_ACCESS_KEY -u AWS_SESSION_TOKEN`, mount no `~/.aws`, set no `AWS_*KEY*` in the compose file except dummy values scoped to MinIO/fake SES endpoints if a client insists on credentials (dummy strings like `gate`/`gate`, never real ones). Any AWS SDK call that is not endpoint-overridden must fail for lack of credentials, never reach account 188701360969.
- Fetching the public Cognito JWKS (`https://cognito-idp.eu-central-1.amazonaws.com/<pool>/.well-known/jwks.json`) to validate test tokens is allowed: it is anonymous and read-only.
- Bruno uses only existing test-user tokens from `~/.batbern/staging-{organizer,speaker,partner}.json` (refresh with `./scripts/auth/refresh-token.sh staging [role]` on the host if expired). Any flow needing a Cognito admin call: record it, do not work around it against production. Never run `scripts/dev/sync-users-from-cognito.sh`.
- Seed data: no PII, `@example.com` style (test-user rows may carry the test users' own addresses, which are test identities, taken from the decoded JWT claims). Never copy production data or the anonymized snapshot.
- Fake SES catches everything; prove it (a mail lands in the fake, nothing else) before running the mail flow.
- Every override is inert in production: activated only by a property/env var that ECS never sets (check `infrastructure/lib/stacks/*` and `infrastructure/lib/constructs/domain-service-construct.ts`), proven by a unit test or a config read recorded in the result.
- SES override in `AwsSesConfig` is test-first (RED then GREEN), in shared-kernel.
- Rack: ONE heavy job at a time (docker builds sequentially, Gradle, git commit hooks, Vitest, Playwright never in parallel). Gradle with `--max-workers=4`. Logs: `set -o pipefail; <cmd> 2>&1 | tee ~/.cache/batbern-test-logs/<name>.log`. Long commands in the FOREGROUND (timeout up to 600000 ms, chunk if needed), never background-and-end-turn. Build contexts, volumes and scratch under `~/.cache/batbern-gate/`, never `/tmp`. Container memory limits so the stack fits (rack has ~10 GB free).
- Stay on branch `docs/epic17-close`. Stage paths explicitly (never `git add -A`, never bare `git stash`). Conventional commits ending with the two trailer lines `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` and `Claude-Session: https://claude.ai/code/session_01BkGdx6q4jVDKkQkFEgqfcY`. No push, no PR.
- At the end: `docker compose -f docker-compose.gate.yml down -v`, remove gate images/volumes and dangling images. Nothing left running.

**Never:**
- No change under `.github/workflows/`; no GitHub job (plan step 2).
- No `SPRING_PROFILES_ACTIVE=local` for the gate (that is the profile with the divergences).
- No editing of applied Flyway migrations.
- No large refactor to make a service start. If an override is large, stop: write the finding and options into the result, that is a valid spike outcome.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| SES override set | `batbern.ses.endpoint` (or chosen key) = `http://fake-ses:port` | `SesClient` built with that endpoint override | No error expected |
| SES override unset (production) | property absent/blank | `SesClient` built exactly as today, no endpoint override | No error expected |
| No credentials, unoverridden AWS call | e.g. Cognito admin call in the gate | call fails with a credentials error, logged; nothing reaches AWS | recorded as dependency |

</intent-contract>

## Code Map

- `shared-kernel/src/main/java/ch/batbern/shared/config/AwsSesConfig.java` -- `SesClient` bean, `@Profile("!test & !local")`, `DefaultCredentialsProvider`; add optional endpoint override (+ static credentials only when the override is set, if the SDK needs them). Unit test in `shared-kernel/src/test/...`.
- `services/*/Dockerfile`, `api-gateway/Dockerfile` -- production Dockerfiles; build context is repo root (`docker buildx build --platform linux/amd64 -f <svc>/Dockerfile .`). EMS uses BuildKit cache mounts.
- ECS env (the "production profile" to reproduce): `infrastructure/lib/constructs/domain-service-construct.ts:82` (`SPRING_PROFILES_ACTIVE=envName`=`staging`, `DATABASE_URL/USERNAME/PASSWORD`, `COGNITO_USER_POOL_ID`, `COGNITO_CLIENT_ID`), `infrastructure/lib/stacks/event-management-stack.ts:108-175`, `company-management-stack.ts:61-99`, `partner-coordination-stack.ts:48`, `api-gateway-service-stack.ts:62,115-145` (service URLs, `TURNSTILE_ENABLED`, `FEATURES_SSO_ENABLED`, `SECURITY_ACTIVE_GATE_ENABLED`, `WATCH_JWT_SECRET`, `JWT_SECRET`). Leave SQS listener flags (`AWS_INBOUND_EMAIL_ENABLED`, `AWS_INBOUND_EMAIL_FORWARDING_ENABLED`, `AWS_BOUNCE_ENABLED`, `AWS_SES_DELIVERY_STATUS_ENABLED`) unset: the listeners are `@ConditionalOnProperty`-gated; record that as an override by omission. Pool id/client id: read from `~/.batbern/staging-organizer.json` token `iss`/`aud` claims, not from AWS.
- AWS clients built at startup: EMS `config/AwsConfig.java` (`S3Client`, `CloudFrontClient`, `S3Presigner`, `@Profile("!local")`, no endpoint override today), CUMS `config/AwsConfig.java` (`S3Client`, `S3Presigner`, CloudWatch gated), CUMS `config/CognitoConfig.java` (`@Profile("!test")`), gateway `config/CognitoConfig.java:60` (Cognito client always built). `LocalAwsConfig` in EMS/CUMS shows the MinIO override pattern (local profile only, do not reuse the profile).
- Schedulers that may call AWS: CUMS `service/CognitoConfirmationResendJob.java` (Cognito `ListUsers` + `resendConfirmationCode` on the production pool) -- must be proven to fail closed without credentials or be switched off by a gate-only property; EMS `@Scheduled` jobs (see `AutomaticTriggerAudienceGuardTest` list) run against seed data only.
- `scripts/dev/seed-test-data.sql` -- candidate seed; check for PII before use, else write `scripts/gate/seed-gate.sql`.
- Bruno: `bruno-tests/*`, `bruno-tests/environments/development.bru` (`baseUrl http://localhost:8000/api/v1`), runner `scripts/ci/run-bruno-tests.sh <env> [token] [--collection X]`. Gateway must listen on host port 8000 or add a `gate` env.
- Result target: `docs/plans/pre-deploy-gate-plan.md`, new section "Result of the spike" (replace the bullet list under "**Result of the spike:**" with the findings, keep the rest).

## Tasks & Acceptance

**Execution:**
- [x] `shared-kernel/src/test/java/ch/batbern/shared/config/AwsSesConfigTest.java` -- RED: endpoint set → client endpoint override equals it; unset/blank → no override -- test first.
- [x] `shared-kernel/src/main/java/ch/batbern/shared/config/AwsSesConfig.java` -- GREEN: optional endpoint property, inert when blank -- gate needs the real `SesClient` path.
- [x] Images -- build gateway, CUMS, EMS, PCS sequentially with production Dockerfiles, `--platform linux/amd64`, tags `batbern-gate/<svc>:spike`; time each build -- shipped artifact.
- [x] `docker-compose.gate.yml` (repo root) + `scripts/gate/` helpers (seed, fake SES choice, run script) -- Postgres 15, MinIO (+ bucket init), fake SES (LocalStack SES or a small SES-API mock whose received mails can be listed over HTTP), four services in profile `staging`, only endpoints overridden -- the gate stack.
- [x] Startup overrides -- start with no credentials, log each failure (service, exception, AWS service), add the smallest gate-only override (e.g. S3 endpoint override property for MinIO in EMS/CUMS `AwsConfig`, scheduler off-switch property), each with a test or config read proving ECS never sets it.
- [x] Bruno + mail -- seed test users from token claims, run every collection against the gate gateway (one `--collection` at a time, logs per collection), run one mail-sending flow and read it back from the fake SES; measure wall time from `compose up` to last result.
- [x] `docs/plans/pre-deploy-gate-plan.md` -- write "Result of the spike": dependency table (dependency, symptom, override, proof it is inert in prod), timings (build per image, startup, Bruno, total), Bruno result per collection (pass/fail counts, cause of failures), mail check, recommendation with per-PR cost labelled as an estimate, and what was not verified.
- [x] Run the full test suite of each touched service once at the end (shared-kernel change → `./gradlew :shared-kernel:test --max-workers=4`, plus each touched service suite, sequentially), `make check-test-ratchet`; commit in logical commits.

**Acceptance Criteria:**
- Given the gate stack started with no AWS credentials, when it is up, then all four services report healthy on `/actuator/health`, or the result names the one that cannot start, why, and the options.
- Given the fake SES, when a mail-sending flow runs through the gateway, then the fake lists the mail with its To:, subject and body, and no SES call left rack (no credentials present; the SES client endpoint points at the fake).
- Given the Bruno collections run against the gate gateway, then the result lists pass/fail per collection with the cause of each failure class.
- Given the production ECS environment, when the overrides' properties are compared with what CDK sets, then none of them is set (test or recorded config read).
- Given the end of the spike, then `docker ps -a` and `docker volume ls` show no gate containers or volumes.

## Implementation Notes

- Result written to `docs/plans/pre-deploy-gate-plan.md` → "Result of the spike". Run artefacts in
  `~/.cache/batbern-gate/run-*` (not committed).
- Scripts: `scripts/gate/build-images.sh`, `run-gate.sh`, `seed.sh`, `run-bruno.sh`,
  `bruno-failures.sh`, `lib.sh`, `fake-aws/fake_aws.py`. Bruno environment `bruno-tests/environments/gate.bru`;
  `scripts/ci/run-bruno-tests.sh` treats `gate` like development (dev-only collections run).

## Plan Change Log

- Fake SES became a fake AWS (SES + in-memory Cognito admin stand-in): Pattern N `adminCreateUser`
  failed with a credentials error and broke 10 speaker requests. Override `batbern.cognito.endpoint`
  in CUMS `CognitoConfig`, test first (`CognitoConfigTest`). Small, so not a stop condition.
- MinIO images are no longer pullable; Adobe S3Mock 4.7.0 instead, published on 172.17.0.1.
- S3 override as one shared-kernel record `S3EndpointOverride` (tested) used by EMS and CUMS `AwsConfig`.
- Ordered service start (CUMS → EMS → PCS): Flyway race on an empty database.
- Seed through the API plus one SQL step for the three test profiles (JIT names users after the e-mail).
- Test fix found by the gate: `users-api/03b` bio null vs "".

## Review Triage Log

## Design Notes

Architecture note: modules touched: shared-kernel config (`AwsSesConfig`), possibly EMS/CUMS/gateway `config` for gate-only endpoint overrides; new deps: none (fake SES and MinIO are containers); new endpoints: none; reused: `LocalAwsConfig` MinIO pattern, `SesRecorder`-style parsing idea for reading the fake; deviation: none.

Property naming: prefer one prefix for gate-only keys (e.g. `batbern.ses.endpoint`, `aws.s3.endpoint`), blank default, and wire via env vars in the compose file. Production inertness is then "CDK never sets `BATBERN_SES_ENDPOINT`" (grep infrastructure) plus a unit test of the blank case.

## Verification

**Commands:**
- `set -o pipefail; ./gradlew :shared-kernel:test --max-workers=4 2>&1 | tee ~/.cache/batbern-test-logs/gate-sk-test.log` -- expected: BUILD SUCCESSFUL
- the touched services' `:services:<svc>:test --max-workers=4` (one at a time) -- expected: BUILD SUCCESSFUL
- `make check-test-ratchet` -- expected: no RAISED, no STALE
- `grep -rn "<each gate-only env var>" infrastructure/lib` -- expected: no match
- `docker ps -a --filter name=gate; docker volume ls | grep -i gate` -- expected: empty at the end
