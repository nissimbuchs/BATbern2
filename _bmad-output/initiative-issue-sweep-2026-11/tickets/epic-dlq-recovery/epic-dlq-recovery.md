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
- Finding 2026-11-15 (checked): the 5 DLQ messages are S3 event records (forwarding 05:21, 10:27, 11:23 on 11-05 and 15:46 on 11-06; replies 06:09 on 11-05). All 5 raw mails are already deleted from `batbern-inbound-emails-staging` (HeadObject 404), so a redrive cannot deliver them and the content cannot be recovered.
- Finding 2026-11-15 (checked): each failed 3 times with `AccessDeniedException ... s3:PutObject on batbern-inbound-emails-staging/mail-attachments/...` (EMS log; 15 failures, 11-05 and 11-06 only). The live EMS task role has only Get/List on that bucket. Cause in code: `infrastructure/bin/batbern-infrastructure.ts:313` grants read only; the `mail-attachments/*` put and delete grants at `inbound-email-stack.ts:449-450` sit in an `if (props.emsTaskRole)` block that nobody feeds, since #1189 (2026-11-02). Inferred, not measured: every inbound mail whose attachment the Mail Center keeps fails the same way until fixed.
- Decision: 2026-11-15, owner: no redrive (nothing to deliver), no purge; the messages expire around 2026-11-20; the grant fix is the first commit of epic 1. Epic 0 is done when both alarms are OK after the expiry.
