---
title: 'The claim-profile link opens the claim page for every role'
type: 'bugfix'
ticket: '2'
created: '2026-11-15'
status: 'built'
baseline_revision: 'c1075a85fe45e10f90715c71541f65010cec1bcc'
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

**Problem:** `/claim-profile` (the link in the claim mail, #1183 D) sits behind `ProtectedRoute`, and `canAccessPath` lists it for no role, so every signed-in user is sent to `/dashboard` and the self-claim cannot be used (#1217).

**Approach:** One list of role-neutral pages that every signed-in role may open (`/dashboard`, `/profile`, `/account`, `/attendee`, `/claim-profile`), replacing the per-role copies; prove it through the real route and `ProtectedRoute` for all four roles.

</frozen-after-approval>

## Implementation Notes

Oneshot, ~60 lines. Architecture note: frontend auth rules only (`src/contexts/authRules.ts`); no new dependency or endpoint; reuses the test auth helper, the real `ProtectedRoute` and the MSW harness; no deviation.
- Audit of the class: every `ProtectedRoute` route in `App.tsx` checked against the path rules; `/claim-profile` was the only role-neutral page no role listed.
- A user with no role opens none of the shared pages (guard keeps the old behaviour).
- `ClaimProfilePage.test.tsx` now renders the page inside the real `ProtectedRoute` on the MSW harness instead of mocking `userAccountApi` and `PublicLayout` (ratchet -2). RED measured: all 4 role cases landed on Dashboard before the fix.

## Verification

- `npx vitest run src/pages/public/ClaimProfilePage.test.tsx src/contexts src/components/auth` -- 21 files, 271 tests green.
- `npm run type-check`, eslint on the touched files, `npm run check:test-ratchet` -- clean.

## Review Triage Log

Quick review, 4 findings: 2 patched, 2 rejected.
- medium, patched: `05-frontend-architecture.md` guard step 3 still described a per-role map in `AuthContext.tsx`. Now names `authRules.ts`, `EVERY_ROLE_PATHS` and the no-role rule.
- low, rejected: the test declares its own `/claim-profile` route instead of App's. `ProtectedRoute` and the rules are real; testing App's route table would mean rendering the whole App (Amplify, layouts), more than a direct correction.
- low, patched: the roleless test's attendee assertion is its positive control (assertion rules); now commented as such, and the spread that makes the user roleless is explained.
- low, rejected: `startsWith` without a segment boundary. Not new (the same entries were in every role list) and no route collides; changing the matcher for all paths is more than a direct correction.
