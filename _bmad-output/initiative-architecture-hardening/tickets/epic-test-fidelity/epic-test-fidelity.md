---
type: epic
title: "Tests run against real contracts and real-shaped data"
parent: initiative-architecture-hardening
covers: [R1, R2, R3, R4]
sources: []
companions: []
after: []
refined: false
risk: medium
---

# Tests run against real contracts and real-shaped data

## Description

Makes tests replace only what sits behind a process boundary. One written mocking and assertion rule, plus a ratchet, only lets mock counts fall (17.0a). S2S clients are tested against the provider's spec, and every service gets a production security-chain test (17.0b). Frontend tests run on a spec-validating MSW harness (17.0c). Production-shaped fixtures are the default (17.0d). Source: the test-fidelity proposal.

## Outcome

Developers and agents get tests that fail when a caller is wrong. The signal is that the ratchet baseline falls, and that the #1072, #1176, #1034 and #1104 fixtures fail without the fix.

## Done when

- The mocking and assertion rules are in `coding-standards.md`, the `bmad-build` override and project-context, and the contradicting line is gone.
- The ratchet runs in a required CI job with a committed baseline that can only fall.
- Every S2S client has a contract test, and every service has a production-chain test.
- The MSW harness, with real i18n and production-shaped fixtures, is the default for frontend tests, and the 10 worst files are converted.
- Each story reached production through its PR, and CI on `develop` runs the new checks.

## Boundaries

- Out: converting every existing `@MockitoBean UserApiClient` (gradual, under the touch rule); the P5 pre-deploy gate (parked).
- Only 17.0c waits on epic 1 (17.3's matcher). 17.0a, 17.0b and 17.0d start at once.

## References

- parent — `_bmad-output/initiative-architecture-hardening/initiative-architecture-hardening.md`, section Requirements (R1, R2, R3, R4), Constraints, Notes (Decision lines)
- prd — `docs/prd/epic-17-architecture-hardening.md`, Rules for every story, Story order
- reference — `docs/plans/test-fidelity-proposal-2026-11.md`
