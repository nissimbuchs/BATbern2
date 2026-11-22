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

The seven identity alarms (see [Identity and user sync](#identity-and-user-sync)) publish to the
same shared topic since #1005 (fixed by #1006); the former private topic with no subscribers is
gone.

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
   replacement rather than a defect — see **#982**. During a deploy and an auto-rollback its
   actions are disabled (`deploy-staging.yml`, `scripts/ci/latency-alarm-actions.sh`, #1144), so
   a mail outside them means an unplanned task replacement (health-check failure, OOM) or a real
   regression. A mail right after a deploy means the breach outlasted it: the alarm went to ALARM
   while muted and was reset to OK, and the next evaluation alarmed again. Confirm by matching
   the window to a task-replacement event before investigating further. If a run shows the
   warning "Could not re-enable", run
   `aws cloudwatch enable-alarm-actions --alarm-names batbern-staging-alb-latency-p95`.
   `alb-availability` evaluates only at 100+ requests per 5 minutes (#1144); a lone 500 at night
   shows up in `alb-5xx` alone.
5. `api-4xx-ratio` is ratio-based and ZAP-proof by design (#995). A spike from a single client IP
   is a scanner, not a regression.
6. Request path since #1152: `api.batbern.ch` (HTTP API) → VPC link `batbern-staging-http-api` →
   internal ALB → gateway. The ALB alarms watch the internal ALB. If every request fails with 503
   and no target is unhealthy, check the VPC link (`aws apigatewayv2 get-vpc-links`, status
   `AVAILABLE`) and the HTTP API integration (`aws apigatewayv2 get-integrations --api-id
   <id>`, connection type `VPC_LINK`). The visitor's address reaches the services as
   `X-BATbern-Client-IP`; a rate-limit log line keyed on `3.71.12x.x` / `3.127.7x.x` (API Gateway
   egress) means the header is not getting through.

**Escalation:** none available. Single operator.

---

## ECS service health

**Alarms:** `{Service}-High-CPU`, `{Service}-High-Memory`, `{Service}-Task-Failures` for
ApiGatewayService, CompanyManagement, EventManagement, PartnerCoordination. (The speaker and
attendee stubs and their alarms went with their stacks, Story 17.2; the `-EventBridge-Failures`
alarms went with the event bus, Story 17.2 part B.)

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

### Domain event listener failures

**Alarms:** `{Service}-Event-Listener-Failures` for EventManagement, CompanyManagement,
PartnerCoordination. Fires on the first swallowed failure in a 5-minute window.

`SpringDomainEventPublisher` delivers domain events to in-process listeners. When a synchronous
listener throws, the publisher logs it and **the request that published the event still
succeeds**, so the user sees no error while the listener's side effect (a mail, a workflow
transition, a notification) did not happen. The alarm is a CloudWatch metric filter on the marker
`DOMAIN_EVENT_LISTENER_FAILED` in the service log group (metric
`BATbern/DomainEvents ListenerFailures-{Service}`).

1. Find the line and its stack trace (the line names the listener, the event type and the
   aggregate):
   `aws logs filter-log-events --log-group-name /aws/ecs/BATbern-staging/<service> --start-time <ms> --filter-pattern '"DOMAIN_EVENT_LISTENER_FAILED"'`
2. Decide from the listener what was lost and for whom (the aggregate id says which event,
   speaker or user), and repair that by hand if it matters before the code fix lands.
3. Fix the listener test first. Whether a listener failure should fail the caller instead is
   story 17.12, not something to change during an incident.

### Scheduled job failures

**Alarm:** `EventManagement-Scheduled-Job-Failures`. Fires on the first recorded failure and stays
in ALARM for 24 hours (1-hour periods, 24 evaluated), the interval of the daily job. Its OK action
closes the GitHub issue when a day passes without a failure, whether or not the skipped item was
handled: do step 2 within that day.

A scheduled job that cannot process one item records it in `ScheduledJobFailures` (EMS) and goes
on with the next one. Today that is the daily auto-publish (01:00): an event whose speakers or
agenda could not be published by the job. The alarm is a CloudWatch metric filter on the marker
`SCHEDULED_JOB_FAILED` in the EMS log group (metric
`BATbern/ScheduledJobs ScheduledJobFailures-EventManagement`).

1. Find the line and its stack trace (it names the job, e.g. `auto-publish-agenda`, and the item,
   the event code):
   `aws logs filter-log-events --log-group-name /aws/ecs/BATbern-staging/event-management --start-time <ms> --filter-pattern '"SCHEDULED_JOB_FAILED"'`
2. The job does not retry: the auto-publish runs on exactly the scheduled day. Publish the phase by
   hand in the event's Publishing tab once the cause is clear.

### Agent runs

**Alarms:** `Agent-Run-Failures`, `Agent-Refused-Mints` (any in 5 minutes) and
`Agent-Daily-Budget-Breaches` (any within 24 hours, 1-hour periods; its OK action closes the issue a
day after the last breach). All three are quiet while the agent is switched off (`AGENT_ENABLED`
false): no run, no line.

agent-service writes one marker line per run end an alarm watches (`RunAlarms`), with the run id,
the end reason and the end detail only, never personal data (#1202). Metric filters on
`/aws/ecs/BATbern-staging/agent` turn them into metrics in `BATbern/AgentRuns`:

| Marker | Metric | Alarm | Means |
|---|---|---|---|
| `AGENT_RUN_FAILED` | `RunFailures-Agent` | `Agent-Run-Failures` | a fault in agent-service (`end_reason` INTERNAL_ERROR); the run is FAILED |
| `AGENT_CREDENTIALS_REFUSED` | `RefusedMints-Agent` | `Agent-Refused-Mints` | CUMS refused the run grant or a mint, or could not be asked (GRANT_REFUSED, TOKEN_REFUSED); the line ends with the CUMS code |
| `AGENT_DAILY_BUDGET_BREACHED` | `DailyBudgetBreaches-Agent` | `Agent-Daily-Budget-Breaches` | `agent.limits.daily-tokens` is spent: every further run today is handed to the organizers |
| `AGENT_RUN_CAP_REACHED` | `RunCapsReached-Agent` | none | any limit stopped a run (also on a budget breach); a per-run cap is the cap working |

A run handed to the organizers because the model was unreachable (MODEL_UNAVAILABLE) has no marker
and no alarm: until the owner installs the Anthropic key every run ends that way, and an outage of
the model is not a fault of ours.

1. Find the line: `aws logs filter-log-events --log-group-name /aws/ecs/BATbern-staging/agent --start-time <ms> --filter-pattern '"AGENT_RUN_FAILED"'`
   (or the other marker). It names the run id.
2. For a failure, the line before the marker (`Agent run <id> failed in step <n>: <exception class>`)
   names the fault; the API stores no error of the run itself. Then read the run through the console
   API (`GET /api/v1/agent/runs/{runId}` as an organizer): steps, model calls (with their error when
   the model call failed), tool calls and verdicts.
3. Refused mints: the code says why. `CALLER_SECRET_MISSING`: agent-service has no caller secret
   (see "Agent signing key" below); `AGENT_SIGNING_KEY_MISSING`: CUMS has no signing key; a grant
   code (`GRANT_INVALID` and others): the principal or grant is no longer valid, which is the gate
   working, unless it repeats for many runs.
4. Budget breaches: decide whether the day's spend was real work (the run list, model calls per run)
   or a runaway; raise `AGENT_LIMITS_DAILY_TOKENS` only by a deploy, or switch the agent off.

---

## Database

**Alarms:** `database-connections`, `database-cpu`, `database-storage-low`

Single-AZ `db.t4g.micro`. There is no failover; a restart is a full outage.

1. `database-connections`: almost always a HikariCP leak in one service — grep `/aws/ecs/BATbern-staging/*`
   for `HikariPool`. Identify the service before acting; restarting the wrong one does nothing.
2. `database-cpu`: look for a missing index on a newly added query path, not for load. Traffic is
   ~1000 users/month; sustained CPU is a query defect.
3. `database-storage-low`: check whether pre-deploy RDS snapshots have accumulated.

### Restoring the database

Not rehearsed yet. Written 2026-11-20 from the live configuration (#1220 item 4); run it once on a
quiet evening against a throw-away restore and correct this section with what you learn.

**What there is to restore from** (checked 2026-11-20):

| Source | Kept | Granularity |
|---|---|---|
| Automated backups of `batbern-staging-postgres` | 7 days (`BackupRetentionPeriod`), window 03:00-04:00 UTC | any second up to about 5 minutes ago (point in time) |
| `batbern-staging-pre-deploy-*` manual snapshots | newest 3; `deploy-staging.yml` takes one before a deploy that changes a migration or a Dockerfile and deletes older ones (#1032) | the moment before that deploy |
| Hand-made snapshots (e.g. `batbern-staging-pre-v103-*`) | until deleted by hand | that moment |

A restore never overwrites the running instance: RDS always creates a **new** instance. The database
secret (`RdsClusterInstanceSecret*`) has no rotation, so a restored instance accepts the same
credentials the services already use.

**First decide: partial or full.**

- **Partial** (a bad migration or a wrong bulk change hit some tables): restore to a side instance,
  copy the affected rows back. Production keeps running. Prefer this.
- **Full** (the instance is lost or the data is broadly wrong): replace the instance. Outage of
  roughly the restore time plus the service restarts.

**1. Restore to a side instance** (both cases):

```bash
export AWS_PROFILE=batbern-staging AWS_REGION=eu-central-1
SRC=batbern-staging-postgres
NEW=batbern-staging-postgres-restore-$(date +%Y%m%d%H%M)
COMMON="--db-instance-class db.t4g.micro --db-subnet-group-name $(aws rds describe-db-instances \
  --db-instance-identifier $SRC --query 'DBInstances[0].DBSubnetGroup.DBSubnetGroupName' --output text) \
  --vpc-security-group-ids $(aws rds describe-db-instances --db-instance-identifier $SRC \
  --query 'DBInstances[0].VpcSecurityGroups[0].VpcSecurityGroupId' --output text) \
  --db-parameter-group-name $(aws rds describe-db-instances --db-instance-identifier $SRC \
  --query 'DBInstances[0].DBParameterGroups[0].DBParameterGroupName' --output text) \
  --no-publicly-accessible --copy-tags-to-snapshot"

# a) point in time (UTC), within the last 7 days
aws rds restore-db-instance-to-point-in-time --source-db-instance-identifier $SRC \
  --target-db-instance-identifier $NEW --restore-time 2026-11-20T15:00:00Z $COMMON
# b) or from a snapshot
aws rds describe-db-snapshots --db-instance-identifier $SRC \
  --query 'reverse(sort_by(DBSnapshots,&SnapshotCreateTime))[].[DBSnapshotIdentifier,SnapshotCreateTime]' --output text
aws rds restore-db-instance-from-db-snapshot --db-snapshot-identifier <id> \
  --db-instance-identifier $NEW $COMMON

aws rds wait db-instance-available --db-instance-identifier $NEW
```

**2a. Partial: copy rows back.** Open a tunnel to the side instance through the bastion
(`scripts/staging/start-db-tunnel.sh` reads the endpoint of `batbern-staging-postgres`; point it at
`$NEW` for this) and compare with production through a second tunnel. Copy with `pg_dump --data-only
-t <table>` or `\copy` of the selected rows, inside one transaction on production. Each service's
Flyway history table (`flyway_schema_history_*`) must stay as production has it: never copy those.
Do not download a dump to a laptop (CLAUDE.md, personal data); work on `rack` or the bastion and
delete the files afterwards. Then delete the side instance:
`aws rds delete-db-instance --db-instance-identifier $NEW --skip-final-snapshot`.

**2b. Full: swap the instances.** The services connect to the endpoint
`batbern-staging-postgres.<id>.eu-central-1.rds.amazonaws.com`, which follows the instance
identifier. Swapping the identifiers therefore points every service at the restored data without a
CDK deploy.

```bash
# stop writers (note each desiredCount first, restore them afterwards)
for s in $(aws ecs list-services --cluster batbern-staging --query 'serviceArns[]' --output text); do
  aws ecs update-service --cluster batbern-staging --service $s --desired-count 0 >/dev/null; done
aws rds modify-db-instance --db-instance-identifier $SRC --new-db-instance-identifier $SRC-old --apply-immediately
aws rds wait db-instance-available --db-instance-identifier $SRC-old
aws rds modify-db-instance --db-instance-identifier $NEW --new-db-instance-identifier $SRC \
  --backup-retention-period 7 --preferred-backup-window 03:00-04:00 \
  --preferred-maintenance-window sun:04:00-sun:05:00 --deletion-protection --apply-immediately
aws rds wait db-instance-available --db-instance-identifier $SRC
# start the services again with their old desiredCount; Flyway validates the history on boot
```

Check after the swap: each service log shows Flyway at the expected version and no `checksum
mismatch`; `https://api.batbern.ch/api/v1/events/current` answers; the three `database-*` alarms
refer to the instance by identifier, so they follow the swap. Keep `$SRC-old`
(deletion protection on) until the owner agrees to delete it. The next CDK deploy of the database
stack must show no replacement of the instance (`cdk diff BATbern-staging-Database`); if it does,
stop and do not deploy.

**Everything written after the restore point is lost** in a full swap: registrations, Mail Center
messages and uploads metadata of that window. Before the swap, export what was written since
(compare `created_at`/`updated_at` on the old instance) if it is still readable.

---

## Email and SES

**Alarms:** `bounce-rate-warning`, `bounce-rate-critical`, `complaint-rate-critical`,
`emails-rejected`, `forwarding-dlq`, `replies-dlq`, `forwarding-failed`, `bounce-processing-dlq`,
`mail-delivery-status-dlq`

**SES reputation is the single most damaging thing to lose here** — a suspension stops speaker
invitations, registration confirmations and the newsletter at once.

1. `bounce-rate-*`: identify whether the bounces are one bad import or a systemic address problem.
   `bounce-rate-warning` fires on more than 5 SES bounces in one hour (a send path bouncing now);
   `bounce-rate-critical` watches the rolling reputation rate. The former latching on one May event
   (#984) was fixed by #1007.
2. `emails-rejected` is the abuse-detection signal. Treat a real firing as urgent. Since forwarding
   moved into EMS, auto-replies and mail to `noreply@`/`calendar@` no longer count, so a firing is
   real senders being refused: check the EMS log for `Forwarding: unauthorized sender` /
   `is not authenticated (dmarc=...)` to tell spoofing from an organizer on an unauthenticated mailbox.
3. `forwarding-dlq`: a mail to a `@batbern.ch` alias failed three times before anything was sent
   (EMS released it each time, usually CUMS unreachable). Fix the cause, then redrive the DLQ
   (`aws sqs start-message-move-task`); the raw mail stays in S3 `forwarding/` for 7 days and
   `email_forwarding_log` makes a redrive safe (no double delivery).
   A claim whose task died *before* sending (Fargate Spot, deploy) is taken over by the next
   redelivery after 6 minutes, so that case heals itself.
   `replies-dlq` is the same for mail to `replies@` (Story 16.0, #1160): a human reply that matched
   no keyword could not be forwarded to the organizers three times before anything was sent. Only
   that forward rethrows, so the keyword actions never ran for these messages and a redrive of the
   replies DLQ to `batbern-inbound-email-{env}` is safe; the raw mail is in S3 `emails/` for 7 days
   and `email_forwarding_log` (alias `replies`) prevents a double send.
4. `forwarding-failed`: copies could not be sent after sending began, or a redelivery found a
   forward that stalled mid-send (task died between copies). These are never retried, to avoid a
   double blast. Find the S3 key in the EMS log (`stalled after sending began` / `copy of … failed`),
   check `email_forwarding_log` (`outcome`, `recipient_count`, `failed_count`), and decide by hand
   whether the missing recipients need the mail.
5. `bounce-processing-dlq`: messages the bounce handler failed. Drain and replay; do not purge.
   `mail-delivery-status-dlq` (Story 16.1b): SES delivery/bounce events EMS failed to apply three
   times, so the affected mails still show `SENT` in the Mail Center. No mail is lost or resent.
   Check the EMS log for `UnreadableEventException` or a database error, fix the cause, then redrive
   (`aws sqs start-message-move-task`). Replaying is safe: statuses only move forward.
6. If a test or E2E run caused the spike, that is the **newsletter super-GAU** class of incident
   (2026-07-01). Both walls exist now (Playwright `@sends-real-email` guard and the backend
   `EmailContentTestMarker` send-block) — verify which one was bypassed.

### Mail Center: notification instead of forwarding (Story 16.6)

Admin setting `mailcenter.notify-instead-of-forward` lists the alias groups answered in the Mail
Center, comma or space separated: `moderator` (`batbern{N}-moderator@`), `replies` (unrecognized
mail and `replies+{token}@` replies at `replies@`), `contact` (`info@`, `events@`, `support@`;
Story 16.7, live since #1194). Empty or missing = forwarding as before.
It is read per mail, so a change applies to the next mail without a deploy. **The owner switches
each group on; nobody else does.** Attachment storage (up to 25 MB per file) is live, so switched
mail keeps its files.

- **Switch on:** Verwaltung (Administration) → tab Settings → "Mail Center: notice instead of
  forwarding", tick the group (e.g. speaker mail = `moderator`), Save. The screen writes the setting
  `mailcenter.notify-instead-of-forward` (`PUT /api/v1/admin/settings/{key}`, organizer only).
  Send a test mail from a private address to the alias; the recipients should get "New mail … Open
  it here" and the conversation should appear on the event's mail page.
- **Switch back:** untick the group and Save. Nothing ticked = an empty value. The next mail is
  forwarded again.
- **Check first after switching on:** CloudWatch `BATbern/EmailForwarder` `NotificationsSent`
  (should rise with the alias traffic), `BATbern/MailCenter` `MailCenterIngestFailed` (should stay 0),
  the `replies-dlq` / `forwarding-dlq` alarms. With a group on, a storage failure is rethrown, so
  the mail is retried and dead-letters instead of being lost: redrive as in step 3 once the cause is
  fixed. `email_forwarding_log.outcome = 'notified'` marks mail that was notified, not forwarded.
- **What is never replaced:** organizer sends to `batbern{N}-speaker@`, `-participants@`,
  `batbern{N}@`, `partner@` and mail to `ok@` are always forwarded. Mail the Mail Center did not
  store (out-of-office, bounces, malformed) is forwarded as before.

---

## Identity and user sync

**Alarms:** `JIT-Provisioning-High-Failures`, `PreSignUp-Linking-Failures`,
`PreTokenGeneration-High-Latency`, `PostConfirmation-High-Latency`,
`User-Creation-High-Failures`, `User-Sync-High-Drift`, `Reconciliation-Orphaned-Users`,
`CustomEmailSender-Unhandled-Trigger`, `CustomEmailSender-Errors`

They publish to the shared alarm topic (#1005 fixed the former private topic with no subscribers).

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
5. `CustomEmailSender-Unhandled-Trigger`: Cognito asked `batbern-{env}-custom-email-sender-trigger`
   for an email type the handler does not implement (log line `Unhandled CustomEmailSender trigger`
   names the `triggerSource`). Cognito sends nothing itself, so that user got no email. Add the
   trigger to `infrastructure/lib/lambda/triggers/custom-email-sender.ts`. Until then, affected
   sign-ups stay `UNCONFIRMED` (#1141); confirm one by hand only when the person has proven they
   own the address, e.g. by writing to info@ from it with DMARC pass.
6. `CustomEmailSender-Errors`: the trigger threw, usually SES (sending paused, throttled) or KMS
   (code decryption). Check the trigger's log group and the SES account status; the user saw a
   failed sign-up, resend or password reset and can retry once it is fixed.

---

## Agent signing key

Agent tokens (agentic interaction) are signed by CUMS with an RS256 private key and verified by
gateway, CUMS, EMS and PCS with its public key. CDK creates both with the placeholder `none`, so a
deploy before the key exists starts every service with agent tokens off. Enabling them is a
one-time owner step after the SecretsStack is deployed.

| What | Where | Container env / Spring property | On |
|---|---|---|---|
| Private key, PKCS#8 PEM | secret `batbern/staging/agent/signing-private-key` | `AGENT_JWT_PRIVATE_KEY` / `agent.jwt.private-key` (signs in `/internal/agent-tokens`) | CUMS only |
| Public key, SPKI PEM | SSM String parameter `/batbern/staging/agent/jwt-public-key` | `AGENT_JWT_PUBLIC_KEY` / `agent.jwt.public-key` | gateway, CUMS, EMS, PCS |
| EMS caller secret (generated) | secret `batbern/staging/agent/ems-caller-secret` | `AGENT_CALLERS_EMS_SECRET` / `agent.callers.ems.secret` (`/internal/agent-grants`, `/internal/agent-eligibility`) | CUMS, EMS |
| Agent-service caller secret (generated) | secret `batbern/staging/agent/agent-service-caller-secret` | CUMS: `AGENT_CALLERS_AGENT_SERVICE_SECRET` / `agent.callers.agent-service.secret` (`/internal/agent-grants`, `/internal/agent-tokens`); agent-service: `AGENT_CUMS_CALLER_SECRET` / `agent.cums.caller-secret` (presents it on both calls; blank or `none`: every run is handed to the organizers with `CALLER_SECRET_MISSING`) | CUMS, agent-service |

**Turn agent tokens on:**

1. Deploy first. Every service logs
   `Agent tokens are off: agent.jwt.public-key holds the placeholder none` and
   `JWT decoder accepts token kinds [...]` without `AGENT`.
2. Optional preview, no AWS call: `scripts/deploy/create-agent-signing-key.sh --dry-run`.
3. Run `scripts/deploy/create-agent-signing-key.sh`. It uses profile `batbern-staging` (override
   with `AGENT_KEY_AWS_PROFILE`) in `eu-central-1`, checks that both values are still `none`,
   writes the private key to the secret, then the public key to the parameter, reads the parameter
   back, and prints the public key. The private key is never printed and never stored locally.
4. Force a new deployment of the four services; the script prints the exact
   `aws ecs update-service --force-new-deployment` loop. ECS reads secrets at task start only.
5. Check each service's start log for `JWT decoder accepts token kinds`: `[COGNITO, WATCH, AGENT]`
   for company-user-management, event-management and api-gateway, `[COGNITO, AGENT]` for
   partner-coordination. company-user-management must no longer log
   `Agent token minting is off`.

While the private key is `none` (or unset, blank), CUMS starts normally and answers every valid,
allowed `POST /internal/agent-tokens` with 503 `AGENT_SIGNING_KEY_MISSING` (logged
`Agent token refused: ... code=AGENT_SIGNING_KEY_MISSING`); a private key that is not an RSA PKCS#8
key fails CUMS startup with `agent.jwt.private-key is not an RSA private key`.

**The script refuses** when either value is not `none`: a key is already there. `--force` replaces
it; every agent token signed with the old key stops verifying once the services restart.

**What resets the key to `none`:** a CDK change to the properties of the secret
`AgentSigningPrivateKey` or the parameter `AgentJwtPublicKey` in `secrets-stack.ts` (description,
name, value, or tags of the SecretsStack), or replacing the SecretsStack. CloudFormation then writes
the template value `none` back and agent tokens go off at the next task start. Fix: re-run the
script with `--force`, then step 4. Ordinary deploys do not touch the values.

Rotation is not automated.

---

## Anthropic API key

agent-service calls the model through its Anthropic adapter (agent runtime story 3). The key lives
in the secret `batbern/staging/agent/anthropic-api-key` (AWS-managed key), injected into the
agent-service task only as `AGENT_ANTHROPIC_API_KEY` (`agent.anthropic.api-key`). CDK creates it
with the placeholder `none`: agent-service starts with the adapter off and hands every run to the
organizers. Installing the key is a one-time owner step after the SecretsStack is deployed. It does
not switch the agent on: that stays `AGENT_ENABLED`.

**Install the key:**

1. Deploy first. agent-service logs
   `Model adapter none, model none: no key installed, runs are handed to the organizers`.
2. Optional check, no AWS call: `scripts/deploy/put-anthropic-api-key.sh --dry-run` (reads the key
   and checks its shape; prints the steps, never the key).
3. Run `scripts/deploy/put-anthropic-api-key.sh` and paste the key at the hidden prompt, or pipe it
   in. It uses profile `batbern-staging` (override with `ANTHROPIC_KEY_AWS_PROFILE`) in
   `eu-central-1`, checks that the secret still holds `none`, writes the key through a umask-077
   temp file as `file://`, and forces a new deployment of the agent service (`--no-restart` skips
   that and prints the command).
4. Check the start log of the new task:
   `aws --profile batbern-staging --region eu-central-1 logs tail /aws/ecs/BATbern-staging/agent --since 30m --filter-pattern '"Model adapter"'`
   must show `Model adapter anthropic, model claude-sonnet-5-5, effort medium`.

**The script refuses** when the secret already holds a key; `--force` replaces it. `none` is refused
too unless `--force` is given: `echo none | scripts/deploy/put-anthropic-api-key.sh --force` switches
the adapter off again after the restart.

**Model and effort** are configuration, not part of the secret: `AGENT_ANTHROPIC_MODEL` (default
`claude-sonnet-5-5`) and `AGENT_ANTHROPIC_EFFORT` (default `medium`) on the task.

**What resets the key to `none`:** a CDK change to the properties of the secret
`AgentAnthropicApiKey` in `secrets-stack.ts` (description, name, value, or the SecretsStack's
tags), or replacing the SecretsStack. `anthropic-api-key.test.ts` pins those properties. Fix:
re-run the script with `--force`.

**While the model fails** (HTTP error, timeout, unreachable), the SDK retries 429, 5xx and
connection errors twice, then the run ends HANDED_TO_ORGANIZERS with `MODEL_UNAVAILABLE`; the
model call in `agent_model_calls` keeps the HTTP status and error type, never the response body.

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
