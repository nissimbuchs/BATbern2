---
title: 'The latency alarm stays quiet during a deploy, the availability alarm needs volume'
type: 'bugfix'
ticket: '4'
created: '2026-11-19'
status: 'approved'
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
