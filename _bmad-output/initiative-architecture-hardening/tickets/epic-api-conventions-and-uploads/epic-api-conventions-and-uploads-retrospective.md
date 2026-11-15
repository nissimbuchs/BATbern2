---
epic: epic-api-conventions-and-uploads
date: 2026-11-15
verdict: accepted-with-open-items
criteria: declared
headless: true
---

# Retrospective: One way in per resource, one upload contract (epic 4)

## Epic summary

Epic file `epic-api-conventions-and-uploads.md` (Requirements via `covers` R14, R15, R16, R22, R23, R24; Done when lines 23 to 31). `tickets.py status` (run 2026-11-15): 11 of 11 tickets `done`, `pending_tickets` empty, none left at `built`. Every plan has `status: done`.

| Ticket | Plan baseline | Range used (feature-branch commits, squash-merged later) | Shipped in |
|---|---|---|---|
| 4.9 API conventions I | 32e579339 | 32e579339..615636778 (7 commits) | #1281 (with the 4.9 owner additions and 16.8c of epic 1) |
| 4.9 owner additions (`plan-4-9b-contract-tightening.md`) | 615636778 | 615636778..6d8d85ac6 (19 commits; includes 16.8c, cut not possible by subject) | #1281 |
| 4.9b one transition idiom | 1bc1e7abb | 1bc1e7abb..6b362888e (12) | #1286 |
| 4.10 API conventions II | ac006ac1a | ac006ac1a..f5052221 (6) | #1289 (batch with 3.6, 3.6b, 3.8, epic 1 sweep) |
| 4.10c participants, newsletter | f5052221 | f5052221..d1d85bdc (18; includes 3.8 commits) | #1289 |
| 4.10b API conventions III | d1d85bdc | d1d85bdc..bade6e7c (4) | #1289 |
| 4.10d additional-email key, get-or-create | bade6e7c | bade6e7c..589fcb270 (9, end = PR head, inferred) | #1289 |
| 4.11 upload contract, company-logo | c91c1377c | c91c1377c..88524e47a (1) | #1308; `logos` drop #1309 |
| 4.11a profile-picture | 130d70523 | 130d70523..91c6b4ca1 (1) | #1311 |
| 4.11b event-photo | 91c6b4ca1 | 91c6b4ca1..59c369b89 (1) | #1311 |
| 4.11c teaser-image | 59c369b89 | 59c369b89..733c99031 (1) | #1311 |
| 4.11d session-material | 733c99031 | 733c99031..ddc4fb015 (1, end = PR head, inferred) | #1311; follow-up fix #1312 |

Ranges: each plan baseline to the next baseline on the same feature branch; the last plan of a branch ends at that PR's `headRefOid` (from `gh pr view`). All baselines exist locally and are ancestors of their PR head (I checked with `git merge-base --is-ancestor`). `git_evidence.py` ran once per range; output in the session scratchpad, not committed. No range contains merges.

Evidence inventory:
- Read: epic file, initiative file (C4, C5, C15, Done when, close note line 120), `tickets.toml`, all 11 plans plus `plan-4-9b-contract-tightening.md` and `null-vs-optional-options.md` (decided option A), `deferred-work.md`, `handoff-2026-11-09.md`, PR bodies and comments of #1281, #1286, #1289, #1308, #1309, #1311, #1312, the Spectral baseline and owners files at HEAD and at 75a2d3156 / 4b20ac3f2.
- No story files (entries were never pulled; `file = null`). No run folders. No companions listed.
- Missing: session logs (not available to this run; process lessons come only from plans and PRs). Previous retrospective: no previous retrospective file existed (none of the initiative's epics had one before this batch).

## Findings

### Spec-to-implementation reconciliation (Done when)

| # | Done-when line | Status | Evidence |
|---|---|---|---|
| R1 | Old operations gone; frontend, Bruno, Playwright call only new ones | Met | I checked: grep over `web-frontend/src`, `web-frontend/e2e`, `bruno-tests` for 25 retired path shapes finds only comments and Bruno files that pin the old path's 404/405 (e.g. `bruno-tests/file-upload-api/06-removed-logos-path-404.bru`, `event-media-api/27-old-material-upload-path-404.bru`). `make check-frontend-api-paths` green per each PR body (not re-run). |
| R2 | Company logo and logo requests on `/uploads/company-logo` in production; EMS no longer touches `logos` | Met | `logos` dropped by CUMS V31 / EMS V154 (#1309, closes #1307); ArchUnit rule widened to `company_user_uploads` and `logos` (#1309 body). I checked: no `logos` table access in `services/*/src/main` except the applied CUMS V28. Production: "#1309: owner go 2026-11-13 after testing the logo flows live" (recorded by the PR, not re-checked). |
| R3 | Profile pictures, photos, teasers, materials on `/uploads/{purpose}` in production, old operations gone | Met, with one escaped defect (F1) | #1311 merged 2026-11-14; #1312 body: "Follow-ups from the owner's production check of #1311". |
| R4 | One transition idiom in the specs; publishing still reaches the public site | Met for specs; production part not recorded | Owner group T1 gone (I checked owners file at 4b20ac3f2 vs HEAD). Public-site check recorded on local dev only (4.9b plan, Implementation Notes "throwaway UI walk"). No in-repo record of a production publish after #1286. |
| R5 | No e-mail in a public path; the 20 lines of 10b and 11a to 11d gone | Met | I checked: at 4b20ac3f2 groups N1 to N6 and N8 owned exactly 20 lines (N5 4, N6 6, N8 4, N1 2, N2 1, N3 1, N4 2); none of these groups exists at HEAD, and no `no-personal-data-path-param` line remains. |
| R6 | Each story ends with a smaller Spectral baseline; callers in `services/` | Met | I checked the baseline entry count at every develop commit that changed it: 124 (#1258) -> 109 (#1281) -> 105 (#1286) -> 87 (#1289) -> 84 (#1308) -> 78 (#1311). Layer baseline `api-layer-baseline.txt`: 0 entries (I checked). |
| R7 | Each story reached production through its PR and was verified end to end | Partly met | Three batch PRs (F9). Production checks recorded for 4.9 (plan "Live:" line), 4.11 (#1309 body), 11a to 11d (#1312 body). For 4.9b, 4.10, 4.10b, 4.10c, 4.10d the plans and #1289 list the post-deploy checks as expected outcomes; no result is recorded in the repo. The initiative close note (initiative file line 120) states every plan is "done and live". |

Initiative Done-when slice (Spectral): I classified the 78 remaining baseline entries against `api-lint-owners.txt` with a read-only script: 77 sit under owner-accepted exceptions (X1 22, X2 8, X3 8, X4 16, X5 3, X6 8, X7 5, X8 2, X9 3, N7 2) and 1 under issue #1070 (`GET /events/current`, open). No UNOWNED or AMBIGUOUS line. Rules: `uuid-path-param` 54, `no-verb-segment` 23, `no-literal-under-events` 1.

- **S1 (defer).** The 3 company-logo-request lines sit under exception X2 although the owner preferred moving them under `/companies/{companyName}/logo-requests` (deferred-work.md line 116, now issue #1320, open). The X2 comment in `api-lint-owners.txt` still says "recorded in deferred-work". So 3 lines are classified as an accepted exception while their intended fate is a fix. Prevention: a line whose fix is planned belongs to an issue group, not an exception group.
- **S2 (accept).** Plans count the baseline in two units: 4.11 "94 -> 91" and 11a "91 -> 89" are file lines (entries + 7 header lines), 11d "80 -> 78" are entries; develop measured 87 -> 84 -> 78 entries (sources: plans 4.11 Design Notes, 11a Tasks, 11d Implementation Notes). Prevention: cite the number `npm run lint:api` prints.

### Lens: boundaries between tickets (adversarial, edge case)

Narrowed scope: files touched by two or more tickets (200 code paths; top shared: `UploadService.java` 5 tickets, `DomainRouter.java` 7, `UserController.java` 5, `EventController.java` 5, `api-lint-owners.txt` 12) and items the plans and PR reviews left open. Not a full diff review.

- **F1 (escaped defect, fixed).** #1308 (4.11) introduced `URL.createObjectURL` previews; the CloudFront CSP `img-src` had no `blob:`, so every upload preview was blocked in production until #1312 (body item 1: "regression from #1308 ... three confirmed theme-image uploads on BATbern63 in the logs"). 4.11's local verification could not see it: local dev serves the SPA from Vite without the CloudFront response-header policy, and #1308's body records "Not done: browser click-through of logo request approve and theme image". Disposition: accept (fixed). Prevention: a frontend change that loads a new resource kind (blob, worker, new origin) is checked on `beta.batbern.ch`, the existing canary for real CloudFront headers (CLAUDE.md, Beta Frontend Canary).
- **F2 (fix now).** 11a dropped the upload year from every final key, including 4.11's `company-logo` (`CompanyLogoUploadHandler.java:70-77`), but `web-frontend/e2e/companies/company-create-logo.spec.ts:93` still expects `/logos/\d{4}/companies/...`. The spec is local-dev only (`test.skip` line 29), so CI never runs it, and #1311's Playwright list does not include it. I checked: the spec was last changed by #1308. C4 lapse across a ticket boundary. Prevention: when a story changes behaviour of a purpose another story owns, rerun that story's local-dev specs.
- **F3 (accept, closed in-PR).** 11a left an open gap: confirm was not bound to the target the upload was reserved for (11a plan, Implementation Notes). 11b closed it with the shared target binding (`UploadService.java:129-157`, `UploadRecord.isBoundElsewhere`). Because 11a to 11d shipped in one PR, the gap never reached production. Residual: uploads reserved without a target (company-logo, event-theme-image, the session editor path) stay unbound, and confirm does not compare the caller with `createdBy`; the #1308 PR review raised this as "Confirm has no ownership check on the upload (security, low)". Defer (F6).
- **F4 (defer).** Event delete leaves the upload rows and S3 objects of its photos, teasers and materials (11b and 11c plans, Implementation Notes: "accepted for now"). Not in `deferred-work.md` and no issue found. Prevention: an "accepted for now" note becomes a deferred-work entry in the same session.
- **F5 (fix now).** #1286 PR review finding 1: the new scheduled-job failure alarm uses a 5-minute period, 1 evaluation period, `NOT_BREACHING` and an OK action, for a job that runs once a day, so the alarm and its issue clear minutes after a nightly failure. I checked: unchanged at `infrastructure/lib/constructs/ecs-service-alarms.ts:323-340`. #1286 merged about an hour after that review (comment 17:50, merge 18:55 UTC) with no recorded triage.
- **F6 (defer, owner decides).** #1308 PR review finding 1: `POST /uploads/{purpose}/import` fetches with `ImageUrlFetcher.DEFAULT_CLIENT`, `followRedirects(NORMAL)`, no resolved-IP check (I checked: `ImageUrlFetcher.java:30-37`), unlike the avatar import's SSRF guard. Organizer-only, image responses only. The code comment calls redirects "acceptable"; no issue or plan entry records that as a decision.
- **F7 (accept).** Old paths answer a mix of 404, 405, 403/400 (`/users/me/*` self paths) and 400 (`{email}` paths), each pinned by an IT and settled under the owner's blanket go (4.10 plan Decisions; 4.10d Implementation Notes; 11b, 11c notes). Recorded here so later retros stop re-flagging it.

### Lens: verification gaps

- **F8 (process).** TDD order not followed in 11a, 11c and 11d (self-reported: 11a Implementation Notes first bullet; 11c "Not strictly RED-first"; 11d "Not RED-first for the EMS handler"), against CLAUDE.md's TDD rule. 11c compensated with mutation checks of the cache eviction and the advisory lock. Accept, lesson below.
- **F9 (process, C5).** Batching: #1281 (4.9, owner additions, 16.8c), #1289 (4 tickets of this epic plus 3 of epic 3 and the epic 1 sweep), #1311 (11a to 11d). Owner agreement in the repo: #1311 ("Owner go 2026-11-13: 11a to 11d ship in one PR", 11a plan line 26, PR body); the 4.9 branch ("Owner 2026-11-09: it goes out together with 4.9", `handoff-2026-11-09.md:13`); #1289 under the owner's blanket go 2026-11-10 with the shared branch named in each plan's Boundaries (e.g. 4.10 plan line 40). I found no in-repo record that 16.8c joining #1281 was agreed; it belongs to epic 1. Cost of batching, sourced: the PR reviewer could not read #1281 or #1289 ("GitHub refuses the full diff (over 300 files)", review comments on both), and recommended splitting #1289 by story.
- **F10 (process).** PR-level review comments have no triage record in any plan: #1286 (F5), #1308 (F3 residual, F6), #1309 (stale `logos` comments, still at `TestFixtureCleanupService.java:213-219`, `TestFixtureCleanupController.java:68`). The plan-level Review Triage Logs cover only the in-session review.
- **F11 (bookkeeping).** Plans 4.10c and 4.10b are `done` with every Execution box unchecked (4.10c lines 79-86, 4.10b lines 81-88), and 4.10b's Implementation Notes are empty. Plans 11a to 11d carry `lenses_ran: []` and an empty Review Triage Log, while their Plan Change Logs and Implementation Notes record review fixes ("Review fixes (coordinator, 2026-11-13)", 11a; "2026-11-13, review", 11b to 11d). The record understates what ran.

### Aggregate views

- **Architecture delta.** New shared-kernel package `ch.batbern.shared.upload` (9 classes, 1006 lines; I counted) used by CUMS and EMS, each with its own table (`company_user_uploads` V30, `event_management_uploads` V153); gateway routes uploads by an explicit purpose map (`DomainRouter`), fail closed. EMS lost its mapping of the CUMS `logos` table, guarded by ArchUnit. No new cross-service call. EMS module script not re-run per epic; the initiative figure (23 pairs / 71 refs, orchestrator-measured 2026-11-15) is not attributable to this epic alone. Narrowed.
- **God-class / size.** `EventController.java` 2648 lines at 4.9's baseline -> 2334 at #1311's head (I measured with `git show | wc -l`); this epic moved participants, registrations list, publishing and photo operations out. `UserController.java` 872 -> 576 now. `UploadService.java` grew to 473 lines over 5 tickets (+490 / -17 across the ranges): a candidate to watch, not a god class (one responsibility, 3 public flows).
- **Duplication map (accept).** "Delete the upload row with its entity" is implemented three times with two lookups: `EventPhotoService.java:77` and `SessionMaterialsService.java:195` by upload id, `EventTeaserImageService.java:135` by purpose and S3 key (teaser ids are not upload ids, 11c notes). Different id models justify it.
- **Pattern divergence.** None beyond F7. Upload handlers follow the one SPI; ITs moved off class-level `@Transactional` where touched (4.10d, 11c, 11d notes).

## Behavior verification

Runtime behavior was not exercised by this retro (no Gradle, Vitest, Playwright or live calls, by the orchestrator's rule). Recorded by the build, not re-checked: full suites and local-dev Bruno/Playwright per plan Verification sections; production checks as listed in R2, R3, R7.

## Previous-retro follow-through

No previous retrospective file existed, so there was nothing to follow through on.

## Action items

Proposed, not applied. Owners are roles; the owner (Nissim) decides what runs.

1. **Fix F2**: update `company-create-logo.spec.ts:93` to the key without year; run it on local dev. Owner: next build session (dev). Remediation.
2. **Fix F5**: give the scheduled-job alarm a daily-shaped window (e.g. 1-day period or no OK action) in `ecs-service-alarms.ts`; infrastructure, so not on an event day (C7). Owner: dev, with owner go for the deploy. Remediation.
3. **Decide F6 and F3 residual**: SSRF guard for `/uploads/{purpose}/import` (refuse redirects or re-validate each hop) and whether confirm checks `createdBy`. Owner: owner decision; file as one issue with the #1308 review text as evidence.
4. **Decide F4**: clean up upload rows and S3 objects on event delete, or accept and record it. Owner: owner decision; capture as a deferred-work entry or issue.
5. **Spec reconciliation S1**: when #1320 is planned, move the 3 logo-request lines from X2 to an issue group `#1320` and fix the X2 comment. Owner: Sofia (`bmad-agent-po`) with the build of #1320.
6. **Process F10**: add to `_bmad/custom/bmad-build.toml` that the PR review comment gets a triage line in the plan (patch / defer with issue / reject with reason) before auto-merge. Owner: owner (customization change).
7. **Process F9**: cap a batch PR at what the PR reviewer can read (GitHub's 300-file limit was hit twice); batch within one epic. Owner: owner decision on C5 wording.
8. **Process F1**: name the beta canary check in the build override for frontend changes that add a resource kind the CSP governs. Owner: owner (customization change).
9. **Bookkeeping F11 and S2**: reconcile 4.10b and 4.10c checkboxes and Implementation Notes, 11a to 11d `lenses_ran` and triage logs, and record the post-deploy results for 4.9b and 4.10 to 4.10d if they exist (R7). Owner: Sofia, confirmed by the owner. Spec reconciliation.

Cosmetic, no item: the stale `logos` comments of F10 are fixed with the next touch of `TestFixtureCleanup*`.

## Decision records

Candidates tested against the ADR rule (hard to reverse, surprising, real trade-off, not yet recorded): one upload contract with per-service tables, purpose map routing, target binding, no year in final keys, teaser limit under `pg_advisory_xact_lock` (all recorded in `ADR-002-generic-file-upload-service.md`, lines 660, 794-798, 852, 902, 924); the 4.9b idiom (`ADR-013-rest-api-crud-conventions.md` line 47); copy-then-drop-one-release-later for a table move (`03-data-architecture.md` lines 600, 1371; `flyway-migration-guide.md` 493-494). Every candidate is already recorded, so no decision record is proposed. None written (headless).

## Acceptance verdict

**accepted-with-open-items** (criteria declared in the epic file's Done when). All 11 tickets are done; R1 to R6 are met in the evidence; R7 is partly met (batched PRs with owner agreement recorded for two of three batches of this epic, production verification recorded for 4.9, 4.11 and 11a to 11d only). No finding blocks: F1 is fixed, F2 affects a local-dev-only spec, F5 and F6 are open and tracked here as action items 2 and 3. Machine verdict, no human decision recorded.

## Open questions

- Was 16.8c's inclusion in #1281 agreed by the owner? No in-repo record found (epic 1's retro may hold it).
- Were the post-deploy checks of #1286 and #1289 (old paths 404/405, anonymous `GET /event-types` 200, invalid Turnstile token 403, access-control DAST) run and green? An answer would turn R7 into met.
- The epic file's open question "closes with a refactor sweep?" (epic Notes line 54) was never answered; the epic closed without a sweep entry. Accept or record.
- Turnstile fail-open on a missing token (#1288) and the outreach data fix (#1287) remain owner decisions, open.

## Assumptions

- The epic resolved from the folder given by the orchestrator; `pending_tickets` was empty.
- `plan-4-9b-contract-tightening.md` and `null-vs-optional-options.md` were treated as part of this epic, as instructed.
- Range ends for 4.10d and 4.11d are the PR heads (inferred); the 4.9 owner-additions range and the 4.10c range include commits of 16.8c and 3.8 that subjects could not separate.
- Diff-scope review ran inline on the narrowed scope above, not through `bmad-review` (orchestrator rule). Session logs unavailable.
- Every action item and the verdict were set without the user.
