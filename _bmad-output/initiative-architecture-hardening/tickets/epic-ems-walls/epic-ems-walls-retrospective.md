---
epic: epic-ems-walls
date: 2026-11-15
verdict: accepted-with-open-items
criteria: declared
headless: true
---

# Retrospective: epic-ems-walls (Epic 5, "EMS has walls a build checks")

## Epic summary

Epic file `epic-ems-walls.md`, covers R18, R19 (epic R1, R2). Narrowed by the owner on 2026-11-14 to two targeted refactors. 17.12, 17.15 and 17.16 were dropped before they had entries (`tickets/tickets.toml:1-2`, `architecture-ems-walls/.memlog.md:24`).

| Ref | Title | status / state | Plan baseline | Shipped in |
|---|---|---|---|---|
| 5.13 | Registrations out of EventController | done / done | `f5f052e6a` | PR #1314, squash `9f1faf0de` |
| 5.14 | Domain exception base | done / done | `25ac9e646` | PR #1314, squash `9f1faf0de` |
| (follow-up) | #1315 six pre-existing registration flaws | plan `status: 'built'` | `9f1faf0de` | PR #1316, squash `74dee295e` |

`pending_tickets`: empty (`tickets.py status`, I checked). No ticket still at `built`. The #1315 plan is a top-level plan without a tree entry, so it does not count toward `pending_tickets`, but its frontmatter still reads `status: 'built'` (`plan-gh-1315-registration-flaws.md:6`, I checked). This contradicts the orchestrator's "every plan file has status done" (see F9).

**Ranges.** Both plan baselines (`f5f052e6a`, `25ac9e646`) are feature-branch commits of `chore/epic5-replan` that the squash merge left off `develop`. Per-plan ranges therefore run inside PR #1314 only (commits listed by `gh pr view 1314`). For `git_evidence.py` I used the develop squash ranges:
- `a707357c5..9f1faf0de` (PR #1314, 5.13 + 5.14): 1 commit, 103 files, 0 merges.
- `9f1faf0de..74dee295e` (PR #1316, #1315 follow-up): 1 commit, 29 files, 0 merges.
Per-story attribution inside #1314 comes from the PR's 16 commit list: 5.13 = `2328247ec 42efc02bc 91d4afcfb 25ac9e646`, 5.14 = `8380e0a6d 149076373 b46ae69b4 9efa90dff f69744c65 8f419cee3 b6281e772 c154dd273 7886dfb25 14acdd6bb`, plus `f5f052e6a` (replan) and `860b8ba6a` (Bruno fix).

**Evidence inventory.**
- Read in full: epic file, `tickets.toml`, both `find` entries, both story plans, `plan-gh-1315-registration-flaws.md`, `architecture-ems-walls/.memlog.md`, epic `.memlog.md`, PR #1314 and #1316 bodies, commit lists and review comments.
- Story files: none (both `story_file: null`, entries never pulled; `description`/`verify` read from `tickets.toml`).
- Run folders: `architecture-ems-walls/` holds only a memlog (the Winston run closed without a spine, `.memlog.md:25`).
- Session logs: not available to this retro. Process-lesson analysis from session logs is skipped. The memory note `epic17-overnight-2026-11-15.md` (owner's auto-memory) was used as a dated run record.
- Previous retrospective: no previous retrospective file existed (none of the initiative's epics had one before this batch).

## Measurements (I checked, 2026-11-15, on the worktree at `74dee295e` + uncommitted docs)

| Measure | Epic start | After #1314 | Now | Source |
|---|---|---|---|---|
| `EventController.java` lines | 2334 (`a707357c5`) | 1703 | 1703 | `git show <rev>:… \| wc -l` |
| `GlobalExceptionHandler.java` lines | 1486 | 627 | 627 | same |
| GEH `@ExceptionHandler` count | 57 (plan) | 22 (plan) | 22 | grep, I checked |
| `RegistrationService.java` lines | 925 | 1001 | 1209 | same |
| `RegistrationController.java` lines | n/a | 364 | 301 | same |
| Module script, violating pairs / refs | 26 / 92 (plan 5.13 Design Notes) | 23 / 71 (PR #1314 table) | 23 / 71 | `ems-module-{deps,target}.py`, re-run read-only |
| `event_core -> registrations` | 12 (EventController 10) | 4 (EventController 2) | 4 (EventController 2) | same |
| GEH violating refs | 13 | 0 | 0 | same |
| EventController violating refs | n/a | n/a | 16 of 71 | same (7 pairs: sessions 5, publishing 3, registrations 2, media 2, topics_ai 2, speakers 1, analytics 1) |

Per-story module numbers are recorded: 5.13 `26/92 -> 26/84` (`story-registrations-out-of-eventcontroller-plan.md:125`), 5.14 `26/84 -> 23/71` (`story-domain-exception-base-plan.md:100`). #1316 added no violating edge: totals unchanged at 23/71 (I checked).

GEH imports only shared-kernel types (`GlobalExceptionHandler.java:3-9`: `ErrorResponse`, `ClientErrorClassifier`, `InvalidStateTransitionException`, `NotFoundException`, `ValidationException`, `SpeakerWorkflowState`, `CorrelationIdGenerator`). The only EMS classes it names are `DomainException` and `DomainErrorResponse`, plus the three module advices in a Javadoc comment (`:39-40`). I checked.

ArchUnit rules (I checked they exist; I did not run them):
- `architecture/EmsEndpointPlacementTest.java:19-27`: `noRegistrationEndpointIn(ROOT, "EventController")` (5.13) and `controllerUsesNoRepository(…"RegistrationController", "RegistrationRepository")` (#1316).
- `architecture/EmsExceptionMappingTest.java:18-24`: `globalExceptionHandlerUsesOnlyExceptionBase` and `exceptionsExtendDomainException` (5.14).
- Fixture tests firing and passing: `EmsModuleRulesFixtureTest.java:149,160,173,186,212,227,238,249`.
- Frozen store: `archunit_store` unchanged across `a707357c5..74dee295e` (empty diffstat); the one non-empty store file has 2 lines (mail core, owner left without story, epic Boundaries line 38).

## Findings

### Spec-to-implementation reconciliation

- **F1. Done-when 5 "own PR per story": deviated, owner-accepted.** Both stories shipped in PR #1314. The PR body states "at the owner's request ("push and open pr")"; the run record says "One PR for both (owner said "open pr"); the epic's "own PR per story" is deviated from on his instruction" (`memory/epic17-overnight-2026-11-15.md`). Disposition: **accept** (recorded deviation, do not re-flag). Prevention: the epic Done-when should say "own PR per story unless the owner batches them", since batching is a recurring owner choice.
- **F2. Production verification of 5.13 is partial.** Ticket verify asks "a registration on www.batbern.ch works" (`tickets.toml` entry 13). The run record says: prod probes of the new handler and anonymous 401/404/400 were done; "Not done: a real registration on www (would create prod data + mail; owner)" (`memory/epic17-overnight-2026-11-15.md`, 02:25 entry). PR #1314 itself says the live check "is done after deploy" without a later record. For #1316 no live check is recorded either; the build+deploy run `43702099801` succeeded (I checked `gh run list`). Disposition: **defer** to the owner (A1). Prevention: a verify item that needs production data or real mail should name its substitute (a probe, or an owner-run registration) at refinement.
- **F3. Accepted behaviour deviations from "status codes unchanged".** 5.13: implementing the generated `RegistrationsApi` made malformed event/registration codes, `page<1` and `limit>100` answer 400 instead of 404 or clamping (`story-registrations-…-plan.md:174`, PR #1314 body; 8159/66 stored codes checked against the patterns per the plan). 5.14: `AgendaFullException` 500 -> 409 and `InvalidSlotAssignmentException` 500 -> 400 in their own commit `f69744c65` (`story-domain-exception-base-plan.md:58,96`). Disposition: **accept** (deliberate, documented, valid requests unchanged).
- **F4. Outcome wording vs as-built: `EventController -> registrations` is 2, not gone.** The epic Outcome says the "10 references" violation is gone; 2 remain (`RegistrationService`, `WaitlistPromotionService`), recorded in the 5.13 Design Notes as the L2 -> L3 deviation the dropped 17.16 would have inverted (`plan:172`). R1 itself (no registration endpoints) holds. Disposition: **accept**; the remaining refs belong to the EventController follow-up story (`_bmad-output/backlog/story-eventcontroller-keeps-only-http.md`).
- **F5. 5.14 scope stops at `exception/`.** Six EMS exceptions outside the package (`AlreadySentException`, `UserMergeService.MergeBlockedException`, `PublishingService.PhaseConflictException`, `PublishValidationException`, `InboundMailStore.DuplicateInboundMailException`, `MailDeliveryStatusService.UnreadableEventException`) do not extend `DomainException` (`story-domain-exception-base-plan.md:28-31`). The ArchUnit rule `exceptionsExtendDomainException` covers only `exception/`. This deferred item is in no tracker: `deferred-work.md` has no entry for it (I checked by grep). Disposition: **defer**, but capture it (A2).

### God-class / size growth

- **F6. `RegistrationService` grew 925 -> 1209 lines (+284) across the epic.** +76 from 5.13 (`listForEvent`, counts, delete guard), +208 from #1316 (transition table, `changeStatus`, `deleteByOrganizer`, `confirmRegistration`). Measured with `git show` at `a707357c5`, `9f1faf0de`, `74dee295e`. The memlog already named it a hotspot at 925 (`architecture-ems-walls/.memlog.md:23`). The growth is the intended direction (logic out of controllers), but the epic moved the size problem one file over without a size signal. Disposition: **defer** (A5). Prevention: when a refactor moves logic into a service, record the receiving file's size before and after, the same way the epic recorded the source file.
- **F7. `EventController` still 1703 lines with 16 violating refs.** I measured 16 of 71 (table above). The orchestrator's backlog story states "18 of the 71 references" (`story-eventcontroller-keeps-only-http.md:22`), but its own table sums to 16 (5+3+2+2+2+1+1, `:29-37`). Disposition: **fix now** in the backlog text (A3); the refactor itself is the follow-up story.

### Pattern divergence

- **F8. `RegistrationController` still reads `EventRepository` directly** (`RegistrationController.java:59,289`, I checked). #1316 removed `RegistrationRepository` and guarded that with an ArchUnit rule, but the rule names one repository only. The same pattern is what the EventController follow-up story forbids ("holds no repository", `story-eventcontroller-keeps-only-http.md:51`). Disposition: **accept** for now, fold into A5's direction note. Prevention: name rules by layer (controller -> any repository) rather than by one class pair when the intent is general.

### Bookkeeping (verification-gap lens over the plans)

- **F9. Plan frontmatter out of date after later fixes.**
  - `plan-gh-1315-registration-flaws.md:6` still `status: 'built'` although PR #1316 merged and closed #1315 (`gh issue view 1315`: CLOSED 2026-11-15T10:39:08Z).
  - `story-registrations-out-of-eventcontroller-plan.md:20-62` lists six `deferred` items; all six were fixed by #1316 (PR body items 1-6), no `resolved` marker.
  - `story-domain-exception-base-plan.md:21-27` lists the Bruno `notifications-api/05` failure as deferred; `860b8ba6a` in PR #1314 fixed it (now `markedCount` 0, `05-mark-many-read.bru:31`).
  Disposition: **fix now** (A4). Prevention: a PR that resolves a plan's deferred item updates that plan's frontmatter in the same PR.

### Diff-scope lenses (inline, narrowed)

Narrowing: `bmad-review` was not invoked (orchestrator rule). I ran the adversarial, edge-case and verification-gap lenses inline over (a) files touched by two or more of 5.13, 5.14, #1316 and (b) items the plans' review logs deferred. Overlap set: 5.13 and 5.14 share `RegistrationService.java`, `EmsModuleRules.java`, `EmsModuleRulesFixtureTest.java`, `module-map.md`; #1316 additionally reworks `RegistrationController.java`, `RegistrationExceptionHandler.java`, `RegistrationCapacityExceededException.java`, `DomainExceptionResponseTest.java`, `EmsEndpointPlacementTest.java`, `RegistrationControllerIntegrationTest.java`, `06-backend-architecture.md`, ratchet baseline. Files outside that set were not reviewed by this retro.

- **Boundary 5.14 -> #1316 (clean, checked).** #1316's new exceptions follow 5.14's contract: `RegistrationAlreadyProcessedException` and `RegistrationStatusTransitionException` extend `DomainException` (`:11`, `:12`), have rows in `DomainExceptionResponseTest` (4 matches, I checked), and the capacity refusal reuses the 5.14 advice via a factory. The 5.14 gate made this boundary safe without a session seeing both sides.
- **Boundary 5.13 -> #1316 (clean, checked).** All six 5.13 deferred items were fixed by #1316 (PR body, issue #1315 closed). The 5.13 review's own deferral of "controller still writes via repository" is now enforced by the new ArchUnit rule (`EmsEndpointPlacementTest.java:24-27`).
- **F10. Transaction-rollback of status change + promotion is untested by design.** #1316 states a failing promotion cannot be provoked without an own-code mock; the guarantee rests on the `@Transactional` boundary (`plan-gh-1315…:96-99`, PR #1316 "Not tested"). Disposition: **accept** (consistent with the mock-at-boundary rule).
- **F11. Create path now rolls back on a CUMS lookup failure.** The confirmation mail moved into `RegistrationService.createRegistration`'s transaction; a CUMS failure now loses the registration instead of keeping it without a mail (`plan-gh-1315…:100-102`, PR #1316 "Behaviour changes"). Disposition: **accept** (documented, owner-approved PR), but it is the one behaviour change in this epic that can turn a CUMS outage into lost anonymous registrations. Listed as an open question.
- **F12. Playwright on the 5.14 build is not recorded.** PR #1314 records Playwright 7/7 without naming the build; the 5.13 plan records it on `42efc02bc`/`91d4afcfb` (`plan:194`), the 5.14 plan records Bruno only (`plan:168`). #1316 ran the registration Playwright specs later on code containing both: 11/12, the failing "cancels a registration" passed 6/6 alone (cause inferred, not proven, PR #1316). Disposition: **accept** (5.14 changes no UI path; error bodies pinned by the capture test). Single flake: no issue per project rule.

### Duplication map

No duplicate solutions found in the overlap set. #1316 removed the one recorded duplication (three copies of confirmation-mail sending -> `RegistrationConfirmationSender`, `plan-gh-1315…:82`).

## Behavior verification

Runtime behaviour was not exercised by this retro (no Gradle, Bruno or Playwright runs, by orchestrator rule). Recorded by the build, not re-checked:
- 5.13: full EMS suite 2808 tests 0 failures; Bruno 3 collections 59/59 requests; Playwright 7/7 on local dev (`story-registrations-…-plan.md:194`).
- 5.14: full EMS suite 2873 tests 0 failures; capture test unchanged between `b46ae69b4` and `9efa90dff`; Bruno 22/23 then the 23rd fixed by `860b8ba6a` and 11/11 (`story-domain-exception-base-plan.md:97,168`, PR #1314).
- #1316: full EMS suite 2951 tests 0 failures; Vitest 530/530; Bruno 59/59; Playwright 11/12 (PR #1316).
- Production: PR #1314 build+deploy green, new handler live, anonymous probes 401/404/400 with unchanged body shape; no real registration (`memory/epic17-overnight-2026-11-15.md`). PR #1316 deploy run green; no live check recorded.

## Previous-retro follow-through

No previous retrospective file existed (first batch of retrospectives for this initiative). Nothing to follow through.

## Action items

All proposed, none applied (headless).

| # | Action | Owner | From |
|---|---|---|---|
| A1 | Decide the 5.13 production check: run one real registration on www.batbern.ch (own address, then cancel) or record an explicit waiver. Same for the #1316 confirm/PATCH paths. | Owner | F2 |
| A2 | Capture the six EMS exceptions outside `exception/` (F5 list) as an issue or backlog story, including widening `exceptionsExtendDomainException` beyond `exception/`. | Sofia (bmad-agent-po) | F5 |
| A3 | Correct `story-eventcontroller-keeps-only-http.md:22` from 18 to 16 references (its own table sums to 16; script re-run gives 16). | Orchestrator | F7 |
| A4 | Plan bookkeeping: set `plan-gh-1315-registration-flaws.md` to `done`; mark the six 5.13 deferred items resolved by #1316 and the 5.14 Bruno item resolved by `860b8ba6a`. | Sofia | F9 |
| A5 | In the EventController follow-up story, add `RegistrationService` size (1209) as a measured before value and a controller -> any repository rule as a candidate gate. | Sofia | F6, F8 |
| A6 | Process: refinement states the production substitute for verify items that would create prod data or send real mail; epic Done-when allows owner batching of PRs. | Sofia | F1, F2 |

Process lessons (no owner action): the capture-test-first pattern of 5.14 (`b46ae69b4` green on the old handler, unchanged through the refactor) is the reason a 1486 -> 627 line rewrite needed no follow-up fix; reuse it for the EventController story (its Done-when already asks for it, `story-eventcontroller-keeps-only-http.md:56`).

## Decision records

Proposed only (headless). None written.

1. **adr-ems-no-modularization** (owner, 2026-11-14): no Spring Modulith, no package moves; Epic 5 keeps targeted splits; 17.12/17.15/17.16 dropped. Hard to reverse in planning terms, surprising given the module map's L0-L6 target, real trade-off (spike: 36 modules / 3505 violations by default detection, 5 modules / 30 violations explicitly annotated). Why: `architecture-ems-walls/.memlog.md:23-24`, commit `f5f052e6a` ("the moves would cost about 350 files and four or more production deploys for no user value"). Only recorded in a memlog and a commit message today.
2. **adr-ems-domain-exception-carries-status** (5.14): domain exceptions carry HTTP status, severity and `details.code` via `DomainException`; `GlobalExceptionHandler` maps the base once; extra-field bodies live in module advices at `@Order(HIGHEST_PRECEDENCE)`; no top-level `errorCode`. Plan `story-domain-exception-base-plan.md:41,143-145`, commit `9efa90dff`. Documented in `module-map.md` and `06-backend-architecture.md` but not as an ADR; the advice-order invariant is the surprising part.

The 5.13 "generated interface brings spec validation" and the #1316 transition table did not pass the test: both are documented in the spec and in `06-backend-architecture.md:1535` and are the ordinary consequence of ADR-006 and an owner product decision respectively.

## Acceptance verdict

**accepted-with-open-items** (criteria declared, epic file Requirements R1, R2 and Done when).

- R1 / DW1: met. No registration mapping in `EventController` (grep, `class EventController implements EventsApi, EventReportingApi, PublicEventsApi`, `:94`); ArchUnit rule plus three fixture tests exist. I checked.
- R2 / DW2: met. GEH imports no EMS domain class; rules and fixtures exist; status and code preservation recorded by the capture test (build, not re-checked); two deliberate status fixes (F3).
- DW3: met. Before/after recorded per story; re-run today matches (23/71, GEH 0).
- DW4: met on the build's record, with documented 400 deviations (F3).
- DW5: deviated (one PR, owner-accepted, F1); production verification partial (F2, open).
- `pending_tickets` empty. No blocking finding. Open items: A1, A2, A4 (tracked here).

## Open questions

- F11: is losing an anonymous registration on a CUMS lookup failure acceptable, or should the mail be sent after commit? The plan forbade inventing an after-commit mechanism; the owner decides.
- F2: does the owner want a real production registration as epic evidence, or are the anonymous probes enough?
- The orchestrator's statement that every plan file has status done does not hold for `plan-gh-1315-registration-flaws.md` (F9).

## Assumptions

- Epic resolved from the folder given by the orchestrator; `tickets.py status` returned 2 rows, both done; `pending_tickets` empty.
- Ranges taken from the develop squash commits because both plan baselines exist only on the deleted feature branch; per-story attribution from PR #1314's commit list.
- Diff-scope review narrowed to the inter-ticket overlap set and deferred items (orchestrator rule); `bmad-review` not invoked.
- Session logs unavailable; the owner's auto-memory run note was used as the dated run record.
- Machine verdict rendered without a human decision: accepted-with-open-items.
- All action items and both decision records are proposals; nothing was applied, no tracker or plan was edited.
