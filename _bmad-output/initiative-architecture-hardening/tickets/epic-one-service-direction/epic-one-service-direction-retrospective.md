---
epic: epic-one-service-direction
date: 2026-11-15
verdict: accepted-with-open-items
criteria: declared
headless: true
---

# Retrospective: Services call in one direction over /internal (epic 3)

## Epic summary

Epic file: `epic-one-service-direction.md` (Requirements R1 to R8, Done when L44-50). Initiative: `initiative-architecture-hardening`.

| Ref | Title | State | Shipped in |
|---|---|---|---|
| 3.6 | EMS calls CUMS over /internal with one token helper | done | #1289 (merged 2026-11-11 08:05Z, 4254d93b) |
| 3.6b | PCS calls CUMS and EMS over /internal | done | #1289 |
| 3.8 | Topic canvas loads partner topics itself | done | #1289 |
| 3.7 | CUMS calls the Mail Center through a contract it owns | done | #1306 (merged 2026-11-12 00:23Z, 49dd9514) |
| 3.7b | CUMS owns the user-reference merge contract, EMS and PCS implement it | done | #1306 |
| 3.8b | EMS calls PCS calendar replies through a contract it owns | done | #1306 |
| 3.sweep | Refactor sweep | done | #1306 |

`tickets.py status` (I ran it): 7 of 7 done, `pending_tickets` empty, no ticket at `built`. Mapping to PRs from the PR titles and commit lists of #1289 and #1306 (I checked with `gh pr view`).

**Ranges** (feature-branch commits, squash-merged; I ran `git_evidence.py` per range, 0 merges in every range):

| Ref | Range | Commits | Files | +/- | Note |
|---|---|---|---|---|---|
| 3.6 | 23e6150f..ac006ac1 | 6 | 66 | +2426/-1977 | cut at ac006ac1: 4.10 commits follow on the same branch |
| 3.6b | e0bbba97..4c44601a | 4 | 49 | +929/-774 | recorded baseline to next |
| 3.8 | 4c44601a..ca0a948e | 5 | 33 | +1350/-722 | cut at ca0a948e: 4.10b follows |
| 3.7 | 9d10da27..c295ff42 | 8 | 60 | +3966/-2845 | starts before the recorded baseline 7815a9c2 so the discarded feed build (dd398ea6, 74b9b31c) is included |
| 3.7b | c295ff42..29c260f3 | 2 | 59 | +1925/-875 | |
| 3.8b | 29c260f3..92abe9cb | 3 | 34 | +744/-422 | |
| 3.sweep | 92abe9cb..4b5aec92 | 5 | 22 | +640/-91 | ends at the #1306 PR head |

229 distinct files, 48 touched by two or more tickets.

**Evidence inventory.** Read: epic file, `tickets.toml` entries (no story files: none pulled), all 7 plans, `deferred-work.md`, ADR-016, the ArchUnit stores, the S2S guard and its baseline, PR bodies and CI job lists of #1289 and #1306. Missing: session logs (process analysis from them skipped); no run folders; no companions. Previous retrospective: no previous retrospective file existed (none of the initiative's epics had one before this batch).

## Findings

### Aggregate views

**AV1. Architecture delta: both cycles removed (accept, positive).** I checked at HEAD 74dee295: CUMS store `e75ed5e2-...` and EMS store `98b035c9-...` are 0 bytes; `s2s-client-urls-baseline.txt` has no entry lines; no `"/api/v1` literal in any `services/*/src/main/java/**/client/**`; downstream base URLs appear in CUMS and EMS `application.yml` only as `batbern.contracts.*.implementers` (CUMS L110, L114; EMS L183). Every `RestTemplate` user in the three services calls `/internal` (EMS `BatbernAiService`/`TrendingTopicsService` call OpenAI, not a service). No `RequestFeed`, `feed_cursors`, `PartnerApiClient`, `PartnerMeetingRsvpClient` or `EventManagement*Client` left in `src/main`. Prevention: none needed.

**AV2. Duplication avoided, failure policy diverged (defer).** The three contract callers (`HttpMailCenterRequestContract`, `HttpUserReferenceContract`, `HttpCalendarReplyContract`) all go through shared-kernel `ContractCalls`; no duplicated header or error code. But the after-commit failure policy differs: Mail Center decisions and calendar replies log ERROR and count (`mailcenter.request_contract.failures`, `forwarding.calendar_reply_contract.failures`), while a holder that misses a primary-email change gets only a WARN with no counter (`PrimaryEmailService.java:73-77`, ADR-016 L76-77). The owner kept primary email a synchronous push precisely so registration mail does not go to the old address (epic L88); a missed holder now drifts silently. Pre-existing behaviour ("failure as today", 7b matrix L43), so not a regression. Prevention: one failure policy per call class (after-commit report = ERROR + counter) stated in ADR-016.

**AV3. Size growth: no god class (accept).** Highest net churn is the spec `cums-internal.openapi.yml` (+708) and tests; the largest new production class is `HttpUserReferenceContract.java` (+147). EMS module numbers at epic end are recorded by 8b (plan 8b L83); the orchestrator measured 23 violating pairs / 71 refs today (orchestrator-measured 2026-11-15), not attributable to this epic alone.

**AV4. Pattern divergence: contract callers live in different packages, and the URL guard follows the package (defer).** CUMS callers sit in `client/`, EMS `HttpCalendarReplyContract` in `forwarding/`, PCS `HttpOutboundMailRecorder` in `mail/`. The S2S URL guard scans only files under a `client` directory (`web-frontend/scripts/check-s2s-client-urls.ts:131-142`), and the service-direction ArchUnit rules match class names only (`ServiceDirectionRules.java:26` `(EventManagement|Partner)*Client*`; `EmsModuleRules.java:110`). I checked that both out-of-package callers call `/internal` today, so the Done when holds; the gates would not catch a new `/api/v1` call or a downstream caller with a neutral name outside `client/`. Provider controllers also kept pre-contract names (`InternalRsvpController`, `InternalUserMergeController`): accept. Prevention: gate on the invariant (no downstream base URL outside `batbern.contracts.*`, no `/api/v1` literal in any class that uses `RestTemplate` or `ContractCalls`), not on a directory or name.

**AV5. Spec-to-implementation reconciliation.**
- **S1 (fix now, spec reconciliation).** The epic file still describes the superseded pull design: Description L17 ("EMS pulls merge and logo requests from a CUMS feed", "PCS pulls calendar RSVPs from an EMS feed") and Done when L48 ("reach the Mail Center by pull"). The initiative says the same (R12 L42 "pulls", R13 L43 "within one poll interval"). R5, R8 and the owner decision (epic L91) replaced this with upstream-owned contracts on 2026-11-11. As built matches R5/R8, not the stale lines.
- **S2 (accept).** R4 names `partner_contacts.username`; the table no longer exists (PCS V6), so 7b implements three columns (7b plan L27, L92). Recorded so later runs stop flagging it.
- **S3 (accept).** R2 "every client uses the helper": the gateway's `GatewayUserStatusClient.java:72` sends `new HttpHeaders()`. R2 is scoped to EMS and PCS clients; the gateway call is token-free by design.
- **S4 (accept).** Ticket 7's `verify` and C3 require the EMS module scripts before and after. The shipped rework records none; the only numbers (plan 7 L97) belong to the discarded feed build. 8b re-ran them on the following baseline (plan 8b L83), so the gap is one plan's record, not the code.
- **S5 (accept).** Primary-email end to end (7 AC 3, 7b AC 2) was not run on local dev because make-primary moves a login in the Cognito pool that serves production (7b plan L78, L120); EMS's endpoint was called directly and `PrimaryEmailIntegrationTest` covers the CUMS path.

### Diff-scope lenses (inline, narrowed)

Narrowing: `bmad-review` was not invoked (orchestrator rule). I ran adversarial, edge-case and verification-gap lenses inline over (a) files touched by two or more tickets, read at HEAD where they carry logic (`ContractCalls`, `MailCenterRequestDecisions`, `RequestConversations`, `PrimaryEmailService`, ADR-016, the guard), and (b) everything the plans' review logs left deferred or rejected. Not read line by line: the 48 shared files' full diffs, test bodies, frontend topic-canvas code.

**L1. WITHDRAWN can overtake a slow open (defer, tracked #1319).** Adversarial. `MailCenterRequestDecisions.java:84-90` sends `WITHDRAWN` in `afterCompletion(STATUS_ROLLED_BACK)` right after the 10 s read timeout; `RequestConversations.close` (`RequestConversations.java:106-112`) is an `UPDATE` of an existing conversation, so a withdrawal that arrives before EMS commits the slow open updates 0 rows and the open then creates an OPEN conversation for a request that does not exist. I checked the code path; the race was not measured (sweep review #2, plan L111). `deferred-work.md` L110-112 moves it to issue #1319 (open; I checked with `gh issue view`). The sweep's closing note "Deferred entries of this epic 2 -> 0" (sweep plan L84) was written before its own review deferred this third entry. Prevention: a compensating call needs an idempotency marker on the receiving side from the start (EMS remembers a withdrawn request id).

**L2. Partial merge across holders (accept).** Edge case. A holder failing mid-merge leaves earlier holders merged and the source profile in place; every holder merge is idempotent, so a retry completes it (7b plan L90, ADR-016 L75-76). The CUMS test repeats a failed merge to completion (7b review #8). The sweep added PCS `partner_user_merge_log` (V12) so a PCS move can be undone by hand. Accepted design, no outbox by owner decision.

**L3. Review depth below the risk (process lesson).** Verification gap. All 7 plans pin `review: 'quick'`, `lenses_ran: ['quick']` (frontmatter L10-13 of each plan), including the three high-risk tickets 6, 7 and 7b. The one concurrency defect still open (L1) was rated "plausible, not measured" by a quick pass. Prevention: a ticket with `risk: high` gets the full lens set, set through a `_bmad/custom` override, not per plan.

**L4. After-deploy checks not written back into the plans (process lesson).** Verification gap. Plans 6 (L149-150, L187) and 6b (L86, L129) end with "after deploy: production 404/405 check and access-control DAST"; the commit that set them done (86a3f31a) changed only the status line. I checked CI: the job "Post-deploy checks (smoke, CORS, access control)" succeeded on the deploy runs of #1289 (run 42973654162) and #1306 (run 43115215031). The production probe of the old paths (400/404/405, `/internal` 403 at the gateway) is recorded only in session memory, not in the repository: recorded by the build, not re-checked.

**L5. Plan records drifted from the work (accept, lesson).** 6b's tasks are all unchecked although built (6b plan L69-79); Implementation Notes of 7, 7b, 8b and sweep still say "not committed" or "Not reviewed yet" while review logs and commits follow (7 L75, 7b L108, 8b L75, sweep L74); 7b's review table has no "Review Triage Log" heading (7b L73); plan 7's file name still says "ems-pulls-mail-center-requests-from-cums". Each is cosmetic, but a reader cannot trust the status lines. Prevention: the build skill refreshes those lines when it appends a review or flips status.

**L6. Cross-ticket boundaries: checked and clean on the narrowed scope.** ADR-016 was edited by all 7 tickets, `cums-internal.openapi.yml` by 5, each service's `S2sProviderContractIntegrationTest` by 4. I compared ADR-016's Enforcement section (L87-111) with the code: the header helper users, the `ContractCalls` contract callers and the guard state it lists match what I grepped. `ContractCalls.post` (stop at first failure) for open and `postToEvery` for decisions, withdrawals and primary email are used consistently across 7, 7b and sweep.

**Wins with evidence.** The no-polling rework deleted the never-deployed CUMS V30 and EMS V153 before any deploy, so no applied migration was touched (plan 7 L28, #1306 body). The contract harness of 6 found and fixed 15 error handlers with out-of-spec `severity` values plus a `companyId: null` the spec forbade (plan 6 L113-119, commit 94c13b45). The read-only production query before PCS joined found 0 orphaned PCS rows (7b plan L92).

## Behavior verification

Runtime behaviour was not exercised end to end by this retrospective. Recorded by the build, not re-checked: local-dev walks per story (plan 6 L137-144, 6b L94, 8 L125-129, 7 L85, 7b L120, 8b L85, sweep L83) and the production deploys of #1289 and #1306 with green post-deploy, Bruno and Playwright jobs (I checked the job conclusions in CI, not the behaviour behind them).

## Previous-retro follow-through

No previous retrospective file existed, so there was nothing to follow through on.

## Action items

Proposed, not applied. Remediation items go to the normal dev loop; the spec reconciliation goes through `bmad-agent-po` with the user's confirmation.

1. **Spec reconciliation (S1).** Rewrite epic Description L17 and Done when L48, and initiative R12 L42 / R13 L43, to the upstream-owned contract wording of R5/R8. Owner: Sofia (`bmad-agent-po`), confirmed by Nissim.
2. **Remediation (L1).** Fix the WITHDRAWN-before-open race with a withdrawal marker in EMS that a late open respects. Already tracked as #1319. Owner: dev loop, owner go.
3. **Remediation (AV2).** A holder that misses a primary-email change logs ERROR and increments a counter (`PrimaryEmailService.java:73-77`), like the other after-commit contract failures; ADR-016 cost line follows. Product judgment (is a silent miss acceptable?) is the owner's; propose as an issue.
4. **Remediation (AV4).** Widen the S2S gates to the invariant: the URL guard scans every main class that uses `RestTemplate` or `ContractCalls`, not only `client/` directories, and a check fails on a downstream base-URL property outside `batbern.contracts.*`. Owner: dev loop.
5. **Process (L3).** Pin the full review lens set for `risk: high` tickets via `_bmad/custom/bmad-build*.toml`. Owner: Nissim (`bmad-customize`).
6. **Process (L4).** When a plan flips to done after deploy, write the after-deploy results (probe codes, DAST run id) into its Verification section. Owner: build skill override / Sofia.
7. **Process (S4, L5).** A plan reworked after an owner decision re-runs its ticket's whole `verify` list and refreshes its task boxes and status lines before review. Owner: build skill override.

## Decision records

Candidates tested: no polling and upstream-owned contracts; compensating `WITHDRAWN` instead of an outbox; not forwarding a JWT that expires within 60 s; preview-all-then-merge-each with idempotent retry; source-scan guard instead of ArchUnit for URLs. Each is hard to reverse and the result of a trade-off, but each is already recorded in ADR-016 (decision 4 amendment L44-48, Consequences L72-83, Enforcement L87-111). No new decision record proposed; none written.

## Acceptance verdict

**accepted-with-open-items** (criteria declared).

- Done when 1: I checked. Guard baseline empty, no `/api/v1` literal in client packages; the 4 machine-only operations are absent from `users-api` and `event-analytics-api` and present in `cums-internal` (L659, L727, L810) and `ems-internal` (L90).
- Done when 2: the merge preview counts EMS and PCS rows and a merge rewrites both: recorded by the build (7b plan L120, `UserMergeIntegrationTest`), not re-checked.
- Done when 3: "by pull" is superseded by the owner decision of 2026-11-11 (S1); requests open and close at once, canvas and RSVPs behave as before: recorded by the build, not re-checked.
- Done when 4: I checked. CUMS and EMS service-direction stores empty, G01 and G02 absent from `api-lint-owners.txt` and the baseline; before and after recorded per story except 7's module scripts (S4).
- Done when 5: I checked. Both PRs merged; the access-control post-deploy job succeeded on both deploy runs.

Open items: #1319 (L1), primary-email WARN (AV2), gate scope (AV4), stale epic and initiative text (S1). None blocks the epic's outcome.

## Open questions

- Is a silently missed primary-email holder acceptable (AV2), given that the owner kept the push synchronous to avoid mail to the old address?
- Should the epic and initiative texts be reconciled (S1), or left as history with the decision line as the correction?

## Assumptions

- Epic resolved from the folder given by the orchestrator; `status` showed 7 of 7 done, `pending_tickets` empty.
- Machine verdict rendered on the evidence alone, no human decision.
- Ranges for 3.6 and 3.8 were cut where unrelated 4.10/4.10b commits begin on the same branch; 3.7's range starts at 9d10da27 to include the discarded first build. Attribution by plan range and commit subject.
- The production probe codes for #1289 come from session memory, not repository evidence; treated as recorded, not verified.
- All 7 action items and the "no decision record" outcome are proposals made without the user.
