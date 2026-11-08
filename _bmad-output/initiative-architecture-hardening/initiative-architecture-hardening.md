---
type: initiative
title: "BATbern architecture hardening"
sources:
  - docs/prd/epic-17-architecture-hardening.md
  - docs/prd/epic-16-mail-center.md
companions:
  - docs/plans/architecture-review-2026-11-actions.md
  - docs/plans/test-fidelity-proposal-2026-11.md
  - docs/architecture/module-map.md
  - docs/architecture/ADR-016-service-directions-and-service-to-service-calls.md
  - docs/architecture/ADR-002-generic-file-upload-service.md
  - docs/architecture/ADR-003-meaningful-identifiers-public-apis.md
  - docs/plans/ems-modularization-extension-points.md
  - _bmad-output/implementation-artifacts/16-8-shared-composer.md
after: []
risk: high
---

# BATbern architecture hardening

## Description

The next feature lands in the right place without anyone having to remember where that is. The platform stops carrying code and infrastructure that nothing calls. The three services call each other in one direction over one transport. The public API follows one set of conventions. EMS gets walls that a build can check, and tests stop replacing the part of reality where the defect sits. Every rule this initiative adds is enforced by a gate, not by a document, and every story leaves the code closer to the module map than it found it. Sources: Epic 17 (stories 17.0a to 17.16) and the 16.8c clean-up from Epic 16, which sits in Epic 17's order after 17.1.

## Outcome

Developers and agents working on BATbern find one endpoint, one transport and one module boundary to extend, and CI rejects a change that breaks them. The signal is that the gate baselines (mock ratchet, Spectral, ArchUnit store, frontend layer baseline, EMS module script) only fall from the 2026-11-07 measurement at develop `89b3618f`, and that each story ends with them smaller than at its start or says why.

## Requirements

- R1 (17.0a): one written rule for what a test may replace, and the assertion rules, are in `coding-standards.md`, the `bmad-build` override and project-context. A CI ratchet fails a PR that raises any per-file count of own-code `vi.mock`, in-process `@MockitoBean` or class-level `@Transactional` write ITs.
- R2 (17.0b): every S2S client is tested against the provider's OpenAPI, and a request outside it fails (the #1072 fixture). Every service has a production security-chain test. A joined `To:` line fails a test without SES credentials.
- R3 (17.0c): frontend component tests run on default MSW handlers for every operation, typed from the generated types. A request outside the spec fails with the validator's message. The 10 worst mock-heavy files are converted.
- R4 (17.0d): production-shaped fixtures are the default on frontend and backend: unicode, case, namesakes, page limits, sort ties, every registration status. The #1034 and #1104 regressions run on them.
- R5 (17.1): only called endpoints remain. The `speakers-api` and `attendees-api` specs and 6 orphan operations are removed in spec and code, and so is the frontend publish-preview chain. The operation count drops by 6.
- R6 (17.2): the SpeakerCoordination and AttendeeExperience services and stacks and the EventBridge bus are gone from production and the repo (checked live). The 2 in-process `@EventListener`s still receive their events.
- R7 (17.3): a required CI check fails when frontend code calls a method and path that no spec defines. One importable spec matcher serves CI and the 17.0c harness. Frontend HTTP calls sit in `web-frontend/src/services/`: the baseline of files outside it that call HTTP ends empty, or each remaining line says why.
- R8 (17.4): Spectral, with a repo ruleset and a violation baseline, fails a PR only on new convention violations. Every added operation gets a comment naming its 3 nearest existing operations.
- R9 (17.5): ArchUnit runs in the test task of CUMS and EMS (PCS calls no downstream service and has nothing to freeze; narrowed by the owner 2026-11-08). The EMS package rules and the no-downstream-client rule are frozen, and the store can only shrink. Violations that need no 17.12 design or cross-service change are fixed in 17.5, and each group left frozen names its owning story.
- R10 (epic-16 16.8 Clean-up, 16.8c): the old compose components and the old preview, send and history endpoints and specs are deleted, including newsletter status and retry. The full list is in the companion `16-8-shared-composer.md`, Dev Notes "Endpoints retired in 16.8c". Every organizer send goes through `/events/{c}/mails`.
- R11 (17.6): no service calls another service's `/api/v1` URL. The 12 machine calls sit under `/internal`, work with and without a user JWT, and are gone from the public specs.
- R12 (17.7): CUMS owns a user-reference merge contract that EMS and PCS implement. The Mail Center pulls merge and logo requests from CUMS. No CUMS class references EMS.
- R13 (17.8): EMS has no PCS client. The topic canvas shows partner topics with clusters, and calendar RSVPs reach PCS meetings within one poll interval.
- R14 (17.9): tasks, app settings and user settings each have one endpoint to change state. `analyze-abstract` sits under the event and is event-scoped.
- R15 (17.10): no `me` / `{username}` twins remain for pictures and profile. One speaker path key is used across the specs. `batch_registrations`, `enroll-stakeholders` and `my-subscription` are folded into their resources.
- R16 (17.11): one upload contract, `/api/v1/uploads/{purpose}` in shared-kernel, carries `company-logo` live, including logo requests. The old `/logos` upload operations are gone, and EMS no longer reads or writes `logos`.
- R17 (17.12): the owner accepts `docs/architecture/ems-modularization.md`, with target packages, move order, release slicing for 17.13 to 17.16 and a defined freeze baseline for the full layering.
- R18 (17.13): `EventController` holds no registration endpoints, and all registration paths behave as before.
- R19 (17.14): `GlobalExceptionHandler` imports no domain class, and every exception keeps its status and error code.
- R20 (17.15): mail core depends on no mail-center class. Delivery status, "What went out" and the moderator Reply-To behave as before.
- R21 (17.16): event core depends on no L3+ module, and all workflow transitions behave as before.

## Constraints

- C1 (17 Rules): every acceptance criterion stating an architectural property names the gate that proves it (ArchUnit, Spectral, contract check, test). A property without a gate is not done.
- C2 (17 Rules): each story file opens with the 5-line architecture note from `module-map.md`, confirmed in the build plan's Design Notes before the first test.
- C3 (17 Rules): a story that claims to remove EMS violations re-runs `scripts/architecture/ems-module-deps.py` and `ems-module-target.py` and records the before and after numbers.
- C4 (17 Decisions 4, Rules): API conventions are enforced by breaking changes with no deprecation period. Spec, generated types, frontend callers, Bruno and Playwright change in the same PR.
- C5 (17 Rules, CLAUDE.md): a PR to `develop` deploys to production. One story is one PR is one deploy, and small ones are batched only with the owner's agreement. Branch from `origin/develop`, push, then ask the owner before opening the PR.
- C6 (17 Status): every story needs the owner's go before it starts.
- C7 (17 Rules, 17.2 AC5): infrastructure changes never deploy on an event day and must be deploy-clean (no CFN resource collision).
- C8 (17 Decisions 5, ADR-016): S2S calls forward the user's JWT when present and never require it. `/internal` authorizes by operation. Services call in one direction.
- C9 (17 Decisions 6, ADR-003 amendment 2026-11): UUID path parameters appear only below a meaningful parent.
- C10 (17 Decisions 7, ADR-002 decision 2026-11-07): there is one upload contract, `/api/v1/uploads/{purpose}`, and each service has its own `uploads` table.
- C11 (17 Decisions 2): in-process Spring events (`ApplicationEventPublisher`) stay when the EventBridge bus goes.
- C12 (epic-16 Constraints, ADR-015): no test, Bruno or Playwright run sends real mail. Send paths are verified on local dev (`/dev/mails`). Mail bodies are PII.
- C13 (CLAUDE.md Flyway, 17.11 AC3): an applied migration is never edited. No EMS migration drops the CUMS-owned `logos` table without the owner's go.
- C14 (17 Decisions 8): test fidelity comes first where it protects later stories. 17.6 waits for 17.0a and 17.0b; 17.9 and 17.11 wait for 17.0c and 17.0d.
- C15 (17 Rules, owner 2026-11-08): leave the architecture cleaner. Every story moves code to the module `module-map.md` assigns, splits or redesigns what stands in the way, and fixes the violations its own gate or change exposes inside its service. A gate story freezes only what needs a later story's design or a cross-service change and names that story per frozen group. A baseline or store committed at the end of a story is smaller than at its start, or the story says why. "No production code change" is never a boundary.

## Done when

- Every gate the initiative adds (mock ratchet, S2S contract tests, MSW validator, FE path contract check, Spectral, ArchUnit) runs in a required CI job on `develop`.
- Production runs three services and no EventBridge bus, and the 6 orphan operations and the 16.8c endpoints answer 404 (checked live).
- The ArchUnit store holds zero EMS→PCS and zero CUMS→EMS client dependencies, and every S2S call goes through `/internal`.
- The frontend layer baseline (`web-frontend/scripts/api-layer-baseline.txt`) is empty, or each remaining line says why.
- Every baseline and store still holding entries names the story or the owner decision behind each group.
- The Spectral baseline has shrunk by the entries of 17.6, 17.9, 17.10 and 17.11, and `company-logo` uploads run on `/uploads/company-logo` in production.
- The EMS module script, re-run, shows fewer violating pairs and references than the 2026-11-07 baseline (28 pairs, 100 references), with the numbers recorded.
- Each story reached production through its own PR and was verified there.

## Boundaries

- Out: the P5 pre-deploy gate (parked until 17.1 to 17.5 and 17.0a to 17.0d, `docs/plans/pre-deploy-gate-plan.md`).
- Out: the five upload purposes after `company-logo`, the remaining EMS module moves (planning, publishing, ai) after 17.12, and the follow-up reviews (action list item 9). Each becomes a story only after its predecessor proves the pattern.
- Out: 16.12 and 16.13 (archived), and converting every existing `@MockitoBean UserApiClient` at once (gradual, under the 17.0a touch rule).
- Out: the target storage design in `docs/prd-enhanced.md` §4.2 (per-type buckets, ClamAV, quotas). 17.11 unifies the upload API on the one existing content bucket. §4.2 waits for the owner's strike/defer/rewrite decision (#1210).
- Must not change: user-visible behaviour outside the breaking API stories. The registration wizard, the Mail Center sends, the topic canvas and the workflow transitions behave as before.
- Tracer path: 17.1 clears the dead surface; the gates (17.3, 17.4, 17.5) and the test harness (17.0a to 17.0d) baseline it and fix what needs no later design (17.5's EMS moves, 3b's frontend calls); the decoupling and convention stories shrink the rest under the gates; 17.12 designs the EMS restructuring from the store 17.5 leaves.

## References

- prd — `docs/prd/epic-17-architecture-hardening.md`, sections Decisions taken, Rules for every story, Story order, Stories
- prd — `docs/prd/epic-16-mail-center.md`, Story 16.8 (clean-up), Decisions taken 2026-11-01 item 2, Constraints
- prd — `docs/prd-enhanced.md` (master PRD; it has no FR for this initiative; §4.2 is out, see Boundaries)
- plan — `docs/plans/architecture-review-2026-11-actions.md` (action list, measured baseline)
- plan — `docs/plans/test-fidelity-proposal-2026-11.md` (P1 to P4, the 57-bug classification)
- architecture — `docs/architecture/module-map.md`, `docs/architecture/ADR-016-service-directions-and-service-to-service-calls.md`, `docs/architecture/ADR-002-generic-file-upload-service.md` (decision 2026-11-07), `docs/architecture/ADR-003-meaningful-identifiers-public-apis.md` (amendment 2026-11)
- plan — `docs/plans/ems-modularization-extension-points.md` (input to 17.12)
- story — 17.1 and 17.3 are v7 story files in `_bmad-output/initiative-architecture-hardening/tickets/epic-dead-surface-and-gates/tickets/` (converted from the v6 files 2026-11-07)
- story — `_bmad-output/implementation-artifacts/16-8-shared-composer.md`, Part 16.8c, AC18, Dev Notes "Endpoints retired in 16.8c"

## Notes

- Assumption: the 16.8c gate "one release after 16.8b" is met. 16.8b went live 2026-11-01 (#1174), and #1180, #1189 and #1194 have reached production since. Only the owner's go remains.
- Decision: 2026-11-07, owner. 16.8c also retires `GET /events/{c}/newsletter/sends/{id}/status` and `POST …/retry`. Retry lives on `POST /events/{c}/mails/{mailKey}/retry`.
- Decision: 2026-11-07, owner. Five epics, cut by distinct outcome in the source's order (protect, decouple, restructure). 16.8c sits in epic 1. One epic of 21 stories was declined.
- Decision: 2026-11-07. Entry ids keep the 17.x story number (`0a` to `16`); 16.8c is `168c`, because ids allow no dot.
- Decision: 2026-11-07. No `bmad-architecture` run: the shared decisions are already recorded in ADR-016, the ADR-003 amendment and the ADR-002 decision of 2026-11-07, and the adopting epics cite them.
- Decision: 2026-11-07, tree check. Epic 4 waits on epic 3: 17.6 moves `PATCH /users/{u}/profile`, which 17.10 merges, and 17.7 drops the logo-request push that 17.11 rebuilds. Epic 5 waits on epics 2, 3 and 4: 17.13 moves tests that the 17.0a ratchet counts, 17.7 and 17.8 change mail-center code that 17.15 splits, and 17.10 folds registration endpoints that 17.13 moves. Entry-level `after` is pinned when stories are planned.
- Decision: 2026-11-07. Epic 2's wait on epic 1 binds only 17.0c, which needs 17.3. 17.0a, 17.0b and 17.0d start at once (source decision 8). 17.1 and 17.3 lead epic 1.
- Decision: 2026-11-07. Epic 1 is the platform-baseline epic: the gates and the dead-surface clean-up that every later epic measures against. The ratchet 17.0a sits in epic 2, which starts in parallel.
- Decision: 2026-11-08, owner. Epic 17 exists to make the architecture cleaner, not to document or freeze it ("With every story it should get better"). New constraint C15; R7 and R9 extended; gate stories fix in-service violations that need no later design and name the owning story of every frozen group. Applied to the PRD (Rules for every story, Story 17.5) and `_bmad/custom/bmad-build.toml` by the owner's session; replanned across the five epics the same day.
- Decision: 2026-11-08, owner. The frontend calls 17.3 froze move into the service layer, baseline to zero where possible: entry 3b in epic 1, built right after 17.5.
