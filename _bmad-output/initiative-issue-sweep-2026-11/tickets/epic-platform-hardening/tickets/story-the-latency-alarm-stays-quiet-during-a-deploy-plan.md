---
title: 'The latency alarm stays quiet during a deploy, the availability alarm needs volume'
type: 'bugfix'
ticket: '4'
created: '2026-11-19'
status: 'done'
route: 'full'
risk: 'medium'
review: 'quick'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** (#1144, R24) `batbern-staging-alb-latency-p95` flaps through every rolling deploy of the services (about 45 min, 22 mails); `batbern-staging-alb-availability` fires on a single night-time 500 because at ~40 requests per 5 min one error drops below 99.9 %.

**Approach (recommendation of #1144, taken):**
1. The deploy job (`.github/workflows/deploy-staging.yml`) runs `aws cloudwatch disable-alarm-actions --alarm-names batbern-staging-alb-latency-p95` before the first service layer and `enable-alarm-actions` after the last, in an `if: always()` step so a failed or cancelled deploy re-enables it. The GitHub Actions role needs `cloudwatch:DisableAlarmActions` and `cloudwatch:EnableAlarmActions` on that alarm ARN only: find where that role is defined (CDK stack or outside CDK; read-only `aws iam get-role-policy`/`list-attached-role-policies` with `AWS_PROFILE=batbern-staging` to see its current permissions). If it is defined in CDK, add the grant there with a CDK test; if it is not, stop and report (do not change IAM by hand).
2. The availability alarm gets a minimum request volume like the latency alarm: `IF(requests >= availabilityMinRequests, ratio, 100)`, threshold config in `lib/config/*-config.ts`, default chosen from data.
3. `alb-5xx` stays as is and keeps reporting single errors.

## Boundaries & Constraints

**Always:** CDK tests for the availability expression and for alb-5xx unchanged; a workflow check (existing workflow lint/test, or a small test that parses the YAML) that the enable step has `if: always()`. Read-only replay of the last 30 days of production ALB metrics (`RequestCount`, `HTTPCode_Target_5XX_Count`, `HTTPCode_ELB_5XX_Count` per 5 min, AWS_PROFILE=batbern-staging, `get-metric-data`) through the old and new availability rule: list the periods each would fire, and pick the minimum volume so a real outage (several 5xx in a busy period) still fires; numbers in the plan's Implementation Notes. Doc: alarm table in `docs/architecture/08-operations-security.md` and the runbook alarm section if it lists these alarms.

**Never:** Change the latency alarm's threshold or periods; remove OK actions; touch other alarms; write anything to AWS.

</frozen-after-approval>

## Code Map

- `infrastructure/lib/constructs/alb-alarms.ts:340-420` (latency, availability, 5xx), thresholds `lib/config/staging-config.ts`, tests under `infrastructure/test/unit/` (grep `alb-availability`).
- `.github/workflows/deploy-staging.yml` layers 4.1 to 4.6 (lines ~674-840), credentials step `aws-actions/configure-aws-credentials` with `batbern-staging-github-actions-role`.

## Verification

- `cd infrastructure && npx jest --maxWorkers=4` (alarm and stack tests), `npm run lint`; actionlint if installed.

## Notes (checked 2026-11-19)

- The role is CDK-defined: `infrastructure/lib/stacks/cicd-stack.ts:124`, live with 4 managed policies (`batbern-staging-github-{cdk-services,cdk-networking,workflow-runtime,cdk-deployment}`); the runtime grant belongs in `github-workflow-runtime`.
- Check whether `deploy-staging.yml` deploys the CICD stack itself. If not, the grant only exists after someone deploys it, so the disable and enable steps must not fail the deploy (`continue-on-error: true` with a `::warning::`), and the PR body must say the CICD stack needs a deploy.
- Replay done (checked 2026-11-19, read-only, LB app/BATber-Servi-XlmJ3ScSHcvA/bbbb0c7eb291ca13, 300 s periods, 2 of 2 below 99.9 %, 30 days): requests per 5 min median 2, p90 92. Real alarm history: 8 OK→ALARM transitions in 30 days. Replayed current rule (requests > 0): 4 firings (2026-10-22 11:40, 10-26 09:15, 10-27 22:40, 10-28 22:25). Minimum 100: 1 firing (10-26 09:15, 49 5xx in 38,539 requests); same for 200, 300, 500. **Use availabilityMinRequests = 100** (the value #1144 proposes), expression `IF(requests >= 100, ..., 100)`. Single errors stay visible through alb-5xx.

## Tasks

- [x] Availability volume floor: `availabilityMinRequests` threshold in `AlbAlarms` (default 100), `alarms.availabilityMinRequests: 100` in `staging-config.ts` (new optional `AlarmConfig` in `environment-config.ts`), passed by `ApiGatewayServiceStack`. Expression `IF(requests >= 100, (1 - (target5xx + elb5xx) / requests) * 100, 100)`. Tests first (red: compile error on the missing prop / config field), then green.
- [x] alb-5xx unchanged, pinned by a test (expression, threshold, 2 of 2).
- [x] IAM: `cloudwatch:DisableAlarmActions` + `cloudwatch:EnableAlarmActions` on `arn:aws:cloudwatch:<region>:<account>:alarm:batbern-<env>-alb-latency-p95` only, in `github-workflow-runtime` (`cicd-stack.ts`). Test first (red, then green). Rendered policy 2,186 chars, far under 6,144.
- [x] `deploy-staging.yml`: disable step before the fast path (`code_only == 'true'`) and one before the EventManagement pre-deploy (`code_only != 'true'`, covers layered and selective); re-auth + re-enable steps after `Wait for ECS services to stabilize`, both `if: always()`; all `continue-on-error: true` with `::warning::`.
- [x] Workflow test `infrastructure/test/unit/deploy-latency-alarm-silence.test.ts` (js-yaml, already a dependency): alarm name from `env.ENVIRONMENT`, disable before every rollout path, enable under `always()` after the last rollout, `always()` re-auth right before it, no toggle can fail the deploy. Red (5 failed) before the workflow edit.
- [x] Docs: ALB row in `08-operations-security.md`, runbook ALB item 4 (`docs/operations/runbooks.md`, it said "deliberately unsilenced"), new subsection in `docs/deployment/cicd-pipeline-guide.md`, mapping `deploy-staging.yml` → `cicd-pipeline-guide.md` in `.github/doc-drift-mappings.yml`.

## Implementation Notes (2026-11-19)

- **Three rollout paths, not one.** Besides layers 4.1-4.6, the fast path (`update-ecs-task.sh`) and the selective path roll services, and in the layered path the "Pre-deploy EventManagement" step (before Layer 2) already deploys EMS with the new image tag. So the CDK-path disable sits before that pre-deploy, which also puts Layer 2-3 and Layer 5 inside the muted window (accepted: they do not roll ECS tasks). In the selective path every step between that position and the selective deploys is layered-only, so the disable runs right before them.
- **Re-enable position:** after `Wait for ECS services to stabilize`, i.e. after the last rollout of every path. In the fast path that step is skipped and the enable runs after `Wait for in-progress CloudFormation stacks`. Calling enable on an alarm whose actions were never disabled is a no-op.
- **Credentials:** the layered path outlives a one-hour role session and a failed run skips the ordinary re-auth steps, so the enable step has its own `always()` configure-aws-credentials step.
- **Grant timing:** Layer 0 deploys the CICD stack before the first disable in the layered path. A PR touching `infrastructure/` takes the layered path, so the grant should exist at the first disable of the PR that ships it. In the fast and selective paths, or if Layer 0 fails, the calls are denied and only warn.
- **Residual risk:** if the disable succeeds and the enable fails, the alarm stays mute until the next deploy re-enables it; the run shows a `::warning::` with the command to run. The runbook says so.
- Pre-push CICD permission sync check replayed by hand: every `aws <svc> <cmd>` in the deploy/build workflows has a test in `cicd-stack.test.ts`.
- actionlint is not installed on rack; the workflow was checked by YAML parse and the jest test only.
- Verification: infra jest full suite 40 passed, 2 skipped of 42 suites; 543 passed, 19 skipped of 562 tests (`~/.cache/batbern-test-logs/e2-4-infra.log`). `npm run lint` clean (eslint, max-warnings 0), `tsc --noEmit` clean, prettier clean on the touched files.
- PR body must say: the CICD stack (Layer 0) carries the new grant; nothing to deploy by hand if the PR runs the layered path.

### Review round 1 (2026-11-19)

- **Reset after mute.** Alarm actions fire on state changes only, so an alarm that went to ALARM while muted never mailed. The toggles moved into `scripts/ci/latency-alarm-actions.sh`; `enable` reads `StateValue` and, if ALARM, runs `set-alarm-state --state-value OK --state-reason "reset after deploy (#1144)"` before `enable-alarm-actions`. Reset-before-enable (not after, as the review proposed) so the reset itself sends no OK mail; the next evaluation alarms and mails if the breach persists. `cloudwatch:SetAlarmState` added to the same statement and ARN; `DescribeAlarms` is already granted on `*` (no resource-level permissions). Script always exits 0, warnings on failure.
- **Rollback job.** `rollback-on-test-failure` runs `rollback-deployment.sh` (ecs update-service). It now mutes before the rollback, waits for stable services after it (`always()`), re-authenticates (`always()`) and re-enables (`always()`). The workflow test finds rolling jobs by command (`update-ecs-task.sh`, `rollback-deployment.sh`, `ecs update-service`, `deploy:staging:layer4-`, `cdk deploy` of a service stack), not step name, and requires `if` to equal `always()` for the enable and its re-auth.
- **Other workflows.** `deploy-code-staging.yml` also rolls services (update-ecs-task.sh) but no workflow calls it (checked 2026-11-19); the test pins both facts, so calling it later fails the test until it gets the mute.
- **Stabilize step fixed (pre-existing bug).** It waited for `event-management-service` etc., which ECS reports as MISSING, so it waited for nothing and never covered the gateway. Now `scripts/ci/wait-ecs-services-stable.sh` resolves `BATbern-<env>-<Fragment>-Service` for CompanyManagement, EventManagement, PartnerCoordination, ApiGatewayService, fails with `::error::` if one resolves to nothing, keeps a warning for a service that does not settle (3 waiter rounds, ~30 min). Used by the deploy and rollback jobs.
- **Infra-change regex (pre-existing bug).** `infrastructure/lib/config/` was missing, so a config-only change (like this story's threshold) was classified code-only and never deployed. Added; the test extracts the pattern from the workflow and checks 7 paths that must match and 2 that must not.
- **Runbook** no longer mentions Spot (production runs plain FARGATE).
- Scripts tested against a stub `aws` on PATH (records calls, answers from env vars): 7 tests.
- Red→green: cicd-stack 1 failed of 30 → 30 passed; deploy-latency-alarm-silence 19 failed of 30 (incl. the lib/config regex) → 30 passed. Touched suites together: 3 suites, 83 tests passed. Full infra suite not rerun in this round (Gradle running on the machine; coordinator runs it).
- **Not fixed, finding:** `rollback-deployment.sh` rolls back only EMS, PCS, CUMS, not the API gateway service. Changing what the rollback reverts is a behaviour decision; left for the coordinator/owner.
