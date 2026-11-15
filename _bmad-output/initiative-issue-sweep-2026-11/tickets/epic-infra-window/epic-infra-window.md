---
type: epic
title: "The gateway sees the visitor and is reachable only through the HTTP API"
parent: initiative-issue-sweep-2026-11
covers: [R23, R25]
sources: []
companions: []
after: []
refined: false
risk: high
---

# The gateway sees the visitor and is reachable only through the HTTP API

## Description

In a maintenance window in the week of 2026-11-16, evening picked by the owner: the ALB is internal behind a VPC link with a trusted client-IP header, images build with the wrapper's Gradle, and the content bucket CORS is current. Production stays on on-demand Fargate. The GitHub issues it covers are the detailed spec; the initiative's triage says which parts are still open.

## Outcome

The owner sees the covered issues (R23, R25) closed with the fix live in production, or closed by the recorded decision.

## Done when

- Each requirement it covers (R23, R25) is shown in production after the deploy, by the check its issue names.
- Its PR deployed green and its covered issues are closed with a comment naming the PR.
- No EventBridge, message bus or polling was added (initiative C1).

## Boundaries

- Only the requirements it covers; anything found on the way is fixed in its own commit or filed (CLAUDE.md Ownership).

## References

- parent — _bmad-output/initiative-issue-sweep-2026-11/initiative-issue-sweep-2026-11.md, section Requirements
- source — _bmad-output/initiative-issue-sweep-2026-11/issue-triage-2026-11-15.md
