---
initiative: initiative-architecture-hardening
date: 2026-11-15
verdict: accepted-with-open-items
criteria: declared
headless: true
---

# Initiative retrospective: BATbern architecture hardening (Epic 17)

Summary of five headless `bmad-retrospective` runs, one per epic folder (the skill takes one epic at a time). The per-epic documents hold the findings with their sources. This file only aggregates them and adds the initiative-level checks.

## Verdicts per epic

| Epic | Tickets | Verdict | Action items | Retrospective |
|---|---|---|---|---|
| 1 dead-surface-and-gates | 10, all done | accepted-with-open-items | 7 | `tickets/epic-dead-surface-and-gates/epic-dead-surface-and-gates-retrospective.md` |
| 2 test-fidelity | 8, all done | accepted-with-open-items | 8 | `tickets/epic-test-fidelity/epic-test-fidelity-retrospective.md` |
| 3 one-service-direction | 7, all done | accepted-with-open-items | 7 | `tickets/epic-one-service-direction/epic-one-service-direction-retrospective.md` |
| 4 api-conventions-and-uploads | 11, all done | accepted-with-open-items | 9 | `tickets/epic-api-conventions-and-uploads/epic-api-conventions-and-uploads-retrospective.md` |
| 5 ems-walls | 2, all done | accepted-with-open-items | 6 | `tickets/epic-ems-walls/epic-ems-walls-retrospective.md` |

`pending_tickets` was empty for all five epics. Criteria were declared in every epic file. Backlog story 17.17 (exhaustive search, `backlog/`) belongs to no epic, so no run covered it: its plan is `status: done` and it shipped in #1259 (merged 2026-11-08). 17.12, 17.15 and 17.16 were dropped by the owner on 2026-11-14 (R17, R20, R21 retired) and are not counted as unfinished.

## Initiative Done when, against the evidence

| Done when | State | Source |
|---|---|---|
| Every added gate runs in a required CI job | Met | epic 1 retro (frontend gates in `frontend-static` under required `build-frontend`, ArchUnit in EMS and CUMS builds); epic 2 retro (`test-ratchet` feeds `build-frontend`, `build.yml:666-690`) |
| Three services, no EventBridge bus, 6 orphan ops and 16.8c endpoints answer 404 live | Partly: 16.8c 404 recorded in production, the 6 orphan operations only checked locally (#1250), `invite-batch` answers 405 | epic 1 retro, finding 1 |
| ArchUnit store: zero EMS to PCS and CUMS to EMS client dependencies, all S2S over `/internal` | Met (stores 0 bytes, no client calls an `/api/v1` URL) | epic 3 retro |
| Frontend layer baseline empty or justified | Met, 0 entries | epic 1 retro |
| Every remaining baseline entry names its owner | Met except the 2 EMS mail-core ArchUnit lines, still attributed to dropped 17.15 at `docs/architecture/module-map.md:88` | epic 1 retro, finding 2 |
| Spectral baseline shrunk by the named stories, rest owned | Met: 150 at first freeze (epic 1 retro), 124 at the first develop commit epic 4 changed it, 78 now, 77 owner-accepted exceptions plus 1 under #1070 | epic 1 and epic 4 retros |
| EMS module script below 28 pairs / 100 refs | Met: 23 / 71 (orchestrator-measured 2026-11-15, re-run read-only by the epic 5 run) | epic 5 retro |
| Each story through its own PR, verified in production | Not met as written: 38 epic tickets shipped in fewer PRs (the orchestrator list has 16, including #1249 planning and #1316 fixes), owner agreement recorded only for part of the batching; production verification recorded for some stories only | all five retros |

Gate baselines over the initiative (orchestrator-measured 2026-11-15): mock ratchet total 937 to 584, ArchUnit EMS store 79 to 2 lines and CUMS 4 to 0 (epic 1 retro), generated-type copy baseline 72 to 0, `EventController` 2334 to 1703 lines, `GlobalExceptionHandler` 1486 to 627 lines.

## Cross-epic findings

1. **Production verification is the weakest record.** Four of five retros found stories whose plans say "verified in production" is required but record no production check: the 17.1 orphan operations (epic 1), 3.6 and 3.6b probes kept only in session memory (epic 3), 4.9b and 4.10 to 4.10d (epic 4), a real registration for 5.13 (epic 5).
2. **Batch PRs replaced "one story, one PR".** C5 allows batching only with the owner's agreement; the agreement is in the tree for #1311, #1314, and part of #1258 and #1281, not for the rest. #1281 and #1289 exceeded GitHub's 300-file diff limit, so the PR reviewer could not read them (epic 4 retro).
3. **Plan bookkeeping drifts after follow-up fixes.** `plan-gh-1315-registration-flaws.md:6` still says `status: 'built'`, which contradicts "every plan is done". The 5.13 and 5.14 plans and `deferred-work.md:69` still list items that later commits fixed (epic 2 and epic 5 retros). Issue #1317 describes EventForm auto-save tests as skipped, but `4cc8d1adf` un-skipped them; the file still mocks `eventApiClient` and `useAuth` (epic 2 retro).
4. **Gates check names and folders, not the full rule.** The S2S URL guard scans only `client/` folders and ArchUnit rules match class names (epic 3). The mock ratchet does not count `MockAdapter` (11 files), non-`Client` `@Mock` fields (approximately 280 in 93 files, regex count) or skipped tests (epic 2). The 5.14 rule covers only `exception/`, six EMS exceptions elsewhere do not extend `DomainException` (epic 5).
5. **The same baseline and owner-file code exists three times** (`api-lint.ts`, `check-api-type-copies.ts`, `check-s2s-client-urls.ts`; epic 1 retro).
6. **One escaped production defect.** #1308 upload previews were blocked by the CloudFront CSP (`img-src` without `blob:`) until #1312. Local dev runs without that CSP (epic 4 retro).
7. **Spec text lags owner decisions.** Epic 3 file and initiative R12, R13 still describe pull and polling, which the owner dropped on 2026-11-11 (epic 3 retro). The epic 1 Done-when line on frozen-group owners predates the 2026-11-14 drop of 17.15 (epic 1 retro).
8. **Review depth below risk.** All seven epic 3 plans pinned a "quick" review, including the three high-risk tickets (epic 3 retro). A #1286 review comment on a self-clearing alarm (`infrastructure/lib/constructs/ecs-service-alarms.ts:323-340`) merged without triage (epic 4 retro).

## Open items tracked elsewhere

Issues #1317 (EventForm, to be reworded), #1318 (ratchet: EmailService mocks), #1319 (Mail Center WITHDRAWN before open), #1320 (logo-request operations), #1070 (Watch API, owner deciding). Backlog story `_bmad-output/backlog/story-eventcontroller-keeps-only-http.md`: line 22 says 18 of 71 refs, its own table and the epic 5 run's script count give 16. The P5 pre-deploy gate spike is being created by the orchestrator.

## Initiative-level action items (proposed, not applied)

| # | Action | Owner |
|---|---|---|
| I1 | Fix the cheap code defects the retros found: logo Playwright regex `web-frontend/e2e/companies/company-create-logo.spec.ts:93`, alarm window in `ecs-service-alarms.ts:323-340`, `module-map.md:88` owner wording | dev loop |
| I2 | Reconcile specs to owner decisions: epic 3 and initiative R12, R13 (no polling); epic 1 Done when (frozen mail-core lines without owning story) | Sofia, owner confirms |
| I3 | Set `plan-gh-1315-registration-flaws.md` to done and clear fixed items from the 5.13, 5.14 plans and `deferred-work.md`; reword #1317; correct the 18/16 count in the backlog story | Sofia |
| I4 | `bmad-build` override: a plan whose ticket requires production verification cannot be marked done without a recorded production check; PR review comments get a triage line in the plan | owner, `_bmad/custom/bmad-build.toml` |
| I5 | Batch PR rule: owner agreement recorded in the plan, one epic per batch, under the 300-file diff limit | owner |
| I6 | Close gate blind spots: count `MockAdapter`, non-`Client` `@Mock` fields and skips in the ratchet (widen #1318); extend the S2S guard beyond `client/`; widen the 5.14 rule to all EMS exceptions | backlog story |
| I7 | One shared baseline and owner-file module under `scripts/lib` for the three gate scripts | backlog story |
| I8 | Owner decisions: read-only production probe of the 6 removed 17.1 operations; redirect guard and creator check on `/uploads/{purpose}/import` and confirm; primary-email push failure logged only as WARN | owner. **Decided 2026-11-15:** no production probe (owner trusts the local checks); no redirect guard on the upload import; the primary-email push failure stays a WARN. No creator check on import and confirm either |
| I9 | Use `beta.batbern.ch` for frontend changes that depend on CloudFront headers (CSP), per finding 6 | process |

## Narrowing and assumptions

- Headless run. Each epic resolved from its folder path given by the orchestrator. No team discussion.
- `bmad-review` was not invoked (orchestrator rule: document review only). The review lenses ran inline over files touched by two or more tickets per epic and over deferred review items.
- Runtime behaviour was not exercised; no Gradle, Vitest or Playwright run. Production checks are cited as the plans and PRs record them.
- Plan baselines sit on squash-merged feature branches, so diff ranges were taken from develop merge commits and PR branches; several PRs carry work of more than one epic.
- No session logs available. No previous retrospective existed for any epic; all five were written in this batch.
- Decision records: six were proposed across the epic retros, none written (headless).
- Verdict here is the aggregate of the five epic verdicts, no human decision. Initiative closing is Sofia's, confirmed by the owner.
