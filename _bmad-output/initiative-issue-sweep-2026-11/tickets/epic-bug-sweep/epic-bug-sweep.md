---
type: epic
title: "The bugs that need no decision are fixed"
parent: initiative-issue-sweep-2026-11
covers: [R2, R3, R4, R5, R6, R7, R8, R9, R10, R27]
sources: []
companions: []
after: []
refined: false
risk: medium
---

# The bugs that need no decision are fixed

## Description

The claim link, trigger states, Live Control language, bell, cockpit card routing, quiet hours, mail fallback language, gsw-BE, dates, cron time zones, the #1223 UI defects, the gateway 4xx log, the EmailService ratchet category, the dev tooling bugs, the ZAP ignore, the leftover EventBridge IAM grant, the browser notification push and the unread notification preferences are fixed or removed and live; it also carries the code fix for the DLQ failure cause that epic 0 names, if any, and hands its versions and dev-tooling fixes to epic 7 to document. The GitHub issues it covers are the detailed spec; the initiative's triage says which parts are still open.

## Outcome

The owner sees the covered issues (R2, R3, R4, R5, R6, R7, R8, R9, R10, R27) closed with the fix live in production, or closed by the recorded decision.

## Done when

- Each requirement it covers (R2, R3, R4, R5, R6, R7, R8, R9, R10, R27) is shown in production after the deploy, by the check its issue names.
- Its PR deployed green and its covered issues are closed with a comment naming the PR.
- No EventBridge, message bus or polling was added (initiative C1).

## Boundaries

- Only the requirements it covers; anything found on the way is fixed in its own commit or filed (CLAUDE.md Ownership).

## References

- parent — _bmad-output/initiative-issue-sweep-2026-11/initiative-issue-sweep-2026-11.md, section Requirements
- source — _bmad-output/initiative-issue-sweep-2026-11/issue-triage-2026-11-15.md

## Notes

- Waits on epic 0 because: the named cause of the DLQ failures, in case it needs a code fix here
