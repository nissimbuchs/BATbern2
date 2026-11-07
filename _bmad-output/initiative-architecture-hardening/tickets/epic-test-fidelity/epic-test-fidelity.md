---
type: epic
title: "Tests run against real contracts and real-shaped data"
parent: initiative-architecture-hardening
covers: [R1, R2, R3, R4]
sources: []
companions: []
after: []
refined: true
risk: medium
---

# Tests run against real contracts and real-shaped data

## Description

Makes tests replace only what sits behind a process boundary. One written mocking and assertion rule, plus a ratchet, only lets mock counts fall (17.0a). S2S clients are tested against the provider's spec, and every service gets a production security-chain test (17.0b). Frontend tests run on a spec-validating MSW harness (17.0c). Production-shaped fixtures are the default (17.0d). Source: the test-fidelity proposal.

## Outcome

Developers and agents get tests that fail when a caller is wrong. The signal is that the ratchet baseline falls, and that the #1072, #1176, #1034 and #1104 fixtures fail without the fix.

## Requirements

- R1 (parent R1): one written rule for what a test may replace, and the assertion rules, are in `coding-standards.md`, the `bmad-build` override and project-context. A CI ratchet fails a PR that raises any per-file count of own-code `vi.mock`, in-process `@MockitoBean` or class-level `@Transactional` write ITs.
- R2 (parent R2): every S2S client is tested against the provider's OpenAPI, and a request outside it fails (the #1072 fixture). Every service has a production security-chain test. A joined `To:` line fails a test without SES credentials.
- R3 (parent R3): frontend component tests run on default MSW handlers for every operation, typed from the generated types. A request outside the spec fails with the validator's message. The 10 worst mock-heavy files are converted.
- R4 (parent R4): production-shaped fixtures are the default on frontend and backend: unicode, case, namesakes, page limits, sort ties, every registration status. The #1034 and #1104 regressions run on them.

## Constraints

- The parent's C1 (named guard), C2 (architecture note), C5 (a PR deploys to production), C6 (owner go per story) and C14 (fidelity first) bind every story.
- C12 binds 17.0b: no test sends real mail; the `EmailService` test runs on a mocked `SesClient`.
- Agent-context files (`CLAUDE.md`, project-context, `_bmad/custom/*.toml`) change only in 17.0a, where the owner asked for the rule to be written there.

## Done when

- The mocking and assertion rules are in `coding-standards.md`, the `bmad-build` override and project-context, and the contradicting line is gone.
- The ratchet runs in a required CI job with a committed baseline that can only fall.
- Every S2S client has a contract test against a spec of its provider, `/internal` calls included, and every service has a production-chain test.
- The MSW harness, with real i18n and production-shaped fixtures, is the default for frontend tests, and the 10 worst files are converted.
- Each story reached production through its PR, and CI on `develop` runs the new checks.

## Boundaries

- Out: converting every existing `@MockitoBean UserApiClient` (gradual, under the touch rule); the P5 pre-deploy gate (parked).
- Only 17.0c waits on epic 1 (17.3's matcher). 17.0a, 17.0b and 17.0d start at once.

## References

- parent — `_bmad-output/initiative-architecture-hardening/initiative-architecture-hardening.md`, section Requirements (R1, R2, R3, R4), Constraints, Notes (Decision lines)
- prd — `docs/prd/epic-17-architecture-hardening.md`, Rules for every story, Story order
- reference — `docs/plans/test-fidelity-proposal-2026-11.md`

## Notes

- Source conflict: R2 says every S2S client is tested against the provider's OpenAPI, but 13 of the client calls go to `/internal/**`, which no spec in `docs/api/` describes (checked 2026-11-07: mail-center merge and logo requests, user merge, merge preview, primary email, email owners, internal users, partner-meeting RSVPs). 17.6 adds 12 more.

- Decision: 2026-11-07, owner. The `/internal` calls get provider specs in `docs/api/internal/` (outside the frontend path check); 17.0b splits into 0b1 (public-API clients), 0b2 (`/internal` specs and their clients) and 0b3 (production setup in tests). 17.0c splits into 0c1 (harness) and 0c2 (10 files converted).
- Decision: 2026-11-07. The tracer bullet is 0a: the rule written down and the ratchet in a required CI job. 0b before 0c because epic 3 (17.6) needs it first; 0d waits on 0c1 because its fixtures are the harness default. A refactor sweep closes the epic.
- Decision: 2026-11-07, owner. 17.0a and 17.2 ship in one branch and one PR; the PR deploys on 17.2's date, which is not an event day.
