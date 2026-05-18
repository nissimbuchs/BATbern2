# Registration Abuse Defense Plan

**Status:** Tier 1 approved 2026-05-18, in progress. Tier 2/3 awaiting prioritization.
**Owner:** Nissim Buchs
**Created:** 2026-05-18
**Drafted by:** Claude (during email-forwarding incident investigation)
**Triggering incident:** SES bounce-rate warning + organizer receiving fan-out of a calendar-acceptance reply on 2026-05-18

---

## Reality check — the "attacker" is us

The 1152 POSTs/5-min against `/api/v1/events/BATbern25/registrations` and the ~780 SES sends to `zaproxy@example.com` are not external — they are **our own scheduled OWASP ZAP scan**.

- `.github/workflows/security-scan.yml` runs every Monday at 03:00 UTC (`cron: '0 3 * * 1'`) and on `workflow_dispatch`.
- The active API scan uses the OpenAPI specs in `docs/api/*.openapi.yml` and posts test payloads (including `zaproxy@example.com`) against `https://api.batbern.ch`.
- Today is Monday 2026-05-18. The scan ran, fuzzed every public POST, and consumed SES quota in the process.

That said — **ZAP is not lying**. The endpoints it abused really are unprotected, and a real attacker could do the same (or worse). The job is therefore: fix the underlying exposure, AND tune the scan so it does not consume our SES quota on every run.

The Cloudflare Turnstile fail-open is **deliberate** and must stay. From commit `4079d394` (2026-05-06):
> Users behind corporate firewalls or with ad blockers cannot load the Cloudflare Turnstile widget, so no X-Turnstile-Token header is sent. The previous behaviour (403 on missing token) blocked these users entirely.

Most BATbern attendees register from corporate networks. Flipping Turnstile to fail-closed would break primary signup. Defenses must work without it.

---

## Threat model (after the realignment)

| Vector | Impact | Reachable today via |
|--------|--------|---------------------|
| Scripted POST flood on `/registrations` with throwaway emails | SES quota burn, bounce reputation drop, possible SES sending-pause | Our own ZAP, any anon attacker |
| Scripted POST flood on `/newsletter/subscribe` | SES quota burn (smaller — confirm-email pattern) | Anon attacker |
| Scripted POST flood on `/registrations/deregister/by-email` | Email-spam abuse against arbitrary registered users | Anon attacker (`DeregistrationController.java:32-34` has the literal TODO) |
| Scripted POST flood on speaker-invite / batch-import | Lower — those endpoints are `hasRole('ORGANIZER')` | Insider only |

SES current state (2026-05-18 ~13:30 UTC):
- 24h send count: 836
- 24h bounce count: ~9
- 15-day rolling bounce rate: **4.0% (alarm at 3%)**
- Suppression list: 406 entries (mostly stale corporate addresses from May 4 bulk send)
- SES throttles sending at 5% bounce-rate; pauses at 10%

We are one bad scan away from a sending outage on the public launch week.

---

## Why the existing protections didn't catch this

1. **Turnstile is fail-open by design** (see commit `4079d394`). ZAP doesn't send the token, request passes through.
2. **`RateLimitingFilter` exists** (`api-gateway/.../RateLimitingFilter.java`) but uses `InMemoryRateLimitStorage` — counters are per-pod on multi-pod ECS, so a moderate flood spreads thin. Anonymous limit isn't strict enough either.
3. **Email-domain reserved-list validation exists** but only on `/newsletter/subscribe` (`RegistrationService.java:195` rejects `example.com`). Registration confirmation accepts it.
4. **No AWS WAF on AWS API Gateway** — only a coarse 1000 req/sec global throttle (`api-gateway-stack.ts:106-109`).
5. **No honeypot field** on the registration form.
6. **No SES-suppression / bounce hygiene** — the May 4 burst of 30 stale-corporate-address bounces is still inflating the rolling rate.

---

## Plan

Three tiers, ordered by impact-per-effort.

### Tier 1 — close the bleeding edge (½ day)

Goal: ZAP's next Monday scan must not burn SES quota. Real low-effort attackers should hit a 422/429 wall before the SES call.

**T1.1 — Apply the reserved-email-domain validator to all outbound mail in `shared-kernel EmailService`.**
- File: `shared-kernel/src/main/java/ch/batbern/shared/service/EmailService.java`
- Before any `SESClient.sendEmail` / `sendRawEmail` call, throw `IllegalArgumentException` (or a new `ReservedEmailRecipientException`) if the recipient's domain matches RFC 2606 / RFC 6761 reserved domains: `example.com`, `example.org`, `example.net`, `test`, `localhost`, `invalid`, `*.example`, `*.test`, `*.invalid`, `*.localhost`.
- Catches every email path uniformly (registration confirm, deregister, speaker invite, partner invite, task reminder, newsletter, etc.) — one place, full coverage.
- Returns to caller as a logged-but-swallowed exception in async paths so user flows don't 500.

**T1.2 — Per-IP rate-limit the three public anonymous POSTs.**
- Files: `api-gateway/.../RateLimiter.java` + `RateLimitingFilter.java` + `InMemoryRateLimitStorage.java`
- Add path-specific anonymous limits (per remote IP, 5-minute window):
  - `POST /api/v1/events/*/registrations` → 5/window
  - `POST /api/v1/newsletter/subscribe` → 5/window
  - `POST /api/v1/registrations/deregister/by-email` → 3/window
- Closes the `DeregistrationController.java:32-34` TODO. Even with `InMemoryRateLimitStorage`, on 2 pods each pod would allow 5 → 10 total per IP — still tight enough.

**T1.3 — Suppress noisy ZAP findings on these endpoints in `.zap/rules.tsv`** so the next scheduled scan doesn't keep re-raising the same active alerts (we know about them). Add IGNOREs for relevant alertIds on the registration / newsletter / deregister URL patterns — only after T1.1 + T1.2 land, so we don't hide regressions.

**Expected outcome after Tier 1:**
- ZAP's POST flood → 422 from T1.1 (no SES call) and/or 429 from T1.2 (request blocked at gateway).
- Bounce rate stops climbing.
- Real low-skill abusers blocked by the same layers.

---

### Tier 2 — defense in depth (1–2 days)

**Layering note — no new datastore needed.** We deliberately do NOT introduce Redis, DynamoDB or any new cache. AWS WAF's rate-based rules give us per-IP throttling with AWS-managed state — that is the distributed rate limiter for the public anonymous paths. The existing Spring `RateLimitingFilter` (Caffeine, per-pod) stays as it is for authenticated/role-based limits, where per-pod accounting is good enough at our QPS. PostgreSQL remains an in-our-back-pocket option if we ever need cross-pod Spring-side limits, but it's overkill for today's threat.

| Layer | Purpose | State store |
|------|---------|-------------|
| AWS WAF rate-based (T2.1, new) | per-IP throttling on anonymous POSTs | AWS-managed |
| Spring `RateLimitingFilter` (existing, keep) | per-authenticated-user / role-coarse limits | Caffeine, per-pod |
| AWS API Gateway global throttle (existing) | overload protection | AWS |

**T2.1 — AWS WAF Web ACL in front of AWS API Gateway.**
- File: `infrastructure/lib/stacks/api-gateway-stack.ts` (add new WAF construct).
- Rules:
  - `AWS-AWSManagedRulesAmazonIpReputationList` — free, catches known scanners and Tor exit nodes.
  - `AWS-AWSManagedRulesCommonRuleSet` (or its `NoUserAgent_HEADER` rule isolated) — many bots send no UA.
  - Custom rate-based rule: **10 requests / 5 min per IP** on the regex `^/api/v1/(events/.+/registrations|newsletter/subscribe|registrations/deregister/by-email)$`. Block on excess.
  - Allow-list our own GitHub Actions runner egress IP range for the security-scan workflow (or use a header secret — see T2.4).
- Cost: ~$5/month per Web ACL + per-million-request charges (negligible at our volume).
- Logs to CloudWatch for tuning.

**T2.2 — *(dropped — no new datastore needed; WAF is the cross-pod rate limiter)*.**

**T2.3 — SES suppression-list hygiene job.**
- Scheduled Lambda (daily at 04:00 UTC) that walks the SES suppression list, re-checks any entry older than 30 days, and removes those whose domain MX still resolves. For BOUNCE entries it can be conservative (only remove with manual review); for COMPLAINT entries leave alone.
- Bonus: report stale entries so we can fix our user list (people who left a company).
- Net effect: the 4% bounce rate drops once the May-04 stale-corporate-address burst ages out properly.

**T2.4 — Tag legitimate scan traffic so it's exempt from rate-limit + reserved-domain checks.**
- In `security-scan.yml`, add a secret header `X-BATbern-Scan: <secret>` to ZAP via the action's `cmd_options`, plus expose the secret to the Spring filter via SSM parameter.
- `RateLimitingFilter` and the reserved-email check skip when the header is present and valid (constant-time compare).
- Lets us keep scanning aggressively without lighting up SES.
- Alternative: replace the production target with a staging-only scan environment that does not have SES production credentials (longer-term — see T3.3).

**T2.5 — Monitoring + alarms.**
- CloudWatch metric filter on `Email domain is reserved` log line in api-gateway / event-management — emits `RegistrationReservedDomainBlocks` metric.
- CloudWatch alarm: if WAF blocks > 50 in 5 min, or if reserved-domain blocks > 10 in 5 min, ALARM → SNS (existing `batbern-staging-alarms` topic). Gives us a heads-up before SES alarms fire.
- Add `EmailsRejected` and `CalendarRepliesDropped` (the metric we just added) to the staging dashboard.

---

### Tier 3 — strategic improvements (backlog)

**T3.1 — Delay the calendar invite until email-confirmation click.**
- Today: registration writes `confirmed` row + sends the .ics in one step.
- Proposed: registration writes `pending` + sends a short *confirmation* email with a token link (no .ics). On click, transition to `confirmed` + send the .ics + welcome content.
- Effect: every spurious registration costs us at most a tiny plain-text email, not the heavy .ics + HTML template.
- Side benefits: cleaner data, GDPR-friendlier, classic double-opt-in pattern.
- Caveat: UX change requires PM/design alignment. Story.

**T3.2 — Honeypot field on the registration form.**
- File: `web-frontend/.../RegistrationWizard.tsx` — add a `style="position:absolute;left:-9999px"` text input with a plausible name (`website`, `phone2`, …). Submit if filled → silently 200, drop server-side.
- 5-minute change, kills the dumbest bots, harmless to humans.

**T3.3 — Dedicated scan environment.**
- The ZAP workflow already targets `api.batbern.ch` (= production). Migrate it to a parallel `scan.batbern.ch` or an isolated staging stack with `SES_ENABLED=false` and a stubbed mailer.
- Removes the conflict between security scanning and production protection.
- Bigger infra change — wait until traffic justifies.

**T3.4 — *(removed — handled by WAF; no Spring-side change required)*.**

---

## Out of scope on purpose

- Tightening Turnstile to fail-closed → blocks corporate users (commit `4079d394`).
- Replacing Turnstile with a different CAPTCHA → same blocker.
- Adding auth to currently-public registration endpoints → breaks the public form.
- Detection-evasion fingerprinting / device-fingerprinting heuristics.

---

## Open questions for stakeholders

1. **Tier 1 first** — small, reversible, kills today's incident loop. Approve to start?
2. **T2.1 (AWS WAF)** — ~$5/month plus per-request. Acceptable?
3. **T2.4** — scan-bypass via header secret is operationally simpler than allow-listing GitHub runner IPs (which are dynamic). Any objection?
4. **T3.1 (double opt-in for registration)** — UX/PM call. Worth scoping a story?

---

## Verification plan after Tier 1 lands

1. Manually re-run `security-scan.yml` via `workflow_dispatch` and confirm:
   - SES send count stays flat during the scan
   - WAF/gateway logs show 422/429 for `zaproxy@example.com` registrations
   - ZAP report still lists the *findings* (information disclosure), but no longer triggers the active email-send finding
2. Watch the `Reputation.BounceRate` metric for 15 days. Expected: gradual drop from 4% as the May-04 suppression burst ages out (it expires 2026-05-19 from the 15-day window).
3. Spot-check public registration on `www.batbern.ch` (mobile, desktop, corporate VPN) — must still work end-to-end.

---

## References

- Triggering commit (Turnstile fail-open rationale): `4079d394 fix(turnstile): fail-open when token is missing, block only on invalid token` (2026-05-06)
- Email forwarding hardening (same incident): `a3ac0a97 fix(email): stop iMIP calendar replies from fan-out to organizers` (2026-05-18)
- TODO acknowledged in code: `DeregistrationController.java:32-34`
- ZAP workflow: `.github/workflows/security-scan.yml`
- ZAP rule overrides: `.zap/rules.tsv`
- Existing rate limiter: `api-gateway/src/main/java/ch/batbern/gateway/security/RateLimitingFilter.java`
- Reserved-domain validator (newsletter only today): `services/event-management-service/src/main/java/ch/batbern/events/service/RegistrationService.java:195`
