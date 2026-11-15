# Pre-deploy gate: real code before production (P5)

Winston, 2026-11-07. Plan, parked by owner decision. Origin: proposal P5 in
[`test-fidelity-proposal-2026-11.md`](test-fidelity-proposal-2026-11.md).

**When:** after stories 17.1–17.5 and 17.0a–17.0d are implemented (owner, 2026-11-07). Then the
local spike (below) comes first. The GitHub job is built only if the spike succeeds.

## Problem

The only layers that run real code end to end, Bruno and Playwright, run in `deploy-staging.yml`
**after** the deploy to production (staging account = production). Consequences:

- They may not send mail or run destructive flows. #1208 skipped a Bruno test for that reason.
- They have damaged real data: #1069, where a PUT replay nulled the evening start time, and 6,365
  orphan notifications.
- Every check before production runs against mocks or the `local` profile.

## Target picture

```
today:   build-services ──push──> ECR ──> deploy-to-staging (ECS = prod) ──> Bruno/Playwright against prod

with P5: build-services ──push──> ECR ──> pre-deploy-gate ──green──> deploy-to-staging ──> (as today)
                                               │
                                               └─ red: no deploy, PR shows the failure
```

`pre-deploy-gate` is a new job in `build.yml` on a throwaway GitHub ARM runner. **It touches no AWS
environment.**

1. It pulls the images that `build-services` just built (`batbern/staging/<service>:<version>-staging.<run>`,
   `linux/arm64`, production `Dockerfile`). These are the same bytes that go to ECS minutes later.
   Services: gateway, CUMS, EMS, PCS. SCS and AES are gone after 17.2.
2. It starts them with `docker compose` (`docker-compose.gate.yml`) in the **production profile**.
   Only the addresses of external systems are overridden, pointing at three helper containers:
   - an empty Postgres, where Flyway runs the real migrations and a seed script adds test data;
   - MinIO in place of S3;
   - a fake SES that keeps every mail.
3. It runs Bruno against the local gateway. Calls between services go over HTTP on the runner's Docker
   network. They are not mocked.
4. Green lets `deploy-to-staging` run. Red blocks the deploy. The runner is discarded afterwards.

| | Status in the gate |
|---|---|
| Shipped images, production profile, security chain, cache, Flyway, calls between services, mail rendering up to sending | real |
| Cognito JWT validation (tokens of the existing CI test users) | real, external |
| Database (empty + seed, no production data), S3, SES delivery | replaced |
| Cognito admin calls (user creation) | must be switched off or replaced (see risks) |

What that gains: Bruno can run mail-sending and destructive flows and check what would have been sent
(#1176 partner invite, #1181 reminder audience, #1208 waitlist promotion). It leaves no residue in real
data. Breaking moves such as 17.6 (`/internal`) fail before production.

## Why not `docker-compose.yml` or `make dev-native-up`

Checked in the files on 2026-11-07.

- **Both run `SPRING_PROFILES_ACTIVE=local`.** That is where the environment differences behind 13 of
  the 57 analysed bugs live: `NoOpCacheManager`, no `SesClient`.
- **`docker-compose.yml` does not run the shipped artifact.** It builds `Dockerfile.dev`, runs
  `gradlew bootRun` on the mounted source, and has no Postgres service. It is the older form of the
  developer stack and has had two changes since May.
- **The native stack** (fat JARs, local Postgres, MinIO) replaced compose because it uses 60–70 %
  fewer resources. It stays the developer tool.

For a gate the choice is not Docker versus native. It is which artifact and which profile: the
production image with the production profile.

## Finding: local mail capture is a bypass, not a fake SES

Checked in the code on 2026-11-07.

- **No `SesClient` locally.** In `local` and `test` there is no `SesClient` bean:
  `AwsSesConfig.java:39`, `@Profile("!test & !local")`.
- **The send path is skipped.** `EmailService` takes the `sesClient == null` branch
  (`EmailService.java:240` and four more send methods) and hands the mail to `LocalEmailCapture`. That
  skips address validation, MIME assembly and `assertNotTestMarked`.
- **That is where #1176 failed.** A comma-joined To: line was rejected only on the real path.

**Needed for the gate:** an SES endpoint override in `AwsSesConfig`, as `EventBridgeConfig.java:66`
already has, so the real `SesClient` talks to a fake SES container (LocalStack or a small SES mock).
Optionally, the developer stack could use the same fake later instead of the bypass. That would also
remove the `sesClient == null` branch, which 17.0b's rule ("no validation behind
`sesClient == null`") targets.

## Step 1: local spike on rack (before any GitHub work)

The goal is to answer the open questions at minimal cost. It needs a feature branch, deploys nothing,
and is a heavy build: nothing else heavy runs on rack in parallel.

1. **Build the images.** Build gateway, CUMS, EMS and PCS with the **production** Dockerfiles, for
   `linux/amd64`. Rack is x86 and CI builds arm64: the recipe is the same, only the CPU differs.
   Emulating arm64 would be slow and tests nothing extra.
2. **Compose file.** Write `docker-compose.gate.yml` with Postgres, MinIO, a fake SES and the four
   images in the production profile.
3. **SES override.** Add the SES endpoint override in `AwsSesConfig`, test first.
4. **Start without AWS credentials.** Whatever fails at startup is the list of hidden AWS dependencies
   (SQS listeners, Secrets Manager, EventBridge if 17.2 has not removed it, schedulers that fire during
   the run). **Report to the owner after this step.**
5. **Run Bruno.** Run the Bruno collections against the local gateway, plus at least one mail-sending
   flow checked against the fake SES. Measure the time from start to result.

**Result of the spike** (run on rack, 2026-11-15, branch `docs/epic17-close`). Everything below was
measured unless it says "estimate" or "not verified".

How to repeat it: `scripts/gate/build-images.sh` (the four images, one at a time), then
`scripts/gate/run-gate.sh` (clean `up`, health, seed, all Bruno collections, mail check, `down -v`).
`run-gate.sh` exits non-zero unless every Bruno collection passed, the fake AWS received at least one
`SendRawEmail`, and no service log holds an AWS credentials error (`SdkClientException`,
`Unable to load credentials`); with today's 23 known failures (below) it therefore exits 1.
Stack: `docker-compose.gate.yml`. Results land in `~/.cache/batbern-gate/run-<timestamp>/`.

*Outcome in one line:* the shipped images start in the production profile with no AWS credentials and
no override at all; 721 of 744 Bruno tests pass, and the 23 that fail fall in two classes that are
test assumptions about production, not code defects.

*Startup without credentials.* First boot with the production environment only (DB and Cognito ids,
secrets as ECS injects them, no endpoint override): gateway, CUMS, EMS and PCS all healthy on
`/actuator/health`, no AWS call at startup, no credential error in the logs. Every AWS client is built
lazily; none is called while the context starts. The SQS listeners are off because CDK's
`AWS_*_ENABLED` flags are not set (they are `@ConditionalOnProperty`).

*Dependencies found, at runtime not at startup:*

| Dependency | Symptom without override | Gate override | Proof it is inert in production |
|---|---|---|---|
| SES (every mail) | `SdkClientException: Unable to load credentials` on send | `BATBERN_SES_ENDPOINT` → fake AWS container (`batbern.ses.endpoint`, `AwsSesConfig`), placeholder credentials only on that client | `AwsSesConfigTest` (blank = production client); `grep` of `infrastructure/lib` and `.github/workflows`: 0 hits |
| Cognito admin API in CUMS (Pattern N: `adminCreateUser` when a speaker is promoted, `adminGetUser`, `adminSetUserPassword`) | `SdkClientException` → HTTP 500 on `POST /internal/users/provision`; 10 speaker requests failed in the first run | `BATBERN_COGNITO_ENDPOINT` → in-memory stand-in pool in the fake AWS container (`batbern.cognito.endpoint`, CUMS `CognitoConfig`) | `CognitoConfigTest`; grep: 0 hits |
| S3 (uploads, presigned PUT, media) | presign/upload fails for lack of credentials | `AWS_S3_ENDPOINT` + `AWS_S3_ENDPOINT_ACCESS_KEY/SECRET_KEY` → S3Mock (`S3EndpointOverride` in shared-kernel, used by the EMS and CUMS `AwsConfig`) | `S3EndpointOverrideTest`; grep: 0 hits |
| CUMS daily Cognito re-send job (08:15) and reconciliation (02:00) | without the Cognito override both would call the production pool and fail closed. In the gate CUMS has one Cognito client, pointed at the stand-in, so both call the fake | re-send: `COGNITO_RESEND_ENABLED=false` (existing property). Reconciliation stays on and calls the stand-in: the seed registers the three test users in the stand-in pool under their real `sub`, so `reconcileOrphanedDbUsers` keeps them active. Residual: a user created in the database without going through the stand-in (not the case for any seeded or Bruno-created user observed) would be deactivated if a stack stays up across 02:00; a normal run lasts minutes | re-send: existing default `true`, CDK does not set it |
| SQS listeners (inbound mail, forwarding, bounces, delivery status) | none | override by omission: CDK's `AWS_*_ENABLED` flags not set | `@ConditionalOnProperty`, default `false` |
| CloudFront invalidation (EMS) | none: `aws.cloudfront.enabled` defaults to `false` and is never set | none needed | see side finding 1 |
| Gateway Cognito client (password reset) | not exercised by Bruno; would fail closed | none | not verified |
| Turnstile | none: Bruno sends no token, the filter fails open | Cloudflare's public always-pass test keys, `TURNSTILE_ENABLED=true` as in production | — |
| Cognito JWKS | read anonymously, as allowed | none | — |

No credential error in the final run (`aws-credential-failures.log` empty). The containers had no AWS
variables and no `~/.aws` mount; compose ran under `env -u AWS_PROFILE -u AWS_ACCESS_KEY_ID ...`.

*Two gate-only adjustments that are not overrides:*
- **Ordered startup.** On an empty database the three Flyway runs raced once in three starts (EMS
  `V5__Create_logos_table`: `duplicate key value violates unique constraint "pg_type_typname_nsp_index"`).
  The compose file starts CUMS, then EMS, then PCS. Costs about 25 s; production never migrates an
  empty database in parallel.
- **MinIO is gone.** `minio/minio` and `quay.io/minio/minio` refuse the pull (checked 2026-11-15), so
  Adobe S3Mock 4.7.0 stands in. It does not check signatures. It is published on the docker0
  address (`172.17.0.1:19090`) so a presigned URL works from the host and from the containers alike;
  a CI runner needs the same trick or Bruno inside the compose network.

*Timings* (rack, 16 cores, warm Docker layer cache, Gradle caches cold inside the build stage):

| Step | Measured |
|---|---|
| Image build, sequential: gateway / CUMS / EMS / PCS | 70 s / 62 s / 77 s / 62 s (271 s) |
| `up` to all healthy (clean DB, ordered start, 148+31+12 migrations) | 58–66 s over three runs |
| Seed (3 profiles, partner company, event 59, one participant) | 2 s |
| Bruno, 23 collections one at a time | 127–131 s |
| `up` to last result | 187–198 s |
| `down -v` | 14 s |

On a GitHub ARM runner (2–4 vCPU instead of 16 cores) the run would take longer; the images already
exist there. **Estimate, not measured:** 6–10 minutes per PR for the gate job, against the plan's
15–25.

*Bruno result, final run* (407 requests: 380 passed, 23 failed, 4 skipped; 721/744 tests):

| Collection | Tests | Cause of failures |
|---|---|---|
| admin-cleanup-api, tasks-api, event-types-api, event-topics-api, events-crud-api, events, sessions-api, speaker-pool-api, event-full-workflow-api, speaker-portal-api, event-publishing-api, participants-api, newsletter-api, notifications-api, ai-prompts-api, session-import-api, company-get-or-create-api, event-media-api | all pass | — |
| companies-api | 35/40 | 5× "should have CORS headers" |
| partners-api | 55/70 | 15× "should have CORS headers" |
| partner-meetings-api | 25/26 | 1× "has CORS headers" |
| file-upload-api | 4/5 | the test accepts only `amazonaws.com` or `localhost:8450` as upload host |
| users-api | 104/105 | same upload-host assertion (profile picture) |

The two failure classes:
1. **CORS lives at the AWS API Gateway edge** (ADR-008); the Spring gateway adds CORS only in the
   `local`/`dev` profiles. The gate has no edge, so 21 checks cannot pass. Options: skip the CORS
   checks when `env = gate` (the edge is covered by CDK tests and the post-deploy smoke), or put a
   small proxy in front that copies the edge's `corsPreflight`, which would test the proxy, not the
   edge. Recommended: the skip.
2. **Upload host hard-coded in two tests.** Make the accepted host an environment variable.

Speaker-portal-api passed in the gate although it is documented as needing the dev-only E2E token
helper; not investigated.

*What the gate found on its first runs* (beyond the dependencies):
- `users-api/03b-restore-current-user.bru` compared a missing bio (`""` in the test, `null` from the
  service). Hidden in production because the organizer account has a bio. Fixed in the test.
- The JIT path names a new user after the e-mail's local part (`success+batbern.speaker@...` became
  `user.success`), so the seed writes the three profiles from the tokens' `custom:username`.
- Seven mails to `@e2e.batbern.invalid` fixtures were refused by the real send path
  (`ReservedEmailRecipientException`), as designed. The Bruno requests still passed, because sending is
  asynchronous. So the gate sees only mails to non-reserved addresses: a collection that wants to check
  mail content must use an SES simulator address.

*Mail check.* Before Bruno, an additional e-mail added through the gateway produced one `SendEmail`
in the fake (To `success+gate-proof@simulator.amazonses.com`, subject "Bestätigen Sie Ihre BATbern
E-Mail-Adresse", HTML body, configuration set, Host header `fake-aws:8025`). The real path also
refused `@example.com` (reserved domain) before that. In the final run the fake listed two
`SendRawEmail` registration confirmations with To, subject and HTML body (seed: the speaker test user
on event 59; Bruno: `participants-api/04` to a `@batbern-test.ch` address). Nothing can have gone to
AWS: no credentials in the containers, and the SES client's endpoint is the fake.
That final run still cleared the fake right after the seed, so the seed's (asynchronous) mail being
in the list was timing, not design. `run-gate.sh` now clears the fake before the seed, so the list
deterministically holds the seed's mail and Bruno's, and it fails the run when no `SendRawEmail`
arrived. This version of the script has not been run against the stack.

*Recommendation.* Build the GitHub job (step 2), as non-required first: the stack starts, the
dependency list is short and every override is a blank-default property. Make it required after it
has been green for about two weeks and the two failure classes are fixed. Before that, decide the
CORS option above and whether Bruno runs inside the compose network on the runner (removes the
docker0 trick).

*Not verified:* the job on a GitHub ARM runner and its real time; arm64 images (rack built amd64);
the gateway's Cognito client (password reset); the Cognito stand-in's fidelity beyond the operations
Bruno reached (`AdminCreateUser`, `AdminGetUser`, `AdminSetUserPassword`); schedulers firing during a
run (crons are daily, except Q&A every 5 min and Q&A freeze hourly, which ran against seed data
without errors in the logs that were checked); memory use of the stack (limits set, peak not
measured); Playwright against the gate.

*Side findings, not changed (owner decision):*
1. `CdnInvalidationService` reads `aws.cloudfront.enabled` (default `false`) and
   `aws.cloudfront.distribution-id`. CDK sets `CLOUDFRONT_DISTRIBUTION_ID`, which binds to neither, and
   never sets the enabled flag. So production never invalidates the CDN on publish. Intended or not
   is the owner's call.
2. `CognitoIntegrationServiceImpl` catches `CognitoIdentityProviderException` only; a client-side
   `SdkClientException` (credentials, network) becomes an unmapped HTTP 500 instead of the mapped 502.

## Step 2: GitHub job (only after a successful spike)

- **New job.** Add a `pre-deploy-gate` job in `build.yml`, with `needs: build-services`. Add it to
  `deploy-to-staging`'s `needs`.
- **Images.** Pull the arm64 images from ECR, or use those already loaded on the runner if the job
  layout allows it.
- **Required check.** Decide with the owner whether it becomes a required check on `develop`. A
  required check that never runs blocks a PR (see `project-context.md`).
- **Bruno split.** Divide Bruno into collections that run only in the gate (mail, destructive) and
  collections that also run post-deploy against production (read-only smoke).

## Risks and open questions

- **Cognito.** With the production profile, CUMS would make admin calls (user creation, as in
  Pattern N in CLAUDE.md) against the **production user pool**. The containers therefore get no AWS
  credentials. Flows that need Cognito admin calls are switched off in the gate or replaced by a
  stand-in. Bruno uses only tokens of existing test users. To be decided in step 1, not discovered.
- **Schedulers and listeners** in the production profile may fire during the run. They must be
  harmless against the seed data (the gate DB holds no real people) or be switched off.
- **Time per PR.** About 15–25 minutes is an estimate, not measured. Step 1 measures it.
- **Seed data.** Today's `scripts/dev/seed-test-data.sql` or a dedicated gate seed. It must contain no
  PII (CLAUDE.md data rules).

## Dependencies

- **17.2** (removes SCS/AES and EventBridge): fewer services and fewer startup dependencies.
- **17.0b** (real `EmailService` against a mocked `SesClient`, no validation behind
  `sesClient == null`): prepares the SES override.
- **17.0a–d in general:** with the harnesses in place, a red gate is rare and cheap to fix, so the gate
  stays green without heavy upkeep.
