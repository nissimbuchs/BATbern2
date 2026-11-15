---
type: epic
title: "The mails stuck in the DLQs reach someone"
parent: initiative-issue-sweep-2026-11
covers: [R1]
sources: []
companions: []
after: []
refined: false
risk: high
---

# The mails stuck in the DLQs reach someone

## Description

The 5 DLQ messages (forwarding 4, replies 1) are read, each delivered by a redrive or handed to an organizer before SQS drops them around 2026-11-19, the cause of each failure is named, and both DLQ alarms are back to OK. A code fix for the cause, if any, rides in epic 1. The GitHub issues it covers are the detailed spec; the initiative's triage says which parts are still open.

## Outcome

The owner sees the covered issues (R1) closed with the fix live in production, or closed by the recorded decision.

## Done when

- Each requirement it covers (R1) is shown in production after the deploy, by the check its issue names.
- Its PR deployed green and its covered issues are closed with a comment naming the PR.
- No EventBridge, message bus or polling was added (initiative C1).

## Boundaries

- Only the requirements it covers; anything found on the way is fixed in its own commit or filed (CLAUDE.md Ownership).

## References

- parent — _bmad-output/initiative-issue-sweep-2026-11/initiative-issue-sweep-2026-11.md, section Requirements
- source — _bmad-output/initiative-issue-sweep-2026-11/issue-triage-2026-11-15.md
