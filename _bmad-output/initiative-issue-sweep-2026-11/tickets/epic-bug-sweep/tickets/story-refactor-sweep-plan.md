---
title: 'Refactor sweep'
type: 'refactor'
ticket: '13'
created: '2026-11-18'
status: 'built'
baseline_revision: '52704a29d3c06ba4ed5de25e24b85c4ced48f531'
route: 'full'
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
- [x] each item; test for the offline badge

**Acceptance Criteria:**
- Given the Live Control page, when the browser fires `offline`, then the Offline badge and hint show in en; on `online` they go away.

## Design Notes

Architecture note: docs, frontend Live Control hook, a store field. No endpoint or dependency. Deviations: none.

## Implementation Notes

- `docs/architecture/tech-stack.md` already said Gradle 9.x / Node 22 (fixed by 8c7fed005); no change. The stale
  Node 20 claims were elsewhere and now say Node.js 22: CLAUDE.md prerequisites, `README.md`,
  `docs/guides/local-development-setup.md`, `docs/guides/BACKEND_CONFIGURATION.md`,
  `docs/deployment/native-execution.md`, `docs/testing/testing-strategy-execution.md`, `infrastructure/README.md`,
  `web-frontend/e2e/workflows/documentation/screencast/SYNCHRONIZATION-GUIDE.md`, the "Set up Node.js 20" step names
  in five workflows (they already pinned `node-version: '22'`; name only, no behaviour change), and
  `scripts/dev/start-all-native.sh` (its check accepted Node 20; now requires 22). Left as is (git grep): the disabled
  `deploy-dev.yml.disabled`, the historical QA gate `docs/qa/gates/1.4-*.yml`, the BMAD training pages (about BMAD's
  own Node need) and two comments in `scripts/update-versions*` that describe the old drift. The service `Dockerfile`s still build in `gradle:8.14.5-jdk21` (prod) and
  `Dockerfile.dev` in `gradle:8.5-jdk21`; tech-stack.md states that truthfully. Not changed: a production build image.
- CLAUDE.md "Gradle Issues": `./gradlew :shared-kernel:clean :shared-kernel:build :shared-kernel:publishToMavenLocal`.
- Env types, class audit: all five names in `ImportMetaEnv` (`VITE_APP_ENV`, `VITE_COGNITO_WEB_CLIENT_ID`,
  `VITE_COGNITO_USER_POOL_ID`, `VITE_COGNITO_DOMAIN`, `VITE_API_GATEWAY_URL`) had no reader (git grep), so the
  interface and its `ImportMeta` augmentation were removed; `vite/client` types the env. The setup guide's
  `web-frontend/.env.local` Cognito block was fiction (the SPA loads Cognito from `GET /api/v1/config`) and now says so.
- Live Control: the hook keeps the poll status and exposes `'offline'` while `navigator.onLine` is false or after an
  `offline` event, the poll status again after `online`. Two page tests (real i18next en, MSW, real hook).
- Coordinator follow-up (C9, approved): back online the hook polls at once (a poll in flight is followed by one
  with no delay) instead of waiting up to 15 s; the online test asserts the extra `getLiveTiming` call inside
  waitFor's 1 s (fails without the listener, checked). `liveControl.offlineHint` no longer says "reload the page"
  but that the page reconnects by itself, in all 10 locales (EN/DE first-class, others straight translations).
- Review follow-up: a poll that succeeds clears the browser-offline override (no stuck "Offline" when no `online`
  event comes). Page tests: the reconnect test answers 503 while offline and holds the 200 after `online`, so
  "Connected" can only come from that answer; a test for the success-clear. Hook tests: offline at mount, `online`
  mid-poll (delay-0 branch, no overlapping poll), listeners removed on unmount. Each new test was checked to fail with
  its behaviour removed.
- Deviations from the Design Notes ("Deviations: none"), each deliberate and approved by the orchestrator under C9:
  (1) the whole `ImportMetaEnv` block removed, not only `VITE_COGNITO_DOMAIN` (all five names unread);
  (2) the `liveControl.offlineHint` text changed in 10 locales; (3) the immediate re-poll on `online`.
- `uiStore.locale`/`setLocale` removed; only tests and three Layout test mocks referenced them. Docs
  `05-frontend-architecture.md` updated. A stale `batbern-ui-locale` key may remain in existing browsers; harmless.

## Verification

**Commands:**
- `cd web-frontend && npm run type-check && npm run lint && npx vitest run --maxWorkers=4 src/pages/LiveControlPage src/hooks src/stores` -- green.

**Rack rules:** Vitest `--maxWorkers=4`, output via `set -o pipefail; ... | tee ~/.cache/batbern-test-logs/<name>.log`. Do not commit or push. A bug found on the way is fixed too and reported (initiative C9).

## Review Triage Log

Quick review, 5 findings, all patched.
- low, patched: Node 20 claims in six more docs (and five workflow step names, the native start script's version check) now say 22.
- low, patched: the three additions beyond the Approach are recorded as deliberate (C9).
- medium, patched: the Offline badge could stay stuck when no `online` event came; a successful poll now clears it, with a test.
- low, patched: the reconnect test now fails the poll while offline and shows Connected only after the new poll's answer.
- low, patched: hook tests for offline at mount, `online` during a poll, and listener removal.
Also found in the sweep (orchestrator, read-only Logs Insights): EMS's registration rate limit keys on 127.0.0.1, one bucket for all visitors; all 3,371 refusals in 14 days came from the weekly ZAP scan. Evidence written to epic 8 (R23 owns the trusted client IP).
Epic verification (orchestrator, full run on the final tree before the last 1.13 patches, then the touched frontend scope again): shared-kernel 692, EMS 3057, CUMS 989, PCS 291, gateway 348, all 0 failures with checkstyle; Vitest 479 files / 6066 tests; infra jest 37 suites / 510; type-check, lint, ratchet, i18n keys, versions check green.
