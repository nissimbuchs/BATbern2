# CI/CD Pipeline Guide

## Overview

The BATbern platform uses GitHub Actions for continuous integration and deployment. The pipeline automates building, testing, security scanning, and deploying all microservices and frontend applications to production (single AWS account 188701360969).

## Pipeline Architecture

```
PR to develop (or push to develop)
  └─ build.yml: verify-hooks, build-shared-kernel, build-services (matrix), frontend jobs,
     test-ratchet, build-infrastructure
       └─ deploy-to-staging → deploy-staging.yml (production, account 188701360969)
            ├─ deploy (CDK / fast path), then post-deploy-checks (smoke, CORS, access control)
            ├─ bruno-tests, playwright-tests (@smoke)
            └─ all green: tag staging-stable; any red: rollback-on-test-failure
  └─ claude-code-review.yml: Claude review and the Doc Drift Check
  └─ auto-merge.yml: merges when the required checks and the deploy succeeded
```

Every workflow is in `.github/workflows/`; the YAML is the source of truth. Other workflows:

| Workflow | Trigger | What it does |
|---|---|---|
| `security-scan.yml` | weekly (Monday 02:42 UTC), manual | OWASP ZAP scan of the live site |
| `nightly-e2e.yml` | nightly 01:17 UTC, manual | Full Playwright `@gate` suite, infrastructure Layer 4 E2E |
| `ai-security.yml` | PR and push to develop, weekly | OWASP LLM checks for the AI code, advisory |
| `api-contract-validation.yml` | PR and push | OpenAPI contract validation |
| `sync-versions.yml` | PR and push | `node scripts/update-versions.js --check` |
| `doc-drift-auditor.yml` | weekly (Monday 08:27 UTC), manual | Weekly doc drift audit |
| `dependabot-batch-merge.yml` | monthly (first Monday) | Builds the Dependabot batch branch, see CLAUDE.md |
| `claude.yml` | issue and PR comments | `@claude` on issues and PRs |
| `deploy-production.yml`, `promote-to-production.yml` | manual | Deprecated, point to the decommissioned account, fail at the first AWS step |

Not in use: Snyk, SonarQube/SonarCloud (`sonarcloud` job in `build.yml` has `if: false`), license
checks, K6. The `*.yml.disabled` files are not run.

## Workflows

### 1. Build Pipeline (`build.yml`)

**Triggers:**
- Push to `develop` or `main`
- Pull requests to `develop` (this deploys production, see section 3)
- Manual dispatch

**Jobs:**
1. **Build Shared Kernel**
   - Builds shared domain types and utilities
   - Runs its tests; the JaCoCo gate (0.60 instruction coverage per module) runs only in
     `make test`, not in CI
   - Publishes to local Maven repository
   - Duration: ~2-3 minutes

2. **Build Services (Parallel Matrix)**
   - Builds all microservices in parallel, on a native arm64 runner (`ubuntu-24.04-arm`)
   - Creates `linux/arm64` Docker images from each service's `Dockerfile` (`docker buildx`),
     matching ECS Fargate ARM64. The Gradle build stage uses `--platform=$BUILDPLATFORM`, so a
     build on an x86 machine compiles natively too and only the runtime stage targets arm64
   - Pushes images to AWS ECR (on push and pull-request events; not on `workflow_dispatch`)
   - Services: event-management, partner-coordination, company-management, api-gateway
   - Until 2026-10-07 this job ran on x86 `ubuntu-latest`, and the whole Docker build ran under
     QEMU: 20-29 minutes per changed service for the image step alone (PR #1072)

3. **Build Frontend** (four jobs since #1074, plus `test-ratchet` since Story 17.0a)
   - `frontend-changes`: skips everything below when nothing under `web-frontend/` or `docs/api/`
     changed (a spec-only PR runs the frontend jobs since Story 17.3, so the path check sees it)
   - `test-frontend (1/3..3/3)`: Vitest in 3 shards in parallel, each writes a blob report with
     coverage; the coverage threshold is off per shard (a shard alone covers ~30%)
   - `frontend-static`: frontend API path check (Story 17.3: its Vitest files, then
     `npm run check:api-paths`), API types up to date, ESLint, Prettier, Vite production build,
     `frontend-dist`. The path check covers every frontend call through the API client, axios or
     a `*Client` (not `fetch`). It fails on a call no spec in `docs/api/` defines (NOT IN SPEC),
     an unresolvable call not in `web-frontend/scripts/api-paths-allowlist.txt` (UNRESOLVED), a
     new file outside `src/services/` with an HTTP call (OUT OF LAYER, baseline
     `web-frontend/scripts/api-layer-baseline.txt`) and a stale entry in either list. Locally:
     `make check-frontend-api-paths`. Then the generated type copy check (Story 17.3c: its Vitest
     file, then `npm run check:api-type-copies`): an `export interface` or object-literal
     `export type` in `web-frontend/src` named like a schema in `src/types/generated/` fails
     (NEW), as does a baselined copy that is gone (STALE); baseline
     `web-frontend/scripts/api-type-copies-baseline.txt` (`--update` only removes lines, adding
     one needs `--allow-growth`), every line owned in
     `scripts/api-type-copies-owners.txt`. Policy: `docs/architecture/coding-standards.md`,
     "Frontend: generated API type copies". Locally: `make check-api-type-copies`. Then the
     OpenAPI convention lint (Story 17.4: its Vitest
     files, then `npm run lint:api`): Spectral with `docs/api/.spectral.yaml` (five rules:
     kebab-case segments, no verb as last segment, no `/internal` path, no literal directly under
     `/events/`, UUID path parameters) over the public specs, compared with
     `web-frontend/scripts/api-lint-baseline.txt` by rule + spec file + method + path. It fails on
     a NEW finding and on a STALE one (fixed but still baselined: `npm run lint:api -- --update`).
     Policy: `docs/architecture/coding-standards.md`, "API conventions lint". Locally:
     `make lint-api`
   - `api-similarity` (Story 17.4, pull requests that change `docs/api/` only, not needed by any
     job, `continue-on-error`): for every operation the PR adds (method + path, parameter names
     blanked), the 3 most similar existing operations by path and summary, posted as one PR
     comment marked `<!-- api-similarity-report -->` and updated in place on later pushes.
     Needs `pull-requests: write`; a fork or Dependabot PR gets a read-only token and the job
     fails harmlessly. Locally: `cd web-frontend && npm run report:api-similarity -- --base origin/develop`
   - `test-ratchet` (Story 17.0a, no path filter: it also runs on PRs that touch only Java):
     its own Vitest file, then `npm run check:test-ratchet`. It counts, per test file, the mocks
     the Test fidelity rules forbid in new tests (`docs/architecture/coding-standards.md`, "Test
     fidelity rules"): `vi.mock` of our own services, hooks and components, `@MockitoBean` of
     in-process beans and of service-to-service clients, and class-level `@Transactional` on ITs.
     It compares them with `web-frontend/scripts/test-ratchet-baseline.txt` and fails on any
     difference: RAISED (a count went up, or a new file has a counted mock) and STALE (a count
     went down or the file is gone, but the baseline was not updated). After a STALE, lock the
     gain in: `cd web-frontend && npm run check:test-ratchet -- --update` and commit the baseline
     in the same PR. Never use `--update` to accept a rise, except when the RAISED lines are
     exactly a renamed or moved file carrying the same counts as its STALE lines (a `git mv`):
     then `--update` is fine and the PR says so. Locally: `make check-test-ratchet`
   - `build-frontend` (the required check, name unchanged): waits for the shards,
     `frontend-static` and `test-ratchet`, fails if any failed, merges the blob reports and
     enforces the coverage threshold on the merged result
   - Before #1074 one job ran it all, and the test step alone took 9-12 minutes (5,600+ tests on a
     4-vCPU runner). Coverage is not the cost: 274 s without, 312 s with it, measured locally.
   - Skipped shards would skip `deploy-to-staging` too: a job without a status function gets an
     implicit `success()` over ALL ancestors. That happened to #1201 (auto-merged without a deploy,
     2026-11-03), so the deploy's `if:` starts with `!cancelled()` and checks each direct need's
     result. `scripts/ci/check-skip-propagation.py` (in `verify-hooks`) fails for any job that a
     skipped ancestor would skip silently. `auto-merge.yml` also refuses to merge a PR whose
     `deploy-to-staging / deploy-to-staging` check did not succeed.

Integration tests (Testcontainers PostgreSQL) run inside each module's Gradle build in
`build-services`; there is no separate integration job.

### 2. Security Scanning (`security-scan.yml`)

OWASP ZAP against the live site, weekly and on demand. After every deploy, `post-deploy-checks`
in `deploy-staging.yml` also runs the anonymous access-control probe
(`scripts/ci/access-control-public.txt` lists what may answer anonymously).

### 3. Production Deployment (`deploy-staging.yml`)

> **Note:** The `deploy-staging.yml` workflow deploys to the production environment (staging account 188701360969 serves production traffic at www.batbern.ch).

**Triggers — read this carefully, it is not what most people assume:**

`deploy-staging.yml` has no `push`/`pull_request` triggers of its own. It is `workflow_call`
only, invoked by the `deploy-to-staging` job in `build.yml`, whose condition is:

```yaml
# .github/workflows/build.yml
if: (github.event_name == 'push'         && github.ref == 'refs/heads/develop') ||
    (github.event_name == 'pull_request' && github.event.pull_request.base.ref == 'develop' &&
     github.actor != 'dependabot[bot]')
```

| Event | Deploys to production? |
|---|---|
| Push/merge to `develop` | **Yes** |
| **Open or update a PR targeting `develop`** | **YES — the PR branch's code, unmerged and unreviewed** |
| PR targeting `develop` opened by `dependabot[bot]` | No — build and test only |
| Push to `main` | No (build only; `main` is not the production branch) |
| Manual `workflow_dispatch` | Yes |

**A pull request deploys to production.** Opening a PR against `develop` ships that branch to
www.batbern.ch before anyone reviews or merges it. There is no `environment:` protection gate on
the deploy job — the only safety mechanisms are `concurrency: deploy-staging` (which serialises
deploys, it does not gate them), the pre-deploy RDS snapshot when migrations are detected, and
the post-deploy E2E suite with rollback.

Practical consequences:
- Treat opening a PR as a production action. Draft PRs still fire it.
- Merging N queued PRs means N sequential production deploys.
- The `dependabot[bot]` exclusion is why dependency PRs are safe to accumulate — they never
  deploy — but merging them does, once each. That is why they are not merged individually: the
  monthly batch (`dependabot-batch-merge.yml`, #1076) collects the green ones into
  `deps/batch-YYYY-MM`, and one person-opened PR from that branch is the month's single deploy.

#### The latency alarm is muted while the services roll (#1144)

`batbern-staging-alb-latency-p95` used to mail through every rolling deploy (JVM warmup of each
replaced task, about 45 minutes and 22 mails). Every job that rolls ECS services now mutes it with
`scripts/ci/latency-alarm-actions.sh`:

| Job | Muted before | Re-enabled after |
|---|---|---|
| `deploy-to-staging`, fast path | `Fast-path code deployment` | `Wait for in-progress CloudFormation stacks` |
| `deploy-to-staging`, layered and selective | `Pre-deploy EventManagement` (layered) or the first `Selective Deploy` | `Wait for ECS services to stabilize` |
| `rollback-on-test-failure` | `Roll back to staging-stable` | `Wait for ECS services to stabilize after rollback` |

The re-enable step is `if: always()` with its own `always()` re-authentication, so a failed or
cancelled run re-enables the alarm too. If the alarm went to ALARM while muted, the script resets it
to OK before enabling (actions fire on state changes only), so a breach that persists past the
rollout mails on the next evaluation. All toggles are `continue-on-error` and only warn: the IAM
grant (`github-workflow-runtime`, that alarm's ARN only) arrives with the CICD stack in Layer 0. A
warning "Could not re-enable" in a run means the alarm is still mute; re-enable it by hand.

`Wait for ECS services to stabilize` (`scripts/ci/wait-ecs-services-stable.sh`) looks the four
services up by their real names (`BATbern-staging-<Service>-Service<hash>`, API gateway included)
and fails if one resolves to nothing. Before #1144 it asked for `event-management-service` etc.,
which ECS reports as MISSING, so it waited for nothing.

`test/unit/deploy-latency-alarm-silence.test.ts` finds every job that rolls services by the commands
it runs and pins the order, the plain `always()`, and both scripts' behaviour against a stub `aws`.

#### Three concurrent PRs: the middle deploy is silently cancelled (#967)

`deploy-staging.yml` sets `concurrency: {group: deploy-staging, cancel-in-progress: false}`. The
`false` is correct — a half-finished production deploy must not be interrupted — but it does **not**
mean "queue indefinitely". GitHub Actions allows **one running plus one pending** run per concurrency
group. When a third run joins, **the previously pending run is cancelled.**

Because `build.yml` invokes the deploy on *every* PR targeting `develop`, three open PRs means three
runs in that group, and the middle one dies. Its own build jobs all passed; only the queued deploy
was killed.

Then it looks like a failure. `auto-merge.yml` waits with `allowed-conclusions: success,skipped`, so a
`cancelled` deploy fails the `auto-merge` check and the PR goes `UNSTABLE` with a red mark. Nothing
was wrong with that PR.

Measured 2026-08-26 across the last 100 `build.yml` runs: **7 runs carry this signature** (every
`build-*` job green, `deploy-to-staging` cancelled), including run `31614489526` from the original
report. The other 35 cancellations in that window are whole-run aborts, which is a different thing.

Contributing factor worth knowing: **`build.yml` has no `concurrency` block at all.** Pushing a new
commit to a PR does not cancel that PR's previous run, so two full pipelines for the same branch each
hold a slot in the deploy group — which makes reaching the three-run threshold easier than it needs
to be.

**The operational rule: never have more than two PRs to `develop` in flight, and merge them strictly
one at a time.** Combined with the `BEHIND` stall below, the honest summary is that this repo's CI
supports exactly one PR at a time.

Considered and not done (recorded so it is not re-derived):

- **Accept `cancelled` as a non-failure in auto-merge.** Rejected on merit: the PR would then merge
  having never actually deployed, which is worse than a false red.
- **Re-dispatch the cancelled deploy.** Blocked by the same `GITHUB_TOKEN` recursion limit documented
  under #976 — it needs a PAT this repo does not have.
- **Add a `concurrency` block to `build.yml`** keyed on the PR, cancelling superseded PR builds.
  Reduces contention but does not remove the three-run eviction.
- **Deploy only on push to `develop`.** The real fix — it removes this, the `BEHIND` stall, and the
  opening-a-PR-ships-to-production hazard together. Deliberately deferred: it costs pre-merge
  validation on real infrastructure. See #967 and #976.

#### Two concurrent PRs: the second one stalls (#976)

`concurrency: deploy-staging` and `strict: true` branch protection are individually correct
and jointly self-defeating. Serialisation guarantees PR B's deploy finishes *after* PR A
merges; strictness then guarantees B is stale and refuses to merge. GitHub's auto-merge does
not update the branch, so B sits armed forever. Of N queued PRs, N-1 land here after each
merge.

Measured with #972 / #974 on 2026-08-18:

```
10:09  #972 opened            -> build + deploy start
10:43  #974 opened            -> builds pass in ~13 min, deploy job QUEUES behind #972
12:05  #972 deploy finishes, auto-merge MERGES it, develop advances
12:05  #974 deploy finally acquires the lock, runs, passes (20/20 checks green)
12:28  auto-merge.yml arms auto-merge on #974
 ...   nothing happens.  mergeable=MERGEABLE, mergeStateStatus=BEHIND
```

`gh pr checks` shows all green. **`mergeStateStatus=BEHIND` is the only signal**, and until
#976 nothing surfaced it. `auto-merge.yml` now polls for it after the checks pass and, when it
finds it, emits a `::warning::` annotation, a job-summary block, and a one-time PR comment
carrying the recovery command:

```bash
gh pr update-branch <pr-number>
```

Recovery costs a full rebuild plus another production deploy of content that is already
deployed. That is not automated, on purpose: `update-branch` commits as `GITHUB_TOKEN`, and
GitHub suppresses workflow runs for GITHUB_TOKEN-generated events, so the new head SHA would
carry no results for the 8 required contexts — the PR would go `BEHIND` → `BLOCKED` and stall
just as silently, with an extra merge commit on top. Re-triggering the checks needs a PAT (the
same recursion guard blocks `gh workflow run` with `GITHUB_TOKEN`), and no PAT secret exists in
this repo. The real answers are a PAT or GitHub's merge queue; both remain open on #976.

Until then: **work one PR to `develop` at a time.**

**Jobs:**
1. **Detect Changes**
   - Analyzes changed components via git diff
   - Selects deployment strategy (fast-path, hotswap, or full CDK)

2. **Database Backup** (if migrations or infrastructure changed)
   - Creates the manual RDS snapshot `batbern-staging-pre-deploy-{timestamp}` before deployment
   - Keeps the newest 3 `pre-deploy` snapshots and deletes older ones (owner decision 2026-11-03).
     Hand-made snapshots with another prefix (e.g. `batbern-staging-pre-v103-*`) are never touched;
     the role may delete only the `pre-deploy` prefix
   - A failure shows as an `::error::` annotation with the AWS message. From Dec 2025 to 2026-11-03
     no backup was taken: the role policy named the instance `BATbern-staging-*` (IAM compares ARNs
     case-sensitively, the instance is lowercase) and a `2>/dev/null` reported it as "instance not
     found" (#1032). Daily automatic backups (7 days, point-in-time restore) were unaffected
   - Listing snapshots is allowed account-wide (read-only); create and delete stay on the
     `pre-deploy` prefix. A failed listing shows as a `::warning::` "Snapshot retention skipped"
   - Creating a snapshot also tags it, because the instance has `CopyTagsToSnapshot=true`; the role
     needs `rds:AddTagsToResource` on the `pre-deploy` prefix (not on the instance). Missing until
     2026-11-03: the first deploy with a migration after #1201 stopped at this step (#1204). The CICD
     stack that grants it deploys after this step, so a change to this permission must be deployed
     first (`cdk deploy BATbern-staging-CICD --exclusively`)

3. **Deploy**
   - Deploys with AWS CDK (layer-based for infrastructure changes)
   - Waits for ECS services to stabilize
   - Then, as separate jobs in this order: `post-deploy-checks` (smoke, CORS, the anonymous
     access-control probe; on green it moves `staging-current` to this release), `bruno-tests`,
     `playwright-tests`. All green: `tag-stable-on-success` moves `staging-stable`. Any of the
     three failed: `rollback-on-test-failure` restores `staging-stable`. Until 2026-11-08 the
     post-deploy checks were steps of the deploy job, and their failure skipped both promotion
     and rollback (PR #1258)
   - Duration: 2-30 minutes depending on deployment tier

**Environment:** https://www.batbern.ch

### 4. Tagged Release Deployment (`deploy-production.yml`)

Deprecated and not usable: it targets the decommissioned account 422940799530 and fails at the
first AWS step. Kept by owner decision 2026-11-03. Production is deployed by every PR to `develop`.

## Required Secrets

Configure these secrets in GitHub repository settings:

### AWS Credentials
- AWS credentials are managed via OIDC federation (no long-lived keys)
- Role: `arn:aws:iam::188701360969:role/batbern-staging-github-actions-role`

### Database Credentials
- Database credentials are managed via AWS Secrets Manager
- Microservices access credentials at runtime via ECS task IAM roles
- No database credentials stored in GitHub Secrets

The full secret list: [github-secrets-setup.md](./github-secrets-setup.md).

## Image Tagging

`{sha}-staging.{run_number}`, e.g. `a1b2c3d-staging.142`. After a green post-deploy check the
release is tagged `staging-current`, after green Bruno and Playwright `staging-stable`.

## Deployment Process

### To Production (automatic)
1. Open a PR to `develop`. Opening it, and every further push to it, deploys that branch to
   production, unmerged (Dependabot PRs excluded)
2. After the deploy: post-deploy checks, Bruno, Playwright `@smoke`; rollback to `staging-stable`
   on failure
3. `auto-merge.yml` merges the PR when the required checks and the deploy are green

## Monitoring and Troubleshooting

### Build Failures

Read the failing job's log in GitHub Actions. Which checks are required is set in the branch
protection of `develop`; the jobs themselves are described above.

### Deployment Failures

**Migration Errors:**
```
Error: Flyway migration failed
Solution: Check database backup, fix migration script, retry
```

**ECS Service Unstable:**
```
Error: Service did not stabilize within timeout
Solution: Check ECS console, CloudWatch logs, increase timeout
```

**Stack still in progress from a previous deploy (#1140):**
```
Error: stack ... is in UPDATE_COMPLETE_CLEANUP_IN_PROGRESS state and can not be updated
Solution: none needed any more. The deploy job's first step, "Wait for in-progress
CloudFormation stacks" (scripts/ci/wait-for-stable-stacks.sh), waits up to 40 min for every
stack named in infrastructure/package.json to settle. It describes them one by one: the
GitHub Actions role may not call list-stacks. Deleting a VPC Lambda can keep a stack in cleanup for
~25 min (ENIs are released lazily); Layer 1 has a 40-min timeout for that reason.
```

**Smoke Tests Failed:**
```
Error: Endpoint returned 500
Solution: Check service logs, rollback if necessary
```

### Rollback Procedures

**Automatic Rollback:**
- A failed post-deploy check (smoke, CORS, access control), Bruno or Playwright run rolls the
  ECS services back to the `staging-stable` images and the frontend to its stable S3 snapshot
  (`rollback-on-test-failure`, `scripts/ci/rollback-deployment.sh`)
- A failure inside the deploy job itself (CDK, ECS stabilisation) is handled by the stack
  recovery steps of that job, not by this rollback
- Team is notified via workflow output
- The rollback moves the services behind CloudFormation's back. Rerunning the same deploy is
  safe: after the service stacks, `scripts/ci/ensure-services-run-image.sh` moves every service
  that does not run the run's image tag to the task definition registered for it (CloudFormation
  sees an unchanged template and would leave the rolled-back image in place)

**Manual Rollback:**
1. Navigate to AWS ECS Console
2. Select production cluster
3. Update service to previous task definition
4. Force new deployment

**Database Rollback:**
1. Find snapshot ID from deployment logs
2. Restore RDS instance from snapshot
3. Update application configuration
4. Redeploy previous version

## Quality Gates

- **Coverage:** frontend statements at least 73 % (Vitest, enforced in `build-frontend`); Java
  JaCoCo instruction coverage at least 0.60 per module, enforced by `make test` only, not in CI;
  none for CDK.
- **Security:** OWASP ZAP weekly, access-control probe after every deploy, `ai-security.yml`
  (advisory).
- **Docs:** Doc Drift Check on every PR (`[no-doc]` opts out), `sync-versions.yml`.

## Best Practices

### Branch Strategy
- Create feature branches from `develop`
- Name: `feature/{description}`
- PR to `develop` for review
- Squash merge to keep history clean

### Commit Messages
Follow conventional commits:
```
feat(cicd): add blue-green deployment
fix(build): correct coverage threshold check
docs(deployment): update runbook
test(integration): add contract tests
```

## Further Reading

- [GitHub Actions Documentation](https://docs.github.com/actions)
- [AWS CDK Guide](https://docs.aws.amazon.com/cdk/)
- [Flyway Documentation](https://flywaydb.org/documentation/)
