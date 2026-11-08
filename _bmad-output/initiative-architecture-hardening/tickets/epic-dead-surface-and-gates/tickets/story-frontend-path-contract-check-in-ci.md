---
id: 3
type: story
title: "Frontend path contract check in CI"
parent: epic-dead-surface-and-gates
covers: [R3]
after: [1]
refined: true
hitl: false
risk: medium
---

# Frontend path contract check in CI

## Description

17.3: CI fails when the frontend calls a method and path that no OpenAPI spec defines, so drift like `getPublishPreview` cannot ship again. The breaking API stories (17.6 to 17.11) use it to prove that the frontend followed. Its spec matcher is one module that 17.0c's MSW validator reuses. It waits on 17.1, so that the check starts clean.

## Requirements

Verify: a fixture call to an unknown path fails the `build-frontend` check, and the check passes on develop with only documented allow-list entries.

1. **One spec matcher** (R3)
   **Given** every spec in `docs/api/` except the `_`-prefixed shared files
   **When** asked whether a method and URL are defined
   **Then** it answers with the matching operation (file and operation id) or a reason
   **And** it is method-aware, treats path parameters as single-segment wildcards (a literal `me` or `_global` matches `{username}` or `{eventCode}`), and strips a leading `/api/v1`
   **And** an unknown query parameter name is a mismatch, and a literal value outside a declared `enum` or `maximum` is a mismatch (`limit=1000` against a maximum of 100, the #1072 case)
   **And** both the CI script and the frontend test setup can import it, with no path aliases and no React.

2. **The scan resolves frontend calls** (R3)
   **Given** the frontend source files, excluding tests, mocks and generated types
   **When** the scan runs
   **Then** every HTTP call through the API client, `axios` or any `*Client` receiver is resolved to a method and path from string and template literals, same-file constants and same-file path helpers, with a trailing `/` stripped
   **And** absolute URLs and presigned upload URLs are ignored
   **And** query parameters built in a params object are left to 17.0c's runtime validator.

3. **Results and exit code** (R3)
   **Given** the scan's results
   **When** a resolved call matches no operation (NOT IN SPEC), a call cannot be resolved and is not allow-listed with a reason (UNRESOLVED), or an allow-list entry matches no call any more (STALE)
   **Then** the check prints each case and exits 1
   **And** otherwise it exits 0 after one summary line: `Frontend API calls: N resolved, M match the spec, K allow-listed.`

4. **Tests prove the rules** (R3)
   **Given** fixture specs and fixture sources
   **When** the matcher and scan tests run
   **Then** these fail: an unknown path, a wrong method, an unknown literal query parameter, an exceeded `maximum`, a stale allow-list entry, and a reproduction of `getPublishPreview` (NOT IN SPEC)
   **And** these pass: a parameterized path, a literal `me` or `_global` in a parameter position, a constant-resolved path and a helper-resolved path, while an absolute or presigned URL is ignored.

5. **It cannot be skipped** (R3)
   **Given** a PR that changes frontend code or only a spec in `docs/api/`
   **When** CI runs
   **Then** the matcher and scan tests and the check run in the job that feeds the required `build-frontend` check, so a violation blocks the merge
   **And** a `make` target runs the same check locally.

6. **Calls outside the service layer are frozen** (R3)
   **Given** the frontend files outside the service layer that make HTTP calls today (15 counted by grep on 2026-11-07)
   **When** the check runs
   **Then** they are listed in a committed baseline. A new file outside the service layer with an HTTP call fails, and a baseline entry whose file no longer calls HTTP fails as STALE.

7. **Initial findings resolved, not allow-listed away** (R3)
   **Given** the six calls the 2026-11-07 prototype found with no spec and no backend mapping (listed in the notes file)
   **When** the story classifies each
   **Then** a dead call chain (nothing renders it) is deleted with its tests, and a live call to an endpoint that exists elsewhere is repointed, with a test
   **And** a live call with no endpoint and a user-visible failure gets a GitHub issue with evidence and an allow-list line carrying the issue number, and the owner is told
   **And** afterwards the allow-list holds only calls the scan genuinely cannot resolve, each with a reason.

8. **Counted** (R3)
   **Given** the summary line
   **When** the story starts and when it ends
   **Then** both lines, plus the final allow-list and baseline sizes, are recorded in the plan.

## Boundaries

- Out: adding any endpoint. A finding is fixed by deleting or repointing the caller, or by an issue.
- Out: moving calls into the service layer. This story only freezes them.
- Out: runtime validation of query parameters built in params objects (17.0c).
- Must not change: the existing frontend CI steps (`check:api-types`, type-check, lint) keep running and keep their results.

## References

- parent — `_bmad-output/initiative-architecture-hardening/tickets/epic-dead-surface-and-gates/epic-dead-surface-and-gates.md`, Requirements R3
- notes — `_bmad-output/initiative-architecture-hardening/tickets/epic-dead-surface-and-gates/tickets/notes-frontend-path-contract-check.md` (inventory, CI job names, design choice, prototype numbers)
- prd — `docs/prd/epic-17-architecture-hardening.md`, Story 17.3
- plan — `docs/plans/architecture-review-2026-11-actions.md`, items 2 and 8
- plan — `docs/plans/test-fidelity-proposal-2026-11.md`, P2.2 (the validator that reuses the matcher)
- architecture — `docs/architecture/module-map.md`, Placement (API calls only in `services/`)

## Notes

- Decision: 2026-11-07, owner. 17.1 and 17.3 ship together in one PR (one production deploy). Build them on one branch, 17.1 first.
- Decision: 2026-11-07, Winston. Stop rule: if the allow-list grows past about 15 entries, stop and raise it with the owner. The fallback is the TypeScript compiler API.
- Decision: 2026-11-07, Winston. The matcher is TypeScript, shared with the 17.0c MSW validator, rather than Python beside `scripts/ci/`.
- Decision: 2026-11-07, owner. Converted from the v6 story file `17-3-frontend-path-contract-check.md`, which was deleted. Its code-level findings are in the notes file.
- Risk: medium. A false positive blocks every frontend PR in a required check; a false negative lets drift ship.
- Decision: 2026-11-08, owner. The calls this story froze (13 files in `api-layer-baseline.txt` at the end of the build) move into the service layer in a follow-up, entry 3b of this epic, built right after 17.5. This story's Boundaries are left as built.
