# Operations & Security

This document consolidates security implementation, performance standards, accessibility guidelines, and operational validation for the BATbern Event Management Platform.

## Security Requirements

### Frontend Security
- **CSP Headers**: Strict content security policy with CloudFront CDN support
  - `connect-src`: Allows connections to self, AWS Cognito (`*.amazoncognito.com`), the
    Cognito custom hosted-UI domain **`auth.batbern.ch`** (Story 12.9 — Amplify POSTs the
    OAuth code exchange to `https://auth.batbern.ch/oauth2/token`), CloudFront CDN
    (`*.cloudfront.net`), branded CDN domain (`cdn.batbern.ch`), and `api.batbern.ch`
  - `frame-src`: Allows Google Maps embeds (`https://www.google.com/maps/`) and Cloudflare Turnstile captcha (`https://challenges.cloudflare.com`)
  - `script-src`: Allows Cloudflare Turnstile script (`https://challenges.cloudflare.com`)
  - **Where configured — two places, easy to miss**: the **SPA document's** CSP is set by
    the frontend CloudFront `ResponseHeadersPolicy` (`infrastructure/lib/stacks/frontend-stack.ts`)
    — NOT by the gateway Java filters, which only cover API responses
    (`SecurityHeadersFilter.java` / `SecurityHeadersHandler.java`). Lesson from the
    2026-06 SSO token-exchange incident: the `auth.batbern.ch` switch broke federated
    login in production because only the gateway CSP was considered — any new outbound
    origin must be added to the **frontend-stack** `connect-src`.
- **COEP/CORP**: Cross-Origin-Embedder-Policy (COEP) header **removed** to allow third-party embeds (Google Maps). Cross-Origin-Resource-Policy (CORP) relaxed from `same-origin` to `cross-origin` for CDN asset delivery.
- **CDN SVG hardening** _(Story 12.12 review, finding #3)_: users can upload SVGs (profile
  pictures, logos) that `cdn.batbern.ch` serves as `image/svg+xml`; an SVG can carry
  `<script>`, which executes when its object URL is opened **top-level** — stored XSS on the
  cdn origin. The content distribution's `ResponseHeadersPolicy`
  (`batbern-content-cache-*`, `infrastructure/lib/stacks/storage-stack.ts`) therefore sends
  CSP `sandbox` (blocks script execution when the resource IS the document; `<img>`
  embedding of PNG/JPEG/WebP/SVG is unaffected) plus `X-Content-Type-Options: nosniff`.
- **XSS Prevention**: Input sanitization and output encoding
- **Secure Storage**: Encrypted localStorage for sensitive data

### Backend Security
- **Input Validation**: Comprehensive request validation
- **Rate Limiting**: API rate limiting per user and endpoint
- **CORS Policy**: Restrictive CORS configuration

### AI / LLM Security (OWASP-LLM, Story 15.9)
- The AI surface lives in `event-management-service` (event description, theme image, abstract
  analysis, trending topics, organizer-editable prompts), is ORGANIZER-gated, AI-feature-flagged
  (`AI_ENABLED`, default off), and **advisory** (output is reviewed before publish; it never drives
  authorization or control flow).
- Mitigations: a fixed non-editable **system guard** message on every chat call (LLM01/LLM08);
  delimiter neutralization in interpolated values; AI output treated as untrusted data (returned as
  string / parsed with safe defaults; React auto-escaping on render, never `dangerouslySetInnerHTML`);
  OpenAI key only in the `Authorization` header; `ai_generation_log` is **hash-only**.
- **Canonical mapping + status:** `docs/security/owasp-llm-agentic-checklist.md`.
- **CI:** advisory `.github/workflows/ai-security.yml` (`aiSecurityTest` `@Tag` subset +
  `scripts/ci/ai-prompt-secret-scan.sh`).

### Authentication Security
- **Token Storage**: Secure JWT storage with automatic refresh
- **Session Management**: Cognito-based session management
- **Password Policy**: Strong password requirements
- **Federated login (Google SSO, ADR-010 — live since 2026-06-04)**: "Continue with Google"
  via Cognito OIDC federation on the custom hosted-UI domain `auth.batbern.ch`
  (`cognito-stack.ts` `CustomUserPoolDomain`). Existing email/password accounts are linked
  transparently (`AdminLinkProviderForUser` in the `PreSignUp_ExternalProvider` trigger);
  brand-new Google users are JIT-provisioned as ATTENDEE and pass the ToS consent gate
  before reaching protected routes (see `06b-user-lifecycle-sync.md` Patterns 1b/F/C).
  The login button is gated on the runtime `features.sso` flag served by
  `GET /api/v1/config` — kill-switch: set `FEATURES_SSO_ENABLED=false` on the API gateway
  and restart (no frontend rebuild). Deactivated accounts are blocked request-time by the
  gateway `AccountActiveFilter` (federation never fires PreAuthentication).
- **Unconfirmed sign-up recovery**: a daily `CognitoConfirmationResendJob` re-sends a fresh Cognito
  confirmation code to accounts unconfirmed past a grace window (Cognito's sign-up code is fixed at
  24h and not configurable). The CUMS task role is granted `cognito-idp:ListUsers` +
  `cognito-idp:ResendConfirmationCode`, scoped to the user-pool ARN (the SPA client has no secret,
  so no `SecretHash` is needed).

## Monitoring & Alerting

### Alarm → GitHub issue lifecycle

CloudWatch alarms publish to the `batbern-{env}-alarms` SNS topic, which invokes the
`batbern-{env}-github-issues` Lambda (`infrastructure/lambda/github-issues-integration/`). It keeps
**one issue per alarm name**, keyed by the alarm name in the issue body:

| Alarm transition | Lambda behaviour |
|---|---|
| → `ALARM`, no issue exists | Creates an issue |
| → `ALARM`, issue open | Comments on the existing issue |
| → `ALARM`, issue closed | Reopens it and comments |
| → `OK` | Comments "alarm resolved" and **closes** it |
| → `INSUFFICIENT_DATA` | Ignored |

**The invariant: an `incident`-labelled issue that is open means the alarm is currently firing.**
That is what makes the tracker answerable to "is anything wrong right now?".

**Every alarm must therefore register BOTH actions:**

```ts
alarm.addAlarmAction(snsAction);
alarm.addOkAction(snsAction);   // without this the issue never closes
```

An alarm with `AlarmActions` and an empty `OKActions` files an issue that can never be closed by the
Lambda, because the recovery notification is never published — the close-on-OK code runs, it is just
never invoked. This was the state of every alarm except the budget alarm until 2026-08-12: #484
stayed open ~2 months and #648 ~3.5 weeks after their alarms had returned to `OK`, and their 44 and
100 comments were re-notifications piled onto records that had stopped being true (issue #956).

`monitoring-stack.test.ts` enforces the pairing: any alarm with alarm actions and no OK action fails
the build. A global count is not enough — it would pass if one alarm carried two OK actions and
another none — so the assertion is per-alarm.

Related: the ZAP security scan files issues through a different path (the scan action itself, not
this Lambda) and needs its own hygiene, because its dedup is broken upstream — see #905 and
`.github/workflows/security-scan.yml`.

### Cost, and what monitoring costs

Container Insights is **disabled** on the ECS cluster. It billed $46.07/month for 163
custom metrics at $0.30 each — 15% of the entire AWS bill — and nothing consumed it: no
alarm references `RunningTaskCount` or `DesiredTaskCount`, no deployed alarm uses the
`ECS/ContainerInsights` namespace, and the OOM-kill alarm that would use it is gated on a
`containerInsightsEnabled` prop no stack passes. Re-enable it in `cluster-stack.ts` and
deploy that stack alone if you need its console dashboards during an incident; metrics
resume within minutes.

Alarms themselves cost $4.10/month and logs $1.50 — both immaterial. When trimming
monitoring for cost, metrics are the line worth looking at, never logs.

Full analysis and the reduction plan: `docs/plans/aws-cost-reduction.md`. Note there is
currently **no cost alarm at all** (#978), which is why the bill ran at double its target
unnoticed.

### What is actually monitored, and where each alarm is defined

Alarms are defined **next to the resource they watch**, not centrally. The alarm topic is created by
`MonitoringStack` and passed outward into the stacks that own resources; identifiers never flow back,
because most of them do not exist yet when `MonitoringStack` is constructed (`bin/` line 172, versus
line 422 for `ApiGatewayServiceStack`).

| Watched | Alarms | Defined in |
|---|---|---|
| ALB (platform ingress) | `alb-5xx`, `alb-latency-p95`, `alb-availability`, `alb-unhealthy-targets` | `AlbAlarms`, instantiated by `ApiGatewayServiceStack` |
| Gateway API surface | `api-4xx-ratio` | `AlbAlarms`, fed by MetricFilters over the api-gateway log group |
| ECS, per service ×6 | `{Service}-High-CPU`, `-High-Memory`, `-Task-Failures`, `-EventBridge-Failures`, `-OOM-Kills` | `EcsServiceAlarms`, instantiated by each service stack |
| RDS | `database-connections`, `database-storage-low`, `database-cpu` | `AlarmConstruct`, in `MonitoringStack` |
| SES reputation | `bounce-rate-warning`, `bounce-rate-critical`, `complaint-rate-critical` | `MonitoringStack` |
| Bounce processing | `bounce-processing-dlq` | `MonitoringStack` |
| Cognito / user sync | 7 alarms | `UserSyncAlarms`, in `MonitoringStack` |
| Inbound email abuse | `emails-rejected` | `InboundEmailStack` |

RDS alarms are the exception that stays central: `DatabaseStack` runs *before* `MonitoringStack`, but
the instance identifier is deterministic (`batbern-{env}-postgres`, set explicitly as
`instanceIdentifier`), so the dimension can be built from a string without a cross-stack reference.

**There is deliberately no cost alarm.** `AWS/Billing` is only published in us-east-1, and
consolidated billing lives in the management account (510187933511) where finance already has
access. Tracked in #978.

### Log levels are owned by application.yml, not logback (#990)

`ch.batbern` is set in each service's `application.yml` and **deliberately nowhere else**. Spring
Boot applies `logging.level.*` *after* the logback configuration is loaded and overrides it, so a
`<logger name="ch.batbern">` element in `logback-spring.xml` is inert while looking authoritative.

That is not a hypothetical. The first attempt to turn api-gateway down from DEBUG edited only
`logback-spring.xml`, passed review, deployed — and DEBUG lines kept arriving. It was caught by
reading the live log group afterwards, not by any test.

Two production-affecting instances existed, and neither is visible from the code alone:

| service | setting | why it applied to production |
|---|---|---|
| `api-gateway` | `ch.batbern: DEBUG` in the default profile | hardcoded; ignored the `LOG_LEVEL=INFO` the task already sets |
| `company-user-management` | `ch.batbern: DEBUG` under the **staging** profile | the estate runs `SPRING_PROFILES_ACTIVE=staging` in the production account, so the `production: INFO` block below it is unreachable config |

The second is the more instructive one: it reads as correct. A reviewer sees `production: INFO`
and moves on. Only the deployment context reveals that block is never evaluated.

Both are now `${LOG_LEVEL:INFO}`. **The default is INFO and DEBUG is opt-in** — `${LOG_LEVEL:DEBUG}`
is safe only while every environment remembers to set the variable, which is the same implicit
coupling that hid this. Raise it with `LOG_LEVEL=DEBUG` on the task when debugging.

Guarded by `infrastructure/test/unit/log-level-hygiene.test.ts`, which walks every
`src/main/**/application*.yml`, resolves each YAML document's profile, and fails if a non-`local`,
non-`test` profile defaults `ch.batbern` to DEBUG. It also asserts no `<logger name="ch.batbern">`
survives in `logback-spring.xml`, because a second owner is what caused the failed fix. Verified by
reintroducing both defects and watching it fail on each.

### Test-runner worker caps are a memory constraint, not a tuning knob

Both JS test runners in this repository pin `maxWorkers: 3`:
`web-frontend/vite.config.ts` (`test.maxWorkers`) and `infrastructure/package.json` (jest).

Neither is a performance choice. Both runners default to roughly one worker per core, and the
development host `rack` has 16 cores against 29,877 MiB — about **1.8 GB of RAM per core before
anything else runs**. A per-core fan-out therefore cannot fit by construction.

Measured on 2026-08-23, when `vitest run --coverage` took the host down twice in 71 minutes.
During the first crash: 17 node processes (1 main + 16 workers) holding **36.6 GB** between
17.5 GB resident and 19.2 GB swapped, with `Free swap = 0kB`. The OOM killer chose
`user@1000.service`, so systemd SIGKILLed every session inside it at once. That is ~2.15 GB per
worker under coverage. Independently, `npx jest test/unit` in `infrastructure/` at its default
15 workers was killed with exit 137 mid-run — each worker synthesises CloudFormation stacks — and
completes cleanly at 3.

3 workers plus the main process is ~8.6 GB, inside the real budget (`user.slice` now enforces
`MemoryMax=14G`, less 3-5 GB of interactive baseline). 4 plus main is ~10.8 GB and too close.

Two things to know before changing these numbers:

- **Keep them absolute, not percentages.** `'25%'` is 4 workers on a 16-core box and 8 on a
  32-core one, which is the same failure on bigger hardware.
- **The host now kills instead of dying.** With the cgroup cap enforced, an over-parallel run no
  longer takes the machine down — a worker is SIGKILLed mid-test (exit 137,
  `constraint=CONSTRAINT_MEMCG`) and it presents as a **flaky test failure**. That is the more
  expensive outcome, because it looks like a defect in our code rather than a resource limit.

Effectively a no-op in CI: `ubuntu-latest` is a 4-vCPU runner where the default is already 3.
(Inferred from GitHub's documented runner spec, not measured on a runner.)

Verified, not assumed: with the cap in place, a 12-file vitest run peaked at exactly 3
concurrent fork workers, counted through `/proc`. An earlier measurement suggesting 5 was an
artefact of the sampler's own `grep` matching itself.

`minWorkers` is deliberately absent — it does not exist in vitest 4 (checked against the
installed 4.1.10 type definitions). `pool` is left alone too: `'forks'` is already the v4
default, so setting it changes nothing.

### Alarm to agent: the triage handoff (#986)

The chain is `alarm -> SNS batbern-{env}-alarms -> batbern-{env}-github-issues Lambda -> GitHub
issue`. That much already existed and works: it opens an issue, comments on re-trigger, and closes
on recovery. What was missing was the last hop.

`.github/workflows/claude.yml` was disabled on 2026-05-25 (`9f21453f`) and is re-enabled. It fires
on `issues: opened` only when the body or title contains `@claude`, so the Lambda now appends a
triage block containing that mention to every issue it **creates**.

Three properties are load-bearing.

**The mention is only on creation.** The re-trigger comment and the recovery comment must never
carry it. An oscillating alarm would otherwise start one agent run per cycle, and the alarm retired
in this same change managed six cycles in three days, two of them lasting 60 seconds. Tests pin all
three cases.

**`contents` stays `read` in the workflow's `permissions:` block.** A pull request against `develop`
invokes `deploy-staging.yml` and ships that branch to www.batbern.ch, unmerged and unreviewed. An
agent that "fixed" an alarm by opening a PR would be performing an unreviewed production deploy in
response to a CloudWatch metric. Without `contents: write` there is no branch to open one from. The
triage prompt also says not to; the permission is the half that does not depend on the model
complying. `issues: write` and `pull-requests: write` are granted so it can reply, which needs no
branch.

**`CLAUDE_TRIAGE_ENABLED=false`** on the Lambda drops the handoff and leaves the issue otherwise
intact. It is an environment variable rather than a bundled constant because that change is
effective immediately: an agent storm can be stopped from the console without deploying code. Same
convention as `FEATURES_SSO_ENABLED`.

Known limitation, accepted rather than solved: a self-healing alarm can close its issue while the
agent run is still going, so the run produces a comment on a closed issue. A poll-based trigger
(cron over `gh issue list --label incident --state open`) would skip those naturally, since an alarm
that clears inside the poll interval never wakes anything. That remains the better design and is not
built.

### An alarm on someone else's behaviour is not a signal (#986)

`alb-4xx` was retired on 2026-08-23. It watched `HTTPCode_Target_4XX_Count > 50` per 5 minutes and
paged six times in the preceding three days, self-resolving every time.

Measured over the window the last page cited (14:35-14:50 UTC), from 808 api-gateway request log
lines:

| bucket | requests | distinct paths |
|---|---|---|
| `/actuator/health` | 360 | 1 |
| `/api/v1/*` — all real traffic | 64 | 6 |
| neither | 384 | 186 |

The 384 were a webshell sweep against `api.batbern.ch`: `/gecko-new.php`, `/aa.php`,
`/wp-content/plugins/hellopress/wp_filemanager.php`, 186 distinct nonexistent `.php` paths in
fifteen minutes. Each is a 404 and each 404 is one `HTTPCode_Target_4XX_Count`. `api.batbern.ch`
resolves straight to the ALB with no CloudFront and no WAF, so nothing stands between a scanner
and a 404.

The number that alarm reported was therefore a property of the internet, not of BATbern, and no
threshold makes it actionable — raising it only chooses how large a scan has to be before it pages.
The lesson generalises past this one alarm: **an alarm must watch something we control.** A metric
that an anonymous third party can move at will is a noise generator, and a noisy alarm is worse
than an absent one because it trains its reader to ignore the channel.

The signal `alb-4xx` was reaching for — a deploy that starts rejecting real requests — is kept, as
`api-4xx-ratio`.

### `api-4xx-ratio`, and why it is a ratio

`ClientErrorMetricsFilter` in the api-gateway logs one `GATEWAY_API_REQUEST` line per request on a
path the gateway serves, and nothing at all for anything else. Two `logs.MetricFilter`s over that
marker publish `BATbern/Gateway ApiRequests` and `ApiClientErrors`, and the alarm compares them:

```
IF(requests >= 10, errors / requests * 100, 0) > 50%, for 3 of 3 periods
```

Three properties are deliberate.

**It counts only our own surface, and only authenticated traffic.** Neither the external scanner
sweep nor our own OWASP ZAP scan produces a log line, so neither enters either side of the
fraction. The credential gate was added after the alarm's first real firing (#995): the weekly
`Security Scan (OWASP ZAP)` against `api.batbern.ch` drove 59,039 unauthenticated 4xx between
03:35 and 03:50 UTC on 2026-08-24, took the ratio to 88%, and paged. A 401 with no
`Authorization` header is the security boundary working correctly; a 401 on a request that
carried a token is the regression worth waking someone for. Credential-less requests are excluded
from **both** sides — dropping them from the numerator alone would let a scan dilute the
denominator and mask a real auth break occurring during it. Accepted cost: a 4xx regression on a
public endpoint is invisible to this alarm. The denominator is explicitly *not* the ALB's `RequestCount`, which counts the
sweep and would therefore inflate during exactly the noise the alarm needs to see through.

**It is a ratio, not a count.** Measured `/api/` requests per 5-minute window over the 24h to
2026-08-23 15:00 UTC: 12-35 through the day, and 988 / 842 / 527 in the three windows from 02:40
while the nightly E2E suite runs. No constant survives a 30× swing — high enough for 02:40 is
unreachable at midday, which is a dead alarm and the #970 failure mode; low enough for midday pages
every night. The nightly suite's own 4xx peak measured 32 in a bin against 988 requests, about 3%,
so a ratio separates them and a count cannot.

**Both filter patterns require `@timestamp`.** The gateway's `logback-spring.xml` writes every event
into this log group **twice** — the `LogstashEncoder` JSON line via stdout and the ECS `awslogs`
driver, plus a plain-text `PatternLayout` line written directly by `ca.pjer.logback.AwsLogsAppender`
to the same group. Only the JSON rendering contains `@timestamp`, so requiring that term
deduplicates. Verified with `aws logs test-metric-filter` against both renderings: the request
pattern matches 2 of 4 sample events and the error pattern 1 of 4, with the plain-text duplicate and
a `SecurityHeadersFilter` DEBUG line correctly excluded. The double-write is itself a defect and is
tracked separately.

A side effect worth naming: **the gateway now has an access log.** It had none, which is why
attributing the 4xx spike above required an Insights query over DEBUG filter-chain chatter. Query
strings are stripped before logging, because token-credentialed endpoints (email verification,
unsubscribe, registration confirm) carry the credential there.

### A declared alarm is not a working alarm

Until 2026-08-19, nine of these alarms had **never evaluated a single datapoint** (#970). They
queried `AWS/ApiGateway` when traffic is served by an ALB, `AWS/ECS` without `ClusterName`,
`DBClusterIdentifier` against a single RDS instance, `AWS/EBS` with no volumes, and `AWS/Billing`
outside us-east-1. One of them — `high-availability` — additionally compared a *request count*
against `99.9` as though it were a percentage and treated missing data as breaching, so it reported
`ALARM` continuously for about ten months.

Nothing caught it, because nothing was looking at the right property:

- `Template.fromStack()` assertions pass against an alarm whose dimensions match no resource on
  earth. CloudFormation does not validate that a dimension resolves to anything.
- The E2E specs asserted `alarm.Threshold === 99.9`, which is true of a dead alarm — and did not
  run in any case (#979).

**The signal that distinguishes a live alarm from a dead one is `StateReason`.** CloudWatch reports
`"Unchecked: Initial alarm creation"` for an alarm that has never evaluated:

```sh
AWS_PROFILE=batbern-staging aws cloudwatch describe-alarms --region eu-central-1 \
  --query 'MetricAlarms[?contains(StateReason, `Unchecked`)].[AlarmName,Namespace,MetricName]' \
  --output text
```

Anything that command returns, hours after a deploy, is watching nothing. When adding an alarm,
check it there once rather than trusting a green unit test. `alert-rules.test.ts` now also guards the
specific dimension mistakes above, and no alarm anywhere may use `treatMissingData: BREACHING` — a
permanently red alarm is worse than no alarm, because it teaches everyone to ignore the channel.

### The deploy warmup is real latency, and the latency alarm must outlast it

`alb-latency-p95` gates its expression on request volume:

```
IF(requests >= 20, latency, 0)
```

That floor is independently correct — quiet windows carry 1–6 requests, where a p95 is essentially
one sample — but **it is not what keeps the alarm quiet**, and it is worth being precise about why,
because the first attempt at this got it wrong.

`alb-latency-p95` sent five notifications on 2026-08-19, the day it shipped. The initial diagnosis
was "low traffic, so p95 is one cold-start request". The measurements say otherwise. Across two ECS
task replacements, per 5-minute window:

| requests | p95 | | requests | p95 |
|---|---|---|---|---|
| 25 | 3.7081 s | | 35 | 4.7058 s |
| 116 | 1.9862 s | | 220 | 1.1722 s |
| 256 | 0.8424 s | | 533 | 0.4328 s |
| 386 | 0.5252 s | | | |
| 45 | 0.0057 s | | | |

Volume **ramps** while latency **decays** — a JVM warming up under real load. Every breaching window
carries 25–533 requests and clears the 20-request floor, so the volume gate would not have suppressed
a single one of those five emails. This is genuine, user-visible latency: on the order of 800 real
requests take seconds after each task replacement, for roughly 10–15 minutes, before settling to a
2–25 **millisecond** baseline.

So the alarm is not wrong and the metric is not noise — the alarm simply has to outlast a condition
that resolves itself. It requires **5 breaching periods out of 6 (25 minutes)**. The observed warmups
breached for 4 and 2 periods respectively; latency that is genuinely stuck still fires. The trade is
explicit: a real regression is detected ~25 minutes later than it otherwise would be, in exchange for
not paging on every deploy.

**Do not "fix" a noisy alarm by raising its threshold.** That hides genuine latency at every traffic
level and produces a signal that looks like coverage and is not — the same failure as the dead alarms
above. Change *when* it speaks, not *what it considers acceptable*.

**The warmup itself is unfixed.** It is now quantified rather than invisible, and tracked separately.
The alarm change buys quiet; it does not make the platform fast after a deploy.

## Cost Optimizations (2026-03)

The following cost optimizations were applied to the production (staging) environment:

| Change | Savings | Rationale |
|--------|---------|-----------|
| **Container Insights V2 disabled** | ~$48/month | Not justified for current low traffic volume |
| **RDS backup retention reduced** 14 → 7 days | ~$4/month | 7 days sufficient for a low-traffic community platform |
| **Bastion auto-stop on tunnel close** | ~$2/month | `start-db-tunnel.sh` now automatically stops the bastion EC2 instance when the SSH tunnel is closed |

**`isProd` bug fix (2026-03-22):** The `cluster-stack.ts` and `incident-management-stack.ts` stacks were incorrectly using `envName === 'production'` to determine production behavior. Since the consolidated environment uses `envName: 'staging'` but serves production traffic, this was changed to use `config.isProduction` (which is `true`). This ensures production-grade behavior (alerts, scaling) is correctly applied.

## CDN Image Resizing (Lambda@Edge)

`cdn.batbern.ch` resizes images on the fly (`?w=&h=&fit=`) via a Lambda@Edge function
(`infrastructure/lib/stacks/storage-stack.ts` → `ImageResizeFn`, source in
`lib/lambda/image-resize/`). Two operational properties matter more than they look.

### The bundle must be byte-deterministic

`sharp` cannot be bundled by esbuild (native `.node` binary), so it stays external and the whole
production `node_modules` is copied into the artifact. If that artifact changes, CDK publishes a **new
Lambda@Edge version**, and CloudFront must re-replicate the function to every edge location. During
that window resize requests return **503**, and the old version cannot be deleted until it drains —
which is what this line in a deploy log means:

```
edge-lambda-stack-… | DELETE_FAILED (skipped) | AWS::Lambda::Version
  | ImageResizeFnCurrentVersion… (this will take a few minutes to recover)
```

So a non-deterministic bundle means **every** deploy pays a replication window, whether or not the
image code changed. That happened until 2026-08-12: `npm` resolved the musl variant on some builds
and not others, so the artifact alternated between two sizes (measured on deployed versions 74–80):

| bundle | contents |
|---|---|
| 15,763,107 B | `@img/{sharp-linux-x64, sharp-libvips-linux-x64, sharp-wasm32, colour}` |
| 24,050,269 B | the same **plus** `@img/{sharp-linuxmusl-x64, sharp-libvips-linuxmusl-x64}` |

The bundling step now deletes the musl variants explicitly. Lambda runs Amazon Linux (**glibc**), so a
musl binary can never load there — it is dead weight, not a fallback. Both variants always contained
the glibc pair `sharp` actually loads, so neither was ever "missing sharp".

**If you change anything under `lib/lambda/image-resize/` or its bundling command, check that two
consecutive deploys publish the same `CodeSize`:**

```bash
AWS_PROFILE=batbern-staging aws lambda list-versions-by-function --region us-east-1   --function-name <edge-lambda-stack-…-ImageResizeFn…>   --query 'Versions[].{Ver:Version,Size:CodeSize,Modified:LastModified}' --output table
```

Lambda@Edge lives in **us-east-1** regardless of where the rest of the platform runs, and its logs
are written in the region nearest the viewer — not in eu-central-1.

### Reading a CDN resize failure

The post-deploy smoke test (`scripts/ci/smoke-tests.sh`, Test 4) asserts a resize returns
`200 image/webp`, retrying for ~2 minutes to ride out the replication window above. When it does
fail, the response shape says which bug it is:

| Observed | Meaning |
|---|---|
| `503` / no response | the function crashed at **init** — a native module genuinely absent from the bundle |
| `200` + `image/jpeg` | the function ran, `sharp` failed, and it fell back to passing the original through |
| `200` + `image/webp` | healthy |

These are different faults with different fixes, and conflating them sends you looking for a
packaging bug when the real answer is "wait for replication". The test randomises `w`/`h` on every
attempt because CloudFront caches error responses briefly — reusing one cache key can return a
cached 503 for a whole retry loop and hide a recovery.

## Performance Benchmarks and SLAs

### Service Level Agreements (SLAs)

**Platform Availability SLAs:**

| Service Tier | Availability Target | Monthly Downtime | Response Time (P95) | Error Rate |
|--------------|--------------------|--------------------|-------------------|------------|
| **Production** | 99.9% | < 43 minutes | < 200ms | < 0.1% |
| **Development (local)** | N/A | N/A | N/A | N/A |

**Business SLA Commitments:**
- **Event Registration**: 99.5% availability during registration periods
- **Speaker Portal**: 99.0% availability during submission deadlines
- **Partner Analytics**: 98.0% availability for monthly reporting
- **Content Discovery**: 99.9% availability for public access

### Detailed Performance Benchmarks

**Frontend Performance Standards:**

```yaml
# Web Vitals Targets (Real User Monitoring)
Core Web Vitals:
  First Contentful Paint (FCP):
    target: "< 1.5s"
    warning: "> 2.0s"
    critical: "> 3.0s"
    measurement: "75th percentile"

  Largest Contentful Paint (LCP):
    target: "< 2.5s"
    warning: "> 3.0s"
    critical: "> 4.0s"
    measurement: "75th percentile"

  First Input Delay (FID):
    target: "< 100ms"
    warning: "> 200ms"
    critical: "> 300ms"
    measurement: "75th percentile"

  Cumulative Layout Shift (CLS):
    target: "< 0.1"
    warning: "> 0.15"
    critical: "> 0.25"
    measurement: "75th percentile"

  Interaction to Next Paint (INP):
    target: "< 200ms"
    warning: "> 300ms"
    critical: "> 500ms"
    measurement: "75th percentile"

# Resource Performance
Bundle Performance:
  initial_bundle_size:
    target: "< 250KB gzipped"
    warning: "> 400KB gzipped"
    critical: "> 500KB gzipped"

  total_bundle_size:
    target: "< 1MB gzipped"
    warning: "> 1.5MB gzipped"
    critical: "> 2MB gzipped"

  code_splitting_ratio:
    target: "> 80% lazy loaded"
    warning: "< 70% lazy loaded"
    critical: "< 50% lazy loaded"

# Network Performance
Network Efficiency:
  time_to_interactive:
    target: "< 3s"
    warning: "> 5s"
    critical: "> 8s"

  lighthouse_performance_score:
    target: "> 90"
    warning: "< 80"
    critical: "< 70"

  total_blocking_time:
    target: "< 300ms"
    warning: "> 600ms"
    critical: "> 1000ms"
```

**Backend Service Performance Benchmarks:**

```yaml
# API Gateway Performance
API Gateway:
  response_time_p50: "< 50ms"
  response_time_p95: "< 200ms"
  response_time_p99: "< 500ms"
  throughput: "> 1000 req/min"
  error_rate: "< 0.1%"

# Domain Service Performance
Event Management Service:
  endpoints:
    "GET /events":
      p95_response_time: "< 150ms"
      throughput: "> 500 req/min"
      cache_hit_ratio: "> 80%"

    "POST /events":
      p95_response_time: "< 300ms"
      throughput: "> 100 req/min"
      validation_time: "< 50ms"

Speaker Coordination Service:
  endpoints:
    "GET /speakers":
      p95_response_time: "< 100ms"
      throughput: "> 300 req/min"
      database_query_time: "< 30ms"

    "POST /speakers/invitations":
      p95_response_time: "< 250ms"
      throughput: "> 50 req/min"
      email_delivery_sla: "< 5 minutes"

Partner Coordination Service:
  endpoints:
    "GET /partners/{id}/topic-votes":
      p95_response_time: "< 200ms"
      throughput: "> 50 req/min"
    "POST /topics/voting":
      p95_response_time: "< 300ms"
      throughput: "> 30 req/min"

Attendee Experience Service:
  endpoints:
    "GET /search":
      p95_response_time: "< 300ms"
      throughput: "> 500 req/min"
      search_relevance_score: "> 85%"
```

**Database Performance Standards:**

```yaml
# PostgreSQL Performance
Database Performance:
  connection_pool_utilization: "< 70%"
  query_performance:
    simple_selects: "< 10ms"
    complex_joins: "< 100ms"
    aggregations: "< 200ms"
    full_text_search: "< 500ms"

  indexing_efficiency:
    index_hit_ratio: "> 95%"
    table_scan_ratio: "< 5%"

  replication_lag: "< 100ms"

# Caffeine In-Memory Cache Performance
Cache Performance:
  memory_utilization: "< 80%"
  hit_ratio: "> 95%"
  avg_response_time: "< 1ms (in-process, no network latency)"
  eviction_rate: "< 5%"
```

## Accessibility Implementation Guidelines

### Accessibility Standards and Compliance

**Compliance Targets:**
- **WCAG 2.1 Level AA**: Primary compliance standard for all user interfaces
- **Swiss Accessibility Laws**: Compliance with Swiss federal accessibility requirements
- **Section 508**: US accessibility standards for government compatibility
- **EN 301 549**: European accessibility standard for ICT procurement

**Accessibility Audit Schedule:**
- **Automated Testing**: Daily during development with axe-core
- **Manual Testing**: Weekly accessibility reviews during sprint cycles
- **Expert Audit**: Quarterly professional accessibility audits
- **User Testing**: Semi-annual testing with users who have disabilities

### Frontend Accessibility Architecture

#### Core Accessibility Framework

```typescript
// Accessibility Provider Configuration
import { AccessibilityProvider } from '@/providers/AccessibilityProvider';

interface AccessibilityConfig {
  reducedMotion: boolean;
  highContrast: boolean;
  fontSize: 'small' | 'medium' | 'large' | 'extra-large';
  screenReader: boolean;
  keyboardNavigation: boolean;
}

const AccessibilityProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [config, setConfig] = useState<AccessibilityConfig>({
    reducedMotion: window.matchMedia('(prefers-reduced-motion: reduce)').matches,
    highContrast: window.matchMedia('(prefers-contrast: high)').matches,
    fontSize: 'medium',
    screenReader: false,
    keyboardNavigation: true
  });

  useEffect(() => {
    // Detect screen reader usage
    const detectScreenReader = () => {
      const hasScreenReader = window.speechSynthesis?.speaking ||
                             navigator.userAgent.includes('NVDA') ||
                             navigator.userAgent.includes('JAWS');
      setConfig(prev => ({ ...prev, screenReader: hasScreenReader }));
    };

    detectScreenReader();
  }, []);

  return (
    <AccessibilityContext.Provider value={{ config, setConfig }}>
      <div
        className={clsx(
          config.highContrast && 'high-contrast',
          config.reducedMotion && 'reduced-motion',
          `font-size-${config.fontSize}`
        )}
      >
        {children}
      </div>
    </AccessibilityContext.Provider>
  );
};
```

#### Semantic HTML and ARIA Implementation

**Accessible Form Components:**

```typescript
interface AccessibleFormFieldProps {
  id: string;
  label: string;
  error?: string;
  helpText?: string;
  required?: boolean;
  children: React.ReactNode;
}

const AccessibleFormField: React.FC<AccessibleFormFieldProps> = ({
  id,
  label,
  error,
  helpText,
  required,
  children
}) => {
  const helpTextId = helpText ? `${id}-help` : undefined;
  const errorId = error ? `${id}-error` : undefined;
  const describedBy = [helpTextId, errorId].filter(Boolean).join(' ');

  return (
    <div className="form-field">
      <label
        htmlFor={id}
        className={clsx('form-label', required && 'required')}
      >
        {label}
        {required && (
          <span aria-label="required" className="required-indicator">
            *
          </span>
        )}
      </label>

      {React.cloneElement(children as React.ReactElement, {
        id,
        'aria-describedby': describedBy || undefined,
        'aria-invalid': error ? 'true' : 'false',
        'aria-required': required || undefined
      })}

      {helpText && (
        <div id={helpTextId} className="help-text">
          {helpText}
        </div>
      )}

      {error && (
        <div
          id={errorId}
          className="error-text"
          role="alert"
          aria-live="polite"
        >
          {error}
        </div>
      )}
    </div>
  );
};
```

**Accessible Data Table:**

```typescript
interface AccessibleTableProps<T> {
  data: T[];
  columns: TableColumn<T>[];
  caption: string;
  sortable?: boolean;
  selectable?: boolean;
}

const AccessibleTable = <T extends Record<string, any>>({
  data,
  columns,
  caption,
  sortable = false,
  selectable = false
}: AccessibleTableProps<T>) => {
  const [sortConfig, setSortConfig] = useState<{ key: string; direction: 'asc' | 'desc' } | null>(null);
  const [selectedRows, setSelectedRows] = useState<Set<string>>(new Set());

  return (
    <table
      role="table"
      aria-label={caption}
      className="accessible-table"
    >
      <caption className="sr-only">{caption}</caption>

      <thead>
        <tr role="row">
          {selectable && (
            <th scope="col" role="columnheader">
              <input
                type="checkbox"
                aria-label="Select all rows"
                onChange={(e) => {
                  if (e.target.checked) {
                    setSelectedRows(new Set(data.map((_, index) => index.toString())));
                  } else {
                    setSelectedRows(new Set());
                  }
                }}
                checked={selectedRows.size === data.length && data.length > 0}
              />
            </th>
          )}

          {columns.map((column) => (
            <th
              key={column.key}
              scope="col"
              role="columnheader"
              aria-sort={
                sortConfig?.key === column.key
                  ? sortConfig.direction === 'asc' ? 'ascending' : 'descending'
                  : sortable ? 'none' : undefined
              }
            >
              {sortable ? (
                <button
                  onClick={() => handleSort(column.key)}
                  className="sort-button"
                  aria-label={`Sort by ${column.header}`}
                >
                  {column.header}
                </button>
              ) : (
                column.header
              )}
            </th>
          ))}
        </tr>
      </thead>

      <tbody>
        {data.map((row, index) => (
          <tr key={index} role="row">
            {selectable && (
              <td role="gridcell">
                <input
                  type="checkbox"
                  aria-label={`Select row ${index + 1}`}
                  checked={selectedRows.has(index.toString())}
                  onChange={(e) => {
                    const newSelected = new Set(selectedRows);
                    if (e.target.checked) {
                      newSelected.add(index.toString());
                    } else {
                      newSelected.delete(index.toString());
                    }
                    setSelectedRows(newSelected);
                  }}
                />
              </td>
            )}

            {columns.map((column) => (
              <td key={column.key} role="gridcell">
                {column.render ? column.render(row[column.key], row) : row[column.key]}
              </td>
            ))}
          </tr>
        ))}
      </tbody>
    </table>
  );
};
```

### Keyboard Navigation Standards

```typescript
// Global keyboard navigation handler
const useKeyboardNavigation = () => {
  useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      // Skip navigation for form inputs
      if (['INPUT', 'TEXTAREA', 'SELECT'].includes((event.target as Element).tagName)) {
        return;
      }

      switch (event.key) {
        case 'Tab':
          // Let browser handle tab navigation
          break;

        case 'Escape':
          // Close modals, dropdowns, etc.
          closeAllPopovers();
          break;

        case 'Enter':
        case ' ':
          // Activate focused interactive elements
          if (event.target instanceof HTMLElement &&
              event.target.getAttribute('role') === 'button') {
            event.preventDefault();
            event.target.click();
          }
          break;

        case 'ArrowDown':
        case 'ArrowUp':
          // Handle vertical navigation in menus
          handleVerticalNavigation(event);
          break;

        case 'Home':
        case 'End':
          // Navigate to first/last element
          handleHomeEndNavigation(event);
          break;
      }
    };

    document.addEventListener('keydown', handleKeyDown);
    return () => document.removeEventListener('keydown', handleKeyDown);
  }, []);
};
```

## Architect Validation Report

### Executive Summary

**Overall Architecture Readiness: HIGH**

**Project Type:** Full-stack application with React frontend and Java/Spring Boot microservices backend

**Critical Strengths:**
- Comprehensive DDD-based microservices architecture aligned with business domains
- Clear separation of concerns with well-defined bounded contexts
- Detailed API specifications with role-based security model
- Robust AWS cloud infrastructure with Swiss compliance considerations
- Comprehensive deployment and CI/CD strategy

**Key Risks Identified:**
- Complex multi-repository structure may increase coordination overhead
- Heavy reliance on external systems (AWS, Hostpoint DNS) creates potential failure points
- Advanced features (AI/ML recommendations) lack detailed implementation guidance

### Section Analysis Results

| Section | Pass Rate | Status | Notes |
|---------|-----------|--------|-------|
| Requirements Alignment | 95% | ✅ PASS | All functional requirements covered, minor gaps in edge case handling |
| Architecture Fundamentals | 90% | ✅ PASS | Strong DDD approach, clear component boundaries |
| Technical Stack & Decisions | 85% | ✅ PASS | Justified technology choices, specific versions defined |
| Frontend Design & Implementation | 80% | ⚠️ WARN | Good coverage, needs more component specification details |
| Resilience & Operational Readiness | 75% | ⚠️ WARN | Monitoring strategy needs expansion |
| Security & Compliance | 85% | ✅ PASS | Strong security model, GDPR compliance addressed |
| Implementation Guidance | 80% | ✅ PASS | Good coding standards, testing strategy well-defined |
| Dependency Management | 90% | ✅ PASS | Clear dependency mapping, versioning strategy |
| AI Agent Implementation Suitability | 85% | ✅ PASS | Well-structured for AI implementation |
| Accessibility Implementation | 70% | ⚠️ WARN | Basic coverage, needs more detailed guidance |

### Risk Assessment

**High Priority Risks:**

1. **Multi-Repository Coordination Complexity**
   - Risk: Development team coordination overhead, integration challenges
   - Mitigation: Implement shared CI/CD pipelines, clear interface contracts
   - Timeline Impact: +2-3 weeks for proper tooling setup

2. **External DNS Provider Integration**
   - Risk: Complex Route53 alternative with Hostpoint, potential deployment issues
   - Mitigation: Early proof-of-concept for DNS automation, fallback manual processes
   - Timeline Impact: +1 week for DNS integration testing

3. **Performance Under Load**
   - Risk: Multiple microservices may create latency chains
   - Mitigation: Implement circuit breakers, comprehensive load testing
   - Timeline Impact: +2 weeks for performance optimization

**Medium Priority Risks:**

4. **AI/ML Feature Implementation**
   - Risk: Vague specifications for intelligent recommendations and search
   - Mitigation: Phase 2 implementation with detailed research phase
   - Timeline Impact: No immediate impact (deferred feature)

5. **Swiss Compliance Complexity**
   - Risk: GDPR and Swiss data protection requirements may be underestimated
   - Mitigation: Legal review of data handling, documented compliance procedures
   - Timeline Impact: +1 week for compliance verification

### Recommendations

**Must-Fix Before Development:**
1. Expand monitoring and observability section with specific tools and metrics
2. Add detailed component specifications for frontend architecture
3. Create proof-of-concept for Hostpoint DNS integration
4. Define specific performance benchmarks and SLAs

**Should-Fix for Better Quality:**
1. Add more comprehensive accessibility implementation guidelines
2. Expand error handling patterns with specific code examples
3. Define detailed AI/ML architecture for future phases
4. Add visual architecture diagrams using C4 model

**Nice-to-Have Improvements:**
1. Consider adding GraphQL layer for flexible frontend data fetching
2. Evaluate event sourcing for audit trail requirements
3. Research serverless options for cost optimization
4. Add chaos engineering practices for resilience testing

### AI Implementation Readiness Assessment

**Readiness Level: HIGH**

**Strengths for AI Implementation:**
- Clear bounded context separation allows focused AI agent work
- Well-defined interfaces and API contracts
- Consistent naming conventions and patterns
- Comprehensive testing strategy provides safety net

**Areas Needing Additional Clarification:**
1. Component-specific implementation patterns need more examples
2. Complex business logic workflows need step-by-step breakdowns
3. Integration testing scenarios require more detail

**Complexity Hotspots to Address:**
- Speaker workflow state machine implementation
- Multi-role permission enforcement
- Progressive publishing engine logic
- Real-time notification system

### Final Validation Summary

The BATbern Event Management Platform architecture demonstrates **HIGH readiness** for implementation. The DDD-based microservices approach is well-suited for the complex event management domain, and the chosen technology stack is appropriate for the scale and requirements.

The architecture successfully addresses all major functional requirements from the PRD and provides a solid foundation for the revolutionary transformation from static website to dynamic event management platform.

**Recommendation: PROCEED WITH DEVELOPMENT** with attention to the identified must-fix items during Sprint 0 setup phase.