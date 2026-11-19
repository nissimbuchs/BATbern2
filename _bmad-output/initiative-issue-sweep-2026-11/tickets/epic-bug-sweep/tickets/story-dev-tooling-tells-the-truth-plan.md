---
title: 'Dev tooling tells the truth'
type: 'chore'
ticket: '12'
created: '2026-11-18'
status: 'built'
baseline_revision: '88373d63cea75cb55bf24c12414b5d826f1bc762'
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

**Problem:** (#1211 small fixes, #1213 code, #1119 leftovers) Dev tooling states things that are not true: `make ci-test` depends on a missing `test-coverage` target; `start-all-native.sh` prints dead ports and a config path that does not work in its hint and header; `.env.example` lacks `COGNITO_DOMAIN_URL`; the CLAUDE.md CloudWatch Insights example uses BSD `date -v-1H`, which fails on Linux (rack); `scripts/update-versions.js` hard-codes `gradle: '8.x'` / `node: '20'` (lines ~144, ~204), so `docs/versions.json` is wrong while `--check` passes; `deploy-staging.yml` carries a commented-out infra E2E step; the shared-kernel README lists types that do not exist.

**Approach:** Fix each at its source: `ci-test` runs real targets; the script prints the live ports (from its BASE_PORT logic) and the working path; `.env.example` gains the variable with a comment where it comes from; the CLAUDE.md query uses GNU `date -d '1 hour ago' +%s` (and notes the macOS form); `update-versions.js` reads the Gradle version from `gradle/wrapper/gradle-wrapper.properties` and Node from the CI workflow (`.github/workflows/*.yml` `node-version`) or `.nvmrc`, `--check` fails on drift, `docs/versions.json` regenerated; the commented E2E step is deleted (or run, if it works today — decide by running it locally against nothing destructive; default: delete, it has been dead); the README type list generated from the real `shared-kernel/src/main/java/ch/batbern/shared/types` (and siblings it lists).

## Boundaries & Constraints

**Always:** Each fix verified by running it on rack (`make ci-test` reaches its first real step; `node scripts/update-versions.js --check` passes and reports Gradle 9.x and Node 22; the CLAUDE.md query's date expression evaluates; `start-all-native.sh` header/hint matches the ports `make dev-native-up` uses). A test for `update-versions.js` reading the wrapper and the workflow (node test runner or vitest, whichever the scripts use).

**Never:** Run a deploy or any AWS write. Change CI behaviour beyond deleting the dead commented step.

</frozen-after-approval>

## Code Map

- `Makefile:444` `ci-test: test-coverage lint`.
- `scripts/dev/start-all-native.sh` (or wherever `make dev-native-up` points; check `Makefile` targets).
- `.env.example`; where `COGNITO_DOMAIN_URL` is read (grep).
- `CLAUDE.md:~712` `--start-time $(date -v-1H +%s)`; check other docs for the same BSD form (grep `date -v`).
- `scripts/update-versions.js:~144,~204`, `docs/versions.json`, `gradle/wrapper/gradle-wrapper.properties`, `.github/workflows/*.yml` node-version.
- `.github/workflows/deploy-staging.yml` commented infra E2E step.
- `shared-kernel/README.md` type list.

## Tasks & Acceptance

**Execution:**
- [x] each item fixed and run

**Acceptance Criteria:**
- Given rack, when running each verify command above, then each succeeds and its output matches the fixed text.

## Design Notes

Architecture note: tooling, docs, one workflow file (comment deletion only). No production code. Deviations: none.

## Implementation Notes

- `.github/workflows/sync-versions.yml`: trigger paths extended (both `pull_request` and `push`) by
  `scripts/update-versions.test.mjs`, `gradle/wrapper/gradle-wrapper.properties`, `.nvmrc` and
  `.github/workflows/**`, so a wrapper or Node bump runs `--check`. This is a trigger-path change on a
  check-only workflow, not a deploy change; approved by the orchestrator under initiative C9 as an
  exception to "Never: change CI behaviour". The workflow already runs `node --test
  scripts/update-versions.test.mjs`.
- `COGNITO_DOMAIN_URL`: nothing reads it (no service config, no frontend code; the frontend's typed
  `VITE_COGNITO_DOMAIN` is a different, also unread name). Instead of adding it to `.env.example`, it was
  removed from `start-all-native.sh`, `sync-backend-config.sh`, `config/templates/backend.env.template`
  and `docs/guides/local-development-setup.md`, so there is no second answer left.
- `deploy-staging.yml`: the commented infra E2E step was already deleted by #1159; no change.

## Verification

**Commands:** the verify commands above; `make lint` scoped where a script changed.

**Rack rules:** one heavy build at a time; do not start long Gradle suites (`make ci-test` only until its first real step, then stop it); output via `set -o pipefail; ... | tee ~/.cache/batbern-test-logs/<name>.log`. Do not commit or push. A bug found on the way is fixed too and reported (initiative C9).

## Review Triage Log

Quick review, 6 findings, all patched.
- medium, patched: `sync-versions.yml` did not trigger on the new version sources; wrapper, `.nvmrc`, workflows and the test file added to both path lists (trigger paths of a check-only workflow, approved by the orchestrator under C9).
- low, patched: `testing-strategy-execution.md` named the missing `make test-coverage`.
- low, patched: README coverage is JaCoCo instruction coverage, not line.
- low, patched: `COGNITO_DOMAIN_URL` had two sources and no reader; removed everywhere (stale pool IDs in the setup guide corrected on the way).
- low, patched: "six JVMs" → four.
- low, patched: OOM query block now uses the profile and fetches results.
