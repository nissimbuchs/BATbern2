---
type: epic
title: "The bugs that need no decision are fixed"
parent: initiative-issue-sweep-2026-11
covers: [R29, R2, R3, R4, R5, R6, R7, R8, R9, R10, R27]
sources: []
companions: []
after: []
refined: true
risk: medium
---

# The bugs that need no decision are fixed

## Description

The claim link, trigger states, Live Control language, bell, cockpit card routing, quiet hours, mail fallback language, gsw-BE, dates, cron time zones, the #1223 UI defects, the gateway 4xx log, the EmailService ratchet category, the dev tooling bugs, the ZAP ignore, the leftover EventBridge IAM grant, the browser notification push and the unread notification preferences are fixed or removed and live; it opens with the fix epic 0 found, the missing mail-attachments put grant for EMS (R1), and hands its versions and dev-tooling fixes to epic 7 to document. The GitHub issues it covers are the detailed spec; the initiative's triage says which parts are still open.

## Outcome

The owner sees the covered issues (R29, R2, R3, R4, R5, R6, R7, R8, R9, R10, R27) closed with the fix live in production, or closed by the recorded decision.

## Requirements

- R1 (parent R29): EMS can store and delete inbound mail attachments under `mail-attachments/` in the inbound bucket, proven by a CDK test on the EMS task role policy, so an inbound mail with a kept attachment no longer lands in a DLQ.
- R2 (parent R2): a signed-in user of any role who opens the claim-profile link from the claim mail reaches the claim page, not /dashboard.
- R3 (parent R3): every trigger state the create-task and edit-template dialogs offer is accepted by the backend on save, and every state the backend accepts can be chosen.
- R4 (parent R4): Live Control shows its labels in the UI language (10 locales); the bell shows the unread count; the Speaker Lineup newsletter card opens Communications.
- R5 (parent R5): quiet hours suppress inside the window, overnight windows included; mails in a locale other than DE (gsw-BE counts as German, owner 2026-11-15) or EN render in EN; speaker reminder and deregistration mails use the recipient's language; gsw-BE can be chosen and saved; dates show in the UI locale; every scheduled job runs in Europe/Zurich time.
- R6 (parent R6): the eleven UI defects listed in the issue no longer occur, and a partner sees the company tier read-only, without "Change tier" (owner 2026-11-15).
- R7 (parent R7): the gateway request log carries the final status of a proxied request, so a downstream 4xx is logged as `status=4xx clientError=true` and counted by the client-error alarm.
- R8 (parent R8): the test ratchet counts unit tests that mock `EmailService` or verify on it, today's count is frozen, and a new one fails CI.
- R9 (parent R9): `make ci-test` runs; the native start script names live ports and the working config path; the CLAUDE.md log query runs on Linux; `docs/versions.json` reports the real Gradle and Node versions; the commented-out infra E2E step in `deploy-staging.yml` is either run or deleted; the shared-kernel README lists the types that exist.
- R10 (parent R10): ZAP no longer reports rule 110009 for our JSON error bodies, and no IAM grant for EventBridge remains in the EMS stack.
- R11 (parent R27): no browser notification WebSocket client or server push remains while the Watch app's live timing, presence and arrivals still run over STOMP; the preferences `inAppNotifications`, `pushNotifications` and `notificationFrequency` are gone from UI, API and schema.

## Constraints

- Parent C1 to C8 bind this epic; C1 (no event bus) and C2 (one PR, one deploy) above all.
- The R1 grant fix is the first commit of the PR (owner 2026-11-15).

## Done when

- Each requirement it covers (R29, R2, R3, R4, R5, R6, R7, R8, R9, R10, R27) is shown in production after the deploy, by the check its issue names.
- Its PR deployed green and its covered issues are closed with a comment naming the PR.
- No EventBridge, message bus or polling was added (initiative C1).

## Boundaries

- Only the requirements it covers, plus every bug or defect found on the way: fixed in this epic's PR, in its own commit, test first (C9). No GitHub issue and no deferred-work entry for it.

## References

- parent — _bmad-output/initiative-issue-sweep-2026-11/initiative-issue-sweep-2026-11.md, section Requirements
- source — _bmad-output/initiative-issue-sweep-2026-11/issue-triage-2026-11-15.md

## Notes

- Decision: 2026-11-15: epic 1 no longer waits on epic 0. The cause it needed is named (R1 here); epic 0 stays open only until the DLQ messages expire (~2026-11-20).
- Was waiting on epic 0 because: the named cause of the DLQ failures, in case it needs a code fix here
- Decision: 2026-11-15: a bug sweep has no cross-layer tracer; story 1 (infra grant) goes first by owner decision, story 4 (riskiest, gates 5-7) second; lanes: frontend auth 2; tasks 3; notifications 4 → 5, 6, 7; cockpit 7 → 8 → 9; gateway 10; ratchet 11; tooling 12; sweep 13 last.
- Decision: 2026-11-15, owner: gsw-BE mails render in German; breakdown of 13 stories approved.
