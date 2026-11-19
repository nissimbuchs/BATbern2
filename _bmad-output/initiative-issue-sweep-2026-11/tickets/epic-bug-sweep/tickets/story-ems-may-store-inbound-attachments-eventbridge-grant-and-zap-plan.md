---
title: 'EMS may store inbound attachments; EventBridge grant and ZAP 110009 gone'
type: 'bugfix'
ticket: '1'
created: '2026-11-15'
status: 'built'
baseline_revision: 'e16231fa7af5b42cc647ac1a50171ca349364f7d'
route: 'oneshot'
route_source: 'auto'
risk: 'medium'
review: 'quick'
review_source: 'pinned'
lenses_ran: ['quick']
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** EMS keeps inbound attachments under `mail-attachments/` in the inbound bucket, but the deployed task role has only `grantRead` on that bucket (bin/batbern-infrastructure.ts:313); the put/delete grant sits in InboundEmailStack's `emsTaskRole` block, which bin never feeds, so an inbound mail with a kept attachment fails and lands in the DLQ. The stack-level test passed while production lacked the grant. EMS also still holds an `events:*` grant for an auto-publish EventBridge scheduler that no longer exists (auto-publish is `@Scheduled`), and ZAP 110009 flags the `path` field of our JSON error bodies.

**Approach:** Wire `grantPut`/`grantDelete` on `mail-attachments/*` next to the existing `grantRead` in bin, delete the dead `emsTaskRole` prop/block and its stack test, delete the EventBridge grant, and prove both on the real staging app synth (the level that would have caught the bug). Add `110009 IGNORE` with the reason to `.zap/rules.tsv`.

</frozen-after-approval>

## Implementation Notes

Oneshot: ~60 lines across 6 files, all mechanical infra/test edits.

Architecture note:
- Modules touched: infrastructure only (bin wiring, InboundEmailStack, EventManagementStack), `.zap/rules.tsv`, docs 08.
- New dependencies: none (EMS stack already imports the inbound bucket ARN via the existing `grantRead`).
- New endpoints: none.
- Reused: the app-synth harness of `test/unit/no-event-bus.test.ts`, extracted to `test/unit/helpers/synth-staging-app.ts` and shared with the new `ems-task-role-grants.test.ts`.
- Deviations: none. Feeding `emsTaskRole` instead would need the EMS role before EMS exists (cyclic stack order); keeping all EMS inbound grants in bin, one place, is the smaller change.

Epic 17 rule: no ArchUnit/ratchet store in scope (infra TS only); the cleanup is the dead `emsTaskRole` prop and the dead EventBridge grant.

Code map:
- `infrastructure/bin/batbern-infrastructure.ts:309-318` -- EMS inbound grants; add put/delete on `mail-attachments/*`.
- `infrastructure/lib/stacks/inbound-email-stack.ts:22,443-451` -- dead `emsTaskRole` prop + block; delete.
- `infrastructure/test/unit/inbound-email-stack.test.ts:113-134` -- test of the dead block; delete (replaced by app-synth test).
- `infrastructure/lib/stacks/event-management-stack.ts:290-306` -- `events:*` grant; delete.
- `infrastructure/test/unit/no-event-bus.test.ts:97-102` -- `should_keepTheAutoPublishRuleGrant` flips to "no `events:` action in EMS"; header comment updated.
- `.zap/rules.tsv` -- add 110009 under FALSE POSITIVES.
- `docs/architecture/08-operations-security.md:95` -- say where the grant is wired and what guards it.

Implementation:
- RED measured: the new synth test failed on baseline (no put/delete on `mail-attachments/*`; `events:PutRule` etc. present). GREEN after the change; full infra unit suite 37 suites / 499 tests green.
- ZAP 110009 evidence checked in run 41424783146 (`zap-api-users`): all 3 hits have evidence `/users/`.
- `no-event-bus.test.ts` now uses the shared synth helper; its "keep the auto-publish grant" test is gone (the new test forbids any `events:` action in EMS).

## Review Triage Log

Quick review, 5 findings: 2 medium, 2 low patched, 1 low rejected.
- medium, patched: `110009 IGNORE .*/users/.*` would ignore the rule on every URL. Checked `zaproxy/docker/zap_common.py` `load_config`: the third column is a URL regex only for OUTOFSCOPE. Now `110009 OUTOFSCOPE .*/users/.*`. The same misreading in the header and the 10024 lines is fixed in its own commit, which also scopes 10024 to `users/additional-emails/verify` (the one hit the global ignore was hiding).
- medium, patched: the test did not guard "EMS writes only under `mail-attachments/`". Added `should_writeTheInboundBucketOnlyUnderMailAttachments`; mutation check (bucket-wide `grantPut` in bin) fails it.
- low, patched: the `events:` check skipped inline role policies and managed policies, and `Roles` could be undefined. Both are covered now.
- low, patched: the synth helper leaked env and outdir when synth threw. It now cleans up and rethrows.
- low, rejected: two full synths per run. Measured at 14 s each, and splitting by concern is worth that.

## Verification

**Commands:**
- `cd infrastructure && npx jest test/unit/ems-task-role-grants.test.ts test/unit/no-event-bus.test.ts test/unit/inbound-email-stack.test.ts test/unit/event-management-iam.test.ts` -- expected: all green; the new grant test fails on the baseline (RED).
- `cd infrastructure && npx tsc --noEmit && npx prettier --check bin lib test` -- expected: clean.
- `cd infrastructure && npx jest test/unit` -- expected: green.

**Manual checks:**
- After deploy (HITL, owner): an inbound mail with an attachment to replies@ shows in the Mail Center with its attachment; EMS log has no AccessDenied on `mail-attachments/`.
