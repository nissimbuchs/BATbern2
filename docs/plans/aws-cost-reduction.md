# AWS cost reduction — getting from $310 to $150/month

**Status:** tiers 1–2 implemented (PR pending). Tier 3 prepared, purchase deliberately not executed.
**Baseline:** July 2026, $309.73. **Target:** $150/month.
**Measured:** 2026-08-20, account 188701360969, eu-central-1.

---

## 1. The event was not the expense

| Month | Total | Note |
|---|---|---|
| June 2026 | $308.66 | BATbern conference month |
| July 2026 | $309.73 | No event — **$1.07 more than the event month** |
| August 2026 | $186.31 | Partial, 20 of 31 days |
| August, projected | $288.78 | Linear extrapolation |

Three months within a dollar of each other, with and without a conference. **There is no
usage component to trim.** Every dollar is infrastructure running whether anyone visits or
not — which is good news, because fixed cost is the kind you can actually remove.

The management account (510187933511) adds ~$5.50/month of its own, so consolidated spend
runs about $315.

## 2. Where it goes

July, the cleanest full month. Four lines are 85% of the bill.

| Service | July | Share | What it actually is |
|---|---|---|---|
| ECS Fargate | $126.72 | 41% | 11 tasks, 24/7. $90.08 vCPU + $36.52 memory |
| CloudWatch | $51.68 | 17% | **$46.07 is Container Insights custom metrics.** Alarms $4.10, logs $1.50 |
| EC2 – Other | $40.46 | 13% | $38.69 is one NAT Gateway existing |
| RDS | $26.28 | 8% | Single db.t4g.micro, on demand |
| Tax | $23.19 | 7% | 8.09%, scales with everything above |
| Load Balancer | $20.11 | 6% | One ALB, the public entry point |
| VPC | $11.17 | 4% | 3 public IPv4 addresses |
| Everything else | $9.49 | 3% | ECR, Secrets, KMS, Route 53, S3 |

### Logs are not the problem

Log ingestion is $1.50/month and storage rounds to nothing — the largest group,
`api-gateway` at 4.7 GB, costs about $0.14/month. `VendedLog-Bytes` bills $0.00. The
CloudWatch line is **metrics, not logs**.

Worth tidying for consistency, not money: `api-gateway` retains 180 days while every other
service retains 30. That is where the 4.7 GB came from.

### The attendee service costs $17/month to answer nothing

`services/attendee-experience-service/src/main/java` contains **exactly one file**,
`AttendeeExperienceApplication.java`. No controllers, no `@RestController`, no endpoints.
The only gateway route pointing at it, `/api/v1/content`, resolves to no handler in any
service. Its sole log activity over 24 hours was Envoy sidecar chatter from Service
Connect. It ran two Fargate tasks around the clock.

### Container Insights was never wired up

$46.07/month for 163 custom metrics at $0.30 each. It was enabled deliberately —
`cluster-stack.ts` carried the comment *"provides RunningTaskCount, DesiredTaskCount
metrics needed by deployment alarms … essential for diagnosing stuck deployments"* — but
that intent was never completed. Verified before switching it off:

- no alarm in the repo references `RunningTaskCount` or `DesiredTaskCount`
- **no deployed alarm uses the `ECS/ContainerInsights` namespace at all**
- the one consumer, `EcsServiceAlarms`' OOM-kill alarm, is gated on a
  `containerInsightsEnabled` prop that **no stack passes**, so it is never created

What is genuinely lost is the CloudWatch console's Container Insights dashboards, which a
human may reach for while a deploy is stuck. That is a real if unmeasured benefit, and the
reason this is reversible in one stack deploy.

### AutoShutdown is an empty stack

`BATbern-staging-AutoShutdown` is deployed and contains a single resource:
`AWS::CDK::Metadata`. The construct opens with `if (envName !== 'development') return;`,
and development is local-only with no AWS account — so the scale-to-zero scheduler has
never run in any environment, while appearing in the stack list as cost control.

---

## 3. Plan

Ordered by what you give up. Each tier states the resulting monthly bill.

### Tier 1 — remove what is not doing anything → **$242/mo** ✅ implemented

| Action | Saving |
|---|---|
| AttendeeExperience parked at 0 tasks (`desiredCount: 0`, `disableAutoScaling: true`) | $17 |
| Container Insights `DISABLED` on the cluster | $46 |

Scaled to zero rather than deleted — Epic 7 will need the service, and a `desiredCount` is
one deploy to restore where a stack is not. Risk: none for the first; the second costs
console dashboards and the (never-created) OOM alarm.

**Parking a service breaks the post-deploy smoke gate**, and the first attempt at this hit
it: `scripts/ci/smoke-tests.sh` asserted all five domain services return 200 from
`/services/{name}/health`, so attendee-experience returned 503 and failed the deploy *after*
every stack had applied successfully. The stacks were live and correct; only the promotion
of `staging-current → staging-stable` was skipped.

The gate now reads desired counts from ECS and skips whatever is parked, rather than
hardcoding an exception that would rot the moment Epic 7 brings the service back. If the ECS
lookup is unavailable the gate checks everything — the fallback is deliberately the stricter
behaviour, so a missing lookup can never silently disable it.

### Tier 2 — stop paying for redundancy that does not exist → **$215/mo** ✅ implemented

| Action | Saving |
|---|---|
| Partner, Speaker, Company Management → 1 task (`desiredCount: 1` + `minCapacity: 1`) | $25 |

**`minCapacity` is the control that matters.** `desiredCount` applies only at service
creation; Application Auto Scaling owns the count thereafter. EventManagement is the proof
— created with `desiredCount: 2`, it has run one task for months because its `minCapacity`
is 1. Setting one without the other achieves nothing. Both are set so a deploy does not
start two tasks and scale one back in, which churns tasks and pays the cold-start cost in
issue #982 for no reason.

Two tasks were never real high availability here: the VPC is single-AZ, and services run
70% on Fargate Spot, which already produces 4–5 minute silent replacements.
**ApiGatewayService deliberately stays at 2** — it is the public entry point and the one
place a replacement gap is visible to visitors.

### Tier 3 — commit to a year → **~$191/mo** ⏸ prepared, not purchased

| Action | Saving | Source |
|---|---|---|
| Compute Savings Plan, 1yr, no upfront | ~$17 | AWS recommends $0.135/hr commitment for $25.82/mo at 20.1%; scaled down for the post-tier-2 footprint |
| RDS Reserved Instance, db.t4g.micro, 1yr, no upfront, Single-AZ | ~$4 | Offering `5059e9a5-25e2-4427-8b41-01b253536613`, $0.013/hr recurring vs ~$0.0186 on demand |

> **Correction to the first estimate.** The RDS reserved instance was initially estimated at
> $8/month. The actual offering is $0.013/hr against roughly $0.0186 on demand — about
> **$4/month**, because most of the $26.28 RDS line is storage and backup, not instance
> hours. Tier 3 therefore saves ~$21, not ~$23.

**Do not purchase yet, and this is the whole reason tier 3 is a separate tier.** AWS's
recommendation of $0.135/hr is computed from a 7-day lookback of the *current* 11-task
footprint. Tiers 1 and 2 remove roughly a third of it. Buying now commits to capacity that
is about to be deleted, and a Savings Plan cannot be resized or cancelled for a year.

Sequence: deploy tiers 1–2 → let the footprint run **7 days minimum** → re-run the
recommendation → purchase against the new baseline.

```sh
# after 7+ days on the reduced footprint
AWS_PROFILE=batbern-staging aws ce get-savings-plans-purchase-recommendation \
  --region us-east-1 --savings-plans-type COMPUTE_SP --term-in-years ONE_YEAR \
  --payment-option NO_UPFRONT --lookback-period-in-days SEVEN_DAYS
```

### Tier 4 — replace the managed NAT → **~$153/mo** ❌ not recommended now

NAT Gateway → t4g.nano NAT instance turns $38.69/month of hourly charge into roughly $3,
plus about $4 from releasing a public IPv4 address if the ALB subnet layout allows.

**This is the real trade on the page.** A NAT instance is a single point of failure you
patch yourself; if it dies, every outbound call — ECR pulls, SES, Cognito — stops. VPC
endpoints are the managed alternative but cost ~$7.30 each and four would be needed, so
they save nothing here.

---

## 4. Where this lands

| | Monthly | vs target |
|---|---|---|
| Baseline (July) | $309.73 | +$160 |
| After tiers 1–2 | **~$215** | +$65 |
| After tier 3 | **~$191** | +$41 |
| After tier 4 | ~$153 | +$3 |

**Tiers 1–3 do not reach $150.** Closing the last $41 means either tier 4's self-managed
NAT, or the structural answer nobody has costed: six always-on Spring Boot JVMs for a
platform serving 0–500 requests per five minutes is a lot of idle compute. Consolidating
the five domain services would dwarf every saving here — and it is an architecture
decision, not a cost tweak.

## 5. Deliberately not proposed

**Nightly scale-to-zero.** The largest single lever — roughly 40% of Fargate — and what the
dead AutoShutdown stack was built for. But `www.batbern.ch` is a public website; scaling
services to zero returns 503s from the ALB overnight, costing real visitors and search
ranking. That is a business call, not an engineering one.

**Downsizing RDS or the ALB.** The database is already the smallest instance class
available; the ALB is the single public entry point. Neither has slack.

**Cutting log retention.** Saves about $0.14/month. Align `api-gateway` to 30 days for
consistency, not for the money.

## 6. Related

- **#978** — there is no cost alarm of any kind. `AWS/Billing` only publishes in us-east-1
  and consolidated billing lives in the management account. Nothing would have told anyone
  the bill was double the target.
- **#982** — ECS task replacement causes 10–15 minutes of multi-second latency. Relevant
  here because task churn is not free, which is why `desiredCount` and `minCapacity` are
  set together above.

## 7. Method

Figures are unblended cost from Cost Explorer `get-cost-and-usage`, grouped by service and
usage type, cross-checked against live inspection of ECS, CloudWatch, EC2, RDS and
CloudFormation. Savings are arithmetic from July usage-type line items at current
on-demand rates. Savings Plan and Reserved Instance figures should be confirmed against
the AWS calculator before purchase.
