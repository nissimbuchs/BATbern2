---
type: epic
title: "Duplicate companies are found and merged"
parent: initiative-issue-sweep-2026-11
covers: [R17]
sources: []
companions: []
after: []
refined: false
risk: high
---

# Duplicate companies are found and merged

## Description

Organizers merge duplicate companies with every reference moved and an alias kept, and a rename moves references the same way. The GitHub issues it covers are the detailed spec; the initiative's triage says which parts are still open.

## Outcome

The owner sees the covered issues (R17) closed with the fix live in production, or closed by the recorded decision.

## Done when

- Each requirement it covers (R17) is shown in production after the deploy, by the check its issue names.
- Its PR deployed green and its covered issues are closed with a comment naming the PR.
- No EventBridge, message bus or polling was added (initiative C1).

## Boundaries

- Only the requirements it covers; anything found on the way is fixed in its own commit or filed (CLAUDE.md Ownership).

## References

- parent — _bmad-output/initiative-issue-sweep-2026-11/initiative-issue-sweep-2026-11.md, section Requirements
- source — _bmad-output/initiative-issue-sweep-2026-11/issue-triage-2026-11-15.md
