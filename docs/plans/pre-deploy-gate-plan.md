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

**Result of the spike:**
- the list of startup dependencies with the proposed override for each;
- the measured time;
- the Bruno result;
- a recommendation on whether to build the GitHub job.

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
