---
title: 'Outreach history rows name the organizer by username'
type: 'bugfix'
ticket: '1'
created: '2026-11-19'
status: 'built'
baseline_revision: 'e93c11b9c1c7b5431141082f970ab8bce36034b3'
route: 'oneshot'
route_source: 'auto'
risk: 'low'
review: 'quick'
review_source: 'pinned'
lenses_ran: ['quick']
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Before Story 4.10, `SpeakerOutreachController` wrote the organizer's e-mail address into `speaker_outreach_history.organizer_username` (#1287). Those rows carry personal data in an audit column, and the UI shows an address where a username belongs. Since 4.10 the code writes usernames.

**Approach:** EMS forward migration V157 (owner, #1287 option 1). For each row whose value contains `@`, it sets the username of the one account that owns the address, matched case-insensitively against `user_profiles.email` or a verified `user_additional_emails.email`. A value that matches no account or several accounts stays unchanged. The migration reports both counts through `RAISE NOTICE`, which Flyway logs to CloudWatch. All services share one database (V116, V151). The migration skips itself when `user_profiles.email` or `user_additional_emails` is missing.

</frozen-after-approval>

## Implementation Notes

Oneshot: one SQL migration and one migration test (about 80 lines). Only verified additional addresses count: an unverified one is just a claim (`UserAdditionalEmailRepository` uses the same rule for SSO linking). Audit of the class: the remaining `getCurrentUserEmail()` callers (`RegistrationController`, `NewsletterController`) need the address itself and write no username column.

## Verification

**Commands:**
- `./gradlew :services:event-management-service:test --tests '*V157*'` -- expected: the four seeded cases (primary match, verified additional match in other case, unknown address, plain username) behave as stated, plus an ambiguous address and an unverified additional address stay unchanged
- the full EMS suite plus checkstyle -- expected: green

**Manual checks:**
- After deploy, the EMS CloudWatch log shows the NOTICE with both counts. They go into the PR.

## Review Triage Log

Quick review, 4 findings: 4 real (2 medium, 2 low), 0 false. All patched.
- medium, patch: `06-backend-architecture.md` still said the old rows keep the address. It now describes V157.
- medium, patch: the ambiguous case was seeded as an additional address equal to another account's primary address. CUMS V16's trigger rejects that in production. It is now two primary addresses that differ only in case, which the case-sensitive unique index allows.
- low, patch: the unverified address was attached to the wrong user (copy slip).
- medium, patch: the NOTICE counts were not asserted. The test now captures Flyway's INFO line and checks "2 ... 3".
