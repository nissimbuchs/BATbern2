# Incident Runbooks

Operator-facing runbooks for every CloudWatch alarm that exists in the BATbern estate.

**Scope decision (#1003, 2026-08-25).** Acceptance criteria AC13 (PagerDuty) and AC16
(StatusPage) were dropped as over-specified for this system: BATbern is a volunteer-run
conference platform with a single operator and no between-event audience watching a status
page. AC14 (runbooks) and AC15 (post-mortem process) are delivered here, as **documents an
operator follows**, not as auto-remediation Lambdas. A bot that restarts production services in
response to a metric is a larger risk than the incidents it would handle.

## How an alarm reaches a human

```
CloudWatch alarm
  -> SNS batbern-staging-alarms
     -> Lambda batbern-staging-github-issues  -> GitHub issue (with metric + redacted log context)
     |                                           -> .github/workflows/claude.yml -> triage comment
     -> email nissim@buchs.be
```

The GitHub issue is the incident record. Work in it, and close it when the alarm returns to `OK`
(alarms carry an OK action, so recovery closes the issue automatically).

> **Known gap:** the seven identity alarms listed under [Identity and user sync](#identity-and-user-sync)
> publish to `batbern-user-sync-alarms-staging`, which has **zero subscriptions** — they reach
> nobody. Tracked as **#1005**.
>
> The subscription address has been changed from the unread `admin@batbern.ch` to
> `nissim@buchs.be` (`monitoring-stack.ts`). **That is not live until the stack deploys, and SNS
> will then send a confirmation email that must be clicked within 3 days** — an unconfirmed
> subscription is deleted and the topic goes silent again, which is precisely how this defect
> arose. Confirm it, then verify:
>
> ```bash
> aws sns list-subscriptions-by-topic \
>   --topic-arn arn:aws:sns:eu-central-1:188701360969:batbern-user-sync-alarms-staging \
>   --query 'Subscriptions[].[Protocol,Endpoint,SubscriptionArn]' --output text
> ```
>
> A `SubscriptionArn` of `PendingConfirmation` means it is still not delivering.
>
> Note this restores **email** only. These seven alarms still bypass the github-issues Lambda, so
> they produce no GitHub issue and no triage comment. Routing them at the shared
> `batbern-staging-alarms` topic instead would fix both and remove the confirmation dependency
> for good; that remains the proposal in #1005.
>
> Until confirmed, check these alarms by hand in the CloudWatch console. Do not treat their
> silence as health.

## Standing conventions

- Profile is always `batbern-staging` — it is the **production** account (188701360969).
- ECS cluster: `batbern-staging`. Log groups: `/aws/ecs/BATbern-staging/{service}`.
- Services run `SPRING_PROFILES_ACTIVE=staging` in the production account. Any config block
  gated `on-profile: production` is unreachable — do not read it as active.
- **Zeros can mean "no traffic", not "fixed".** Before reading a filter's silence as success,
  confirm the window had events at all.

```bash
export AWS_PROFILE=batbern-staging
aws cloudwatch describe-alarms --alarm-names <name> \
  --query 'MetricAlarms[0].{State:StateValue,Reason:StateReason,Updated:StateUpdatedTimestamp}'
```

---

## ALB and edge

**Alarms:** `alb-5xx`, `alb-availability`, `alb-latency-p95`, `alb-unhealthy-targets`,
`api-4xx-ratio`

1. Is it one service or all of them? `aws elbv2 describe-target-health --target-group-arn <arn>`
   — an all-service failure points at the database or the ALB, a single-service one at that
   service's last deploy.
2. Check for a deploy in the window: `gh run list --workflow build.yml --limit 5`.
3. Read the service log for the window (see per-service commands in CLAUDE.md).
4. `alb-latency-p95` alone, lasting 10-15 minutes and self-resolving, is very likely an ECS task
   replacement rather than a defect — see **#982**, which is a known, deliberately unsilenced
   pattern. Confirm by matching the window to a task-replacement event before investigating further.
5. `api-4xx-ratio` is ratio-based and ZAP-proof by design (#995). A spike from a single client IP
   is a scanner, not a regression.

**Escalation:** none available. Single operator.

---

## ECS service health

**Alarms:** `{Service}-High-CPU`, `{Service}-High-Memory`, `{Service}-Task-Failures`,
`{Service}-EventBridge-Failures` for ApiGatewayService, CompanyManagement, EventManagement,
SpeakerCoordination, PartnerCoordination, AttendeeExperience.

> `{Service}-OOM-Kills` is declared in `ecs-service-alarms.ts` but **dormant by design**: it is
> gated on `containerInsightsEnabled`, and Container Insights is off on the cluster as a cost
> decision (`cluster-stack.ts`). No stack passes the flag, so the alarm has never deployed and
> will not fire. If Insights is ever re-enabled, this alarm becomes live — read the comment
> above the construct first, it records why the metric is named per service rather than
> dimensioned (the CloudWatch Logs API rejects dimensions on literal-term filter patterns).

1. `aws ecs describe-services --cluster batbern-staging --services <name> --query 'services[0].{Running:runningCount,Desired:desiredCount,Events:events[:5]}'`
2. **`Task-Failures` right after a deploy is usually Flyway.** Grep the log group for
   `checksum mismatch` / `FlywayValidateException`. If present, an applied migration was edited —
   the service is crash-looping and ECS has rolled back to a stale image, so the deploy is
   silently not advancing. Compare the deployed image tag against `develop` HEAD.
   Fix forward with a **new** migration; never edit the applied one.
3. `High-Memory` with no deploy: check for an N+1 introduced by an `@ElementCollection` fetch
   change, or an unpaginated `findAll()`.
4. **Do not restart to make the alarm go away** before capturing the log window. The task's logs
   are the only evidence and a replacement discards the running state.

---

## Database

**Alarms:** `database-connections`, `database-cpu`, `database-storage-low`

Single-AZ `db.t4g.micro`. There is no failover; a restart is a full outage.

1. `database-connections`: almost always a HikariCP leak in one service — grep `/aws/ecs/BATbern-staging/*`
   for `HikariPool`. Identify the service before acting; restarting the wrong one does nothing.
2. `database-cpu`: look for a missing index on a newly added query path, not for load. Traffic is
   ~1000 users/month; sustained CPU is a query defect.
3. `database-storage-low`: check whether pre-deploy RDS snapshots have accumulated.

---

## Email and SES

**Alarms:** `bounce-rate-warning`, `bounce-rate-critical`, `complaint-rate-critical`,
`emails-rejected`, `bounce-processing-dlq`

**SES reputation is the single most damaging thing to lose here** — a suspension stops speaker
invitations, registration confirmations and the newsletter at once.

1. `bounce-rate-*`: identify whether the bounces are one bad import or a systemic address problem.
   Note **#984** — `bounce-rate-warning` currently watches a metric that keeps it latched on a
   single May event with 0 bounces in 14 days. Confirm against real recent sends before acting.
2. `emails-rejected` is the abuse-detection signal. Treat a real firing as urgent.
3. `bounce-processing-dlq`: messages the bounce handler failed. Drain and replay; do not purge.
4. If a test or E2E run caused the spike, that is the **newsletter super-GAU** class of incident
   (2026-07-01). Both walls exist now (Playwright `@sends-real-email` guard and the backend
   `EmailContentTestMarker` send-block) — verify which one was bypassed.

---

## Identity and user sync

**Alarms:** `JIT-Provisioning-High-Failures`, `PreSignUp-Linking-Failures`,
`PreTokenGeneration-High-Latency`, `PostConfirmation-High-Latency`,
`User-Creation-High-Failures`, `User-Sync-High-Drift`, `Reconciliation-Orphaned-Users`

> These currently notify **nobody** — see **#1005**.

**A Cognito trigger that throws 503s every sign-in.** That is the failure mode to rule out first.

1. Check the trigger Lambdas' own log groups for unhandled exceptions. Triggers are written to
   fail open (catch, emit a metric, return the event) — an alarm here often means the fail-open
   path itself is firing, which is a warning, not yet an outage.
2. `PreSignUp-Linking-Failures`: federated identity could not be matched to an existing account.
   Expected in small numbers. A spike means the case-insensitive email match or the verified
   additional-email fallback regressed.
3. `Reconciliation-Orphaned-Users`: a Cognito user with no `user_profiles` row. Usually a JIT
   interceptor failure earlier in the chain — resolve that first, then re-run reconciliation.
4. Kill switch if SSO itself is the problem: `FEATURES_SSO_ENABLED=false`.

---

## When the triage loop itself is the problem

Kill switch: set `CLAUDE_TRIAGE_ENABLED=false` on the `batbern-staging-github-issues` Lambda.
Effective immediately, no deploy needed.

Do **not** fix a runaway triage loop by granting the workflow more permissions. `contents: read`
in `.github/workflows/claude.yml` is deliberate: a PR against `develop` deploys to
www.batbern.ch, so `contents: write` would let an agent reacting to a metric perform an
unreviewed production deploy.

---

## After the incident

Any incident that caused user-visible impact, or that took more than an hour, gets a post-mortem:
[`post-mortem-template.md`](post-mortem-template.md), or open one from the
**Post-mortem** issue template. Blameless, and the action items go in the backlog before the
document is considered done.
