---
title: 'Unconfirmed newsletter subscriptions expire'
type: 'feature'
ticket: '5'
created: '2026-11-20'
status: 'built'
baseline_revision: '2cca0218bee4d2fc3f9d2ce0444c88cb98309de0'
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

**Problem:** After story 3.4 a request that nobody confirms stays pending for good. Its row holds an address nobody vouched for, and its link keeps working, although the mail says it is valid for 7 days.

**Approach:**
- The confirmation window is 7 days, counted from the last mail (`confirmation_sent_at`), else from `subscribed_at`.
- `confirm(token)` refuses a token whose window has passed (404, as for an unknown token).
- A daily EMS job (`0 45 3 * * *`, zone Europe/Zurich, ShedLock) handles requests whose window has passed:
  - It deletes rows that were never confirmed and are neither unsubscribed nor suppressed.
  - It clears the token of an unsubscribed row that carries an expired request. The row stays as the record of the opt-out.
  - Confirmed rows without a request and suppressed rows are never touched.
  - It logs both counts.

</frozen-after-approval>

## Implementation Notes

Oneshot: a service method, a scheduler class and tests (about 80 lines). This is the same pattern as `EmailForwardingLogRetentionScheduler`.

## Verification

**Commands:**
- `./gradlew :services:event-management-service:test --tests '*Newsletter*' --tests '*ScheduledZoneGuard*'` -- expected: with a fixed clock, a pending row past the window is deleted. A younger pending row, an active row, a suppressed row and an unsubscribed row stay; the unsubscribed row's expired token is cleared. An expired token answers 404.
- full EMS suite plus checkstyle -- expected: green

## Review Triage Log

Quick review, 7 findings: 7 real (2 high, 2 medium, 3 low), 0 false. All patched.
- high, patch: a registration opt-in on an old unsubscribed row was counted from the old `subscribed_at` and wiped the same night. A new `confirmation_requested_at` (in the unshipped V158) is set on every request. The window counts from the later of that and the last mail.
- high, patch: a registration opt-in has no mail of its own and was deleted after 7 days, although its registration stays confirmable for about 9 days. Such rows get 14 days unless an organizer's confirmation mailed them.
- medium, patch: the token-clearing branch touched suppressed rows. It now skips them.
- medium, patch: the confirm page told a visitor with an expired link only that it was "invalid or used". The text in all 10 locales now names expiry and asks the visitor to sign up again.
- low, patch: the spec's 404 description and the `confirm` Javadoc did not mention expiry. Both do now.
- low, patch: the missing edge tests were added: registration opt-ins at 10 and 15 days, a mailed registration opt-in, a re-request on an old unsubscribed row, and an unsubscribed and suppressed row.
Also found while testing: the job's ShedLock (`lockAtLeastFor` PT5M) skipped repeat calls in one test run. The tests now call the service method that the job runs.
