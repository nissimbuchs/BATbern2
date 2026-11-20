---
type: initiative
title: "Every open issue of Sep to Nov 2026 is fixed, decided or dated"
sources:
  - _bmad-output/initiative-issue-sweep-2026-11/issue-triage-2026-11-15.md
companions:
  - docs/architecture/module-map.md
  - docs/architecture/ADR-016-service-directions-and-service-to-service-calls.md
after: []
risk: medium
---

# Every open issue of Sep to Nov 2026 is fixed, decided or dated

## Description

After Epic 17 closed, 32 GitHub issues from the last two months are still open: bugs found during the doc audit and BATbern60, monitoring blind spots, decided features, and infrastructure scheduled for after the event. This initiative works them off in a handful of PRs, each one epic and one production deploy, and asks the owner every decision that is still open before the ticket that needs it is written. The triage of 2026-11-15 (source) says which parts are already done or superseded; the GitHub issue stays the detailed spec of each item.

## Outcome

The owner sees every issue from 2026-09-15 on closed with its fix live, closed as decided, or carrying a date, and the two DLQ alarms back to OK, without one EventBridge or event-bus line added to the platform.

## Requirements

- R1 (#1229, #1232): every message in the forwarding and replies DLQs is inspected, its cause named, and both DLQ alarms are back to OK once the messages expire (about 2026-11-20); the 5 raw mails were already deleted, so none can be delivered (checked 2026-11-15).
- R29 (#1229, #1232, epic 0 finding): EMS can store and delete inbound mail attachments under `mail-attachments/` in the inbound bucket, proven by a CDK test on the EMS task role policy, so an inbound mail with a kept attachment no longer lands in a DLQ.
- R2 (#1217): a signed-in user of any role who opens the claim-profile link from the claim mail reaches the claim page, not /dashboard.
- R3 (#1218): every trigger state the create-task and edit-template dialogs offer is accepted by the backend on save, and every state the backend accepts can be chosen.
- R4 (#1219): Live Control shows its labels in the UI language (10 locales); the bell shows the unread count; the Speaker Lineup newsletter card opens Communications.
- R5 (#1222 items 1, 2, 5, 6, 7; #1119 A3): quiet hours suppress inside the window, overnight windows included; mails in a locale other than DE (gsw-BE counts as German, owner 2026-11-15) or EN render in EN; speaker reminder and deregistration mails use the recipient's language; gsw-BE can be chosen and saved; dates show in the UI locale; every scheduled job runs in Europe/Zurich time.
- R6 (#1223 items 1-12): the eleven UI defects listed in the issue no longer occur, and a partner sees the company tier read-only, without "Change tier" (owner 2026-11-15).
- R7 (#1313): the gateway request log carries the final status of a proxied request, so a downstream 4xx is logged as `status=4xx clientError=true` and counted by the client-error alarm.
- R8 (#1318): the test ratchet counts unit tests that mock `EmailService` or verify on it, today's count is frozen, and a new one fails CI.
- R9 (#1213 code, #1211, #1119 leftovers): `make ci-test` runs; the native start script names live ports and the working config path; the CLAUDE.md log query runs on Linux; `docs/versions.json` reports the real Gradle and Node versions; the commented-out infra E2E step in `deploy-staging.yml` is either run or deleted; the shared-kernel README lists the types that exist.
- R10 (#1178, #1276, triage): ZAP no longer reports rule 110009 for our JSON error bodies, and no IAM grant for EventBridge remains in the EMS stack.
- R11 (#1231): no dependency is pinned below the Spring Boot BOM, and the Trivy jar scan shows none of the 28 HIGH findings those pins caused.
- R12 (#1202): no e-mail address reaches CloudWatch from any service, message or stack trace, proven by a test per service through its real logback config, and every CloudWatch metric filter still matches after deploy.
- R13 (#1254): retired 2026-11-15, the owner keeps the indented JSON (option 3).
- R14 (#1288): a newsletter subscription becomes active only after its owner confirms it from a DE/EN mail link; unconfirmed subscriptions expire.
- R15 (#1263): the upcoming-event cards show the self-nomination call to action to every visitor while the topic phase is open, per AC0-AC6 of the issue.
- R16 (#1287): speaker outreach history rows hold the organizer's username; rows whose address matches no user are left unchanged and counted in the PR.
- R17 (#1251): organizers find candidate duplicate companies and merge them, every reference moving to the target and the source staying findable as an alias; a rename moves references the same way; per the owner decisions of 2026-11-07.
- R18 (#1320): retired 2026-11-15, the owner keeps the current logo-request path.
- R19 (#1260): only materials marked public appear in anonymous responses or downloads, and no anonymous response carries the URL or id of a non-public one; the files stay under the CDN prefix (owner 2026-11-15); speaker and organizer set the flag; per AC1-AC5 of the issue.
- R20 (#1120, #1215): deleting a user removes or anonymizes every row that references the person in CUMS, EMS and PCS per the row list of the issue comment of 2026-10-29 (approved 2026-11-15), Q&A posts keeping their text under an anonymized author; the Cognito login is deleted; the delete is refused with its reason for the last organizers and for anyone who spoke at a past event; each delete leaves an audit entry; the gateway GDPR stubs are gone.
- R21 (#1210, #1212, #1213 docs, #1214, #1220 item 4): the docs named in the issues describe the running system; the doc-drift check flags a feat or fix that misses its mapped docs; a database restore runbook exists.
- R22 (#1212 items 1, 4, watch APIs): no spec operation lacks an implementation; the admin settings keys are listed in the spec; the CUMS watch controllers implement their generated interfaces.
- R23 (#1152): rate limiting, Turnstile and audit logs see the visitor's IP, and the gateway ALB is not reachable from the internet except through the HTTP API.
- R24 (#1144): the latency alarm stays quiet during a deploy, and the availability alarm needs a minimum request volume, while `alb-5xx` still reports single errors.
- R25 (#1220 items 1, 2, 5): production images build with the wrapper's Gradle; the content bucket CORS allows `beta.batbern.ch` and the local dev port; production stays on on-demand Fargate (owner 2026-11-15).
- R27 (#1222 items 3, 4): no browser notification WebSocket client or server push remains while the Watch app's live timing, presence and arrivals still run over STOMP; the preferences `inAppNotifications`, `pushNotifications` and `notificationFrequency` are gone from UI, API and schema.
- R28 (#1119 A2): CLAUDE.md, coding-standards and the gradle comments state the enforced coverage gate (0.60 for api-gateway and shared-kernel) instead of 85-90%.
- R26 (#1124): enum-like fields are enums in the specs, one spec per change, values checked against what the backend emits.

## Constraints

- C1 (ADR-016 decision 4, Story 17.2, owner 2026-11-15): no EventBridge, message bus or polling is introduced; a downstream service reacts to an upstream change only through an `/internal` contract the upstream owns (`module-map.md`).
- C2 (CLAUDE.md Deployment): a PR to `develop` deploys to production; one epic is one PR is one deploy; branch from `origin/develop`, push, and ask the owner before opening the PR; batch commits, one push per open PR.
- C3 (owner 2026-11-15): every open owner decision is asked before the ticket that depends on it is written, and recorded as a dated Decision line.
- C4 (CLAUDE.md Ownership): production data changes (DLQ redrive, #1287 rewrite) run only with the owner's go at the time, after a read-only count.
- C5 (CLAUDE.md Ownership, #1152): infrastructure that needs a maintenance window ships in its own PR, on an evening the owner picks, with a rollback plan, never on an event day.
- C6 (coding-standards Test fidelity rules, CLAUDE.md TDD): test first; mocks at the process boundary only; touched tests move off own-code mocks.
- C7 (CLAUDE.md localization): new UI keys in all 10 locales; mail templates DE and EN only.
- C8 (CLAUDE.md Quality Standards): one heavy build at a time on rack; build output through `tee` with `pipefail`.
- C9 (owner 2026-11-15, supersedes the CLAUDE.md Ownership "record as an issue" exceptions for this initiative): a bug or defect found on the way is fixed right away in the current epic's PR, in its own commit, test first. No new GitHub issue and no `deferred-work.md` entry; review findings are patched, not deferred. When the fix needs an owner decision, a production data change or a maintenance window (C3 to C5), ask the owner in the session and fix it after the answer. Goal: the open GitHub issue count goes down, never up.

## Done when

- Every issue in the triage's "Still open" table, except the parked #1124 and the out-of-scope #1119 A4 and #1070, is closed with its fix live in production, or closed by an owner decision, each closing comment naming the PR or the decision.
- `batbern-staging-forwarding-dlq` and `batbern-staging-replies-dlq` are in OK and both DLQs are empty.
- `git grep -i eventbridge` over `services/`, `shared-kernel/`, `api-gateway/` and `infrastructure/lib` finds no new use compared with develop f8f09ef05 (and the leftover IAM grant is gone).
- Each epic's PR deployed green, with its post-deploy checks run.

## Boundaries

- Out: #1070 (removal of `/events/current`, dated early 2027 by the owner).
- Out until decided: #1119 A4 (frontend error reporting), #1124 (enum series, one spec per change).
- Out: new features beyond what the issues specify; anything found on the way becomes its own issue or a fix in the epic that touches the code.
- Touch point: CUMS `user-references` contract (`docs/api/internal/cums-internal.openapi.yml`): extended for the user delete; owner: epic-gdpr-delete.
- Touch point: CloudWatch metric filters and alarms (`infrastructure/lib/constructs/alb-alarms.ts`): read by the masking change; owner: epic-platform-hardening.
- Tracer path: PR 0 restores the mail path the DLQs hold, PR 1 fixes the bugs without decisions, then the decided features, the company merge, the docs, and the infrastructure in its window.

## References

- source — `_bmad-output/initiative-issue-sweep-2026-11/issue-triage-2026-11-15.md`
- architecture — `docs/architecture/module-map.md`, "Services: who may call whom"
- architecture — `docs/architecture/ADR-016-service-directions-and-service-to-service-calls.md`, decision 4

## Notes

- Decision: 2026-11-15: epic 0 (DLQ recovery) comes first, not a platform baseline: the DLQs drop their messages around 2026-11-19 (14-day retention).
- Decision: 2026-11-15, owner: #1254 keep the indented JSON (option 3), R13 retired; #1223 item 12 partners see the tier read-only (into R6); #1222 item 10 a failed access-control probe alerts only, no rollback.
- Decision: 2026-11-15, owner: remove the browser notification push, keep the Watch STOMP endpoint (#1222.4); remove the unread notification preferences (#1222.3); Turnstile stays fail-open (#1222.9); coverage gates stay 0.60 and the docs say so (#1119 A2).
- Decision: 2026-11-15, owner: retire the 04-api narratives for an index per spec, delete `patchSessionQnaWindow` (#1212); retire the doc-drift resolver (#1214.8); strike FR7, 14, 15, 18, 20 and rewrite §4.2 to the real bucket (#1210). All in epic 7.
- Decision: 2026-11-15, owner: #1260 files stay on the CDN, URLs of non-public ones never handed out; #1320 dropped (R18 retired); production stays on-demand Fargate; the #1152 window is an evening in the week of 2026-11-16, picked once the PR is ready.
- Decision: 2026-11-15: R24 moves to epic 2, which owns `alb-alarms.ts`, so epic 8 waits on nothing and fits this week's window.
- Decision: 2026-11-15, owner: the mail-attachments grant fix is the first commit of epic 1, no separate hotfix; the DLQ messages are left to expire, not purged.
- Assumption: an epic that adds a Flyway migration takes the next free version when it rebases on develop; epics 1, 3, 4, 5 may each add one.
- Decision: 2026-11-15: #1070 out of scope, removal dated early 2027 by the owner (2026-11-03).
- Decision: 2026-11-15: #1119 A4 and #1124 stay outside the epics until the owner decides; #1124 is a per-spec series.
- Parked: R26 (#1124), no epic until the owner schedules the per-spec series; known to the owner (triage 2026-11-15).
- Decision: 2026-11-15, owner: #1120 row list approved as written; Q&A text kept, author anonymized; past speakers cannot be deleted (409, handled by hand); Cognito login deleted; delivery over the CUMS user-references contract. #1215 stubs deleted.
- Decision: 2026-11-20, owner: epic-gdpr-delete (R20, #1120) postponed, #1120 closed as not planned with the decision and the 2026-11-20 answers recorded on it (Mail Center left as is; upcoming speakers refused like past ones; audit entry with username and counts; the person's merge-log rows deleted). The #1215 part ships in epic-docs-and-spec-hygiene: gateway stubs removed, the user delete dialog and docs say it removes the profile only, and the 500 on a granted role or Watch pairing is fixed (CUMS V39).
- Decision: 2026-11-20: epic-dlq-recovery done. Both DLQ alarms OK (replies 13:47Z, forwarding 17:24Z), messages expired unredriven; #1229 and #1232 closed with the evidence.
