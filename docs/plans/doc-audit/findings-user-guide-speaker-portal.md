# Doc Audit Findings — User guide — speaker portal
**Audited:** 2026-03-09
**Doc:** `docs/user-guide/speaker-portal` (README.md, invitation-response.md, content-submission.md, dashboard.md)
**Tests searched:** `services/speaker-coordination-service/src/test/java`

## Summary
- VALIDATED: 1
- MISMATCH: 0
- UNTESTED: 22
- UNDOCUMENTED: 0

**Overall:** The speaker-coordination-service test suite contains exactly **3 test files** with **2 meaningful test methods**, both of which only exercise the Spring Actuator health/info endpoints. There is **zero test coverage** for any speaker portal business rule. Every functional claim in these four documentation files is untested.

---

## MISMATCH

_None found._

---

## UNTESTED

### U1 — RESPOND token is single-use
**Doc claims (README.md):** "RESPOND — Valid For: 30 days | Reusable? No (single-use) | Used For: Accept / Decline invitation"
**Also (invitation-response.md):** "they become invalid after the first click"
**Risk:** high — if single-use enforcement is missing, a token could be replayed by a third party to accept/decline on behalf of a speaker

### U2 — VIEW token is reusable
**Doc claims (README.md):** "VIEW — Valid For: 30 days | Reusable? Yes | Used For: Dashboard, profile updates, content submission"
**Risk:** medium — if VIEW tokens are accidentally marked single-use, speakers lose dashboard access after first click

### U3 — Token validity period is 30 days
**Doc claims (README.md):** "Valid For: 30 days" for both RESPOND and VIEW tokens
**Also (invitation-response.md):** "RESPOND tokens are valid for 30 days from the invitation send date"
**Risk:** high — no test verifies token expiry logic; an off-by-one or missing expiry check would silently accept expired tokens forever or reject tokens prematurely

### U4 — Token is 32-byte SecureRandom; only SHA-256 hash stored
**Doc claims (README.md):** "Tokens are cryptographically generated (32-byte SecureRandom) and only their SHA-256 hash is stored in the database — the token itself is never persisted."
**Risk:** high — if the raw token is persisted instead of the hash, PII/security breach risk on DB compromise

### U5 — Rate limiting: 5 requests/minute per IP
**Doc claims (README.md):** "Rate limiting: 5 requests/minute per IP."
**Risk:** medium — no test validates the rate-limit threshold or that excess requests are rejected with 429

### U6 — Accept transitions INVITED → ACCEPTED
**Doc claims (invitation-response.md):** "Speaker status transitions: INVITED → ACCEPTED"
**Risk:** high — core state machine transition; if absent or wrong, downstream quality-review and publication flows break

### U7 — Decline transitions INVITED → DECLINED
**Doc claims (invitation-response.md):** "Speaker status transitions: INVITED → DECLINED"
**Risk:** high — organiser kanban and reporting depend on DECLINED state being set correctly

### U8 — Decline reason is required
**Doc claims (invitation-response.md):** "Reason for declining *:" (shown as required field)
**Risk:** medium — if backend accepts an empty decline reason, organiser contact history is incomplete

### U9 — `change_reason = 'SPEAKER_PORTAL_RESPONSE'` recorded in status history
**Doc claims (invitation-response.md):** "Status history records the transition with `change_reason = 'SPEAKER_PORTAL_RESPONSE'`"
**Risk:** medium — audit trail correctness; no test verifies the exact value stored

### U10 — Confirmation email sent on acceptance
**Doc claims (invitation-response.md):** "A confirmation email is sent automatically to the speaker with: Event and session details, Content submission deadline, Link to update their profile, Link to submit presentation content"
**Risk:** medium — email delivery untested; a missing domain event or email handler would silently skip confirmations

### U11 — Organiser notified in-app on accept and decline
**Doc claims (invitation-response.md):** "The organiser is notified in-app (async)" on acceptance; "The organiser is notified in-app" on decline
**Risk:** medium — async notification untested; broken event handler would silently suppress organiser alerts

### U12 — Content submission: title max 200 chars (required), abstract max 1000 chars (required)
**Doc claims (content-submission.md):**
> "Title — Required: Yes | Limit: 200 characters"
> "Abstract — Required: Yes | Limit: 1000 characters"
**Risk:** high — if backend does not enforce these limits, oversized content enters the DB and corrupts display

### U13 — Abstract warning shown when under 200 characters
**Doc claims (content-submission.md):** "Warning shown if under 200 chars"
**Risk:** low — UX guidance only; backend enforcement not implied, but doc states this as system behaviour

### U14 — File types restricted to PPTX, PDF, KEY; max 50 MB
**Doc claims (content-submission.md):**
> "Accepted formats: PPTX, PDF, KEY"
> "Maximum size: 50 MB"
**Risk:** high — if backend presigned-URL generation does not restrict content-type or size, speakers can upload arbitrary files to S3

### U15 — Files uploaded directly to S3 via presigned URL (never proxied through backend)
**Doc claims (content-submission.md):** "Files are uploaded directly to S3 via presigned URL — they are never sent through the backend."
**Risk:** medium — architecture correctness; a regression to proxied upload would cause large-file timeouts in production

### U16 — Draft auto-saved every 30 seconds; restored on re-visit
**Doc claims (content-submission.md):** "The portal automatically saves a draft every 30 seconds. If the speaker closes the browser and returns via their link, the draft is restored from the server."
**Risk:** medium — no test verifies server-side draft persistence or restoration; data loss on browser close if broken

### U17 — Content submission transitions ACCEPTED → CONTENT_SUBMITTED
**Doc claims (content-submission.md):** "Speaker status transitions: ACCEPTED → CONTENT_SUBMITTED"
**Risk:** high — organiser Phase C workflow depends on this state; if broken, submissions are invisible to organisers

### U18 — Each resubmission increments version number; all versions stored
**Doc claims (content-submission.md):** "Each resubmission increments the version number (v1, v2, v3…), and all versions are stored for the organiser."
**Risk:** medium — organiser review history and rollback capability depend on versioning; unverified

### U19 — Reminder tiers: Tier 1 = 14 days, Tier 2 = 7 days, Tier 3 = 3 days before deadline
**Doc claims (README.md):**
> "Tier 1 — Friendly: 14 days before deadline"
> "Tier 2 — Urgent: 7 days before deadline"
> "Tier 3 — Final: 3 days before deadline"
**Risk:** high — timing logic untested; wrong thresholds would send reminders at wrong times or not at all

### U20 — Reminders skipped if speaker already responded or submitted
**Doc claims (README.md):** "Reminders are skipped if the speaker has already responded or submitted content."
**Risk:** high — without this check, accepted/submitted speakers receive spam reminders

### U21 — After Tier 3, in-app notification created for organiser
**Doc claims (README.md):** "After Tier 3, an in-app notification is created for the organizer."
**Risk:** medium — organiser escalation path untested; silent failure if domain event is missing

### U22 — Dashboard upcoming events filtered to active states; past events to CONFIRMED or ACCEPTED with past date
**Doc claims (dashboard.md):**
> "Shows all events where the speaker is in an active state (invited, accepted, content submitted, quality reviewed, confirmed)."
> "Shows all events where: The event date is in the past, and The speaker was CONFIRMED or ACCEPTED"
**Risk:** medium — incorrect filter logic would show cancelled/declined speakers in upcoming view or exclude valid past events

---

## UNDOCUMENTED

_No undocumented test assertions found. The only substantive tests (`HealthControllerIntegrationTest`) verify Spring Actuator `/actuator/health` (status UP) and `/actuator/info` (HTTP 200). These are infrastructure-level checks not expected to appear in the speaker portal user guide._

---

## VALIDATED
- "Spring Boot service is operational / health UP" → `HealthControllerIntegrationTest#should_returnHealthStatus_when_healthEndpointCalled`

---

## Recommended Actions

The speaker-coordination-service is the most under-tested service in the BATbern backend relative to its documentation surface area. The following test gaps carry the highest production risk and should be addressed first:

| Priority | Finding | Why |
|----------|---------|-----|
| 🔴 Critical | U1 — RESPOND token single-use | Security: token replay attack |
| 🔴 Critical | U3 — 30-day expiry enforced | Security: expired tokens accepted indefinitely |
| 🔴 Critical | U4 — SHA-256 hash stored, not raw token | Security: DB breach exposes all tokens |
| 🔴 Critical | U6, U7 — State machine transitions | Core workflow correctness |
| 🔴 Critical | U17 — ACCEPTED → CONTENT_SUBMITTED | Organiser Phase C depends on this |
| 🟡 High | U12 — Title/abstract field limits | Data integrity |
| 🟡 High | U14 — File type & size enforcement | S3 security / abuse prevention |
| 🟡 High | U19 — Reminder tier timing | Speaker experience / spam prevention |
| 🟡 High | U20 — Reminders skip if already responded | Spam prevention |
