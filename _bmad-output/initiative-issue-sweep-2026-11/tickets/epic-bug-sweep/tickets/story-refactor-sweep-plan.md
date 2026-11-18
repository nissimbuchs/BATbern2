---
title: 'Refactor sweep'
type: 'refactor'
ticket: '13'
created: '2026-11-18'
status: 'in-progress'
baseline_revision: '40b0ea85eff8470f42db87ea2ce63771c7ec008a'
route: 'full'
route_source: 'auto'
risk: 'low'
review: ''
review_source: ''
lenses_ran: []
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Stories 1-12 deferred nothing (C9) but left small leftovers in their reports: `docs/architecture/tech-stack.md` still says Gradle 8 / Node 20 (`docs/versions.json` now says 9.x / 22); CLAUDE.md shows `cd shared-kernel && ./gradlew clean build publishToMavenLocal`, against its own "Gradle from the root" rule; the frontend declares a `VITE_COGNITO_DOMAIN` env type nothing reads; `useLiveSessionControl` never sets the `offline` status, so Live Control's "Offline" badge and offline hint (keys in 10 locales) are unreachable; `uiStore.locale` is unused.

**Approach:** Fix each: docs follow `docs/versions.json`; CLAUDE.md uses `./gradlew :shared-kernel:clean :shared-kernel:build :shared-kernel:publishToMavenLocal` from the root; remove the unused env type; Live Control shows Offline when the browser goes offline (`online`/`offline` window events and `navigator.onLine`), back to the poll state when online, with a test; remove `uiStore.locale` and its callers if any.

## Boundaries & Constraints

**Always:** Tests for behaviour changes (offline badge: real i18next, MSW harness). Grep before removing that nothing reads it.

**Never:** Touch production config or data. Start new feature work.

</frozen-after-approval>

## Code Map

- `docs/architecture/tech-stack.md`, `docs/versions.json`.
- `CLAUDE.md` "Gradle Issues" troubleshooting block.
- `web-frontend/src/vite-env.d.ts` (or wherever `VITE_COGNITO_DOMAIN` is typed).
- `web-frontend/src/hooks/useLiveSessionControl*`, `src/pages/LiveControlPage/LiveControlPage.tsx` (ConnectionBadge), `__tests__/LiveControlPage.test.tsx`.
- `web-frontend/src/stores/uiStore*`.

## Tasks & Acceptance

**Execution:**
- [ ] each item; test for the offline badge

**Acceptance Criteria:**
- Given the Live Control page, when the browser fires `offline`, then the Offline badge and hint show in en; on `online` they go away.

## Design Notes

Architecture note: docs, frontend Live Control hook, a store field. No endpoint or dependency. Deviations: none.

## Verification

**Commands:**
- `cd web-frontend && npm run type-check && npm run lint && npx vitest run --maxWorkers=4 src/pages/LiveControlPage src/hooks src/stores` -- green.

**Rack rules:** Vitest `--maxWorkers=4`, output via `set -o pipefail; ... | tee ~/.cache/batbern-test-logs/<name>.log`. Do not commit or push. A bug found on the way is fixed too and reported (initiative C9).
