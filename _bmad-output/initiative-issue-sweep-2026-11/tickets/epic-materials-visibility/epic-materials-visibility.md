---
type: epic
title: "Only public materials are published"
parent: initiative-issue-sweep-2026-11
covers: [R19]
sources: []
companions: []
after: []
refined: false
risk: medium
---

# Only public materials are published

## Description

Speakers and organizers mark each material public or not, and no anonymous response or download exposes a non-public one. The GitHub issues it covers are the detailed spec; the initiative's triage says which parts are still open.

## Outcome

The owner sees the covered issues (R19) closed with the fix live in production, or closed by the recorded decision.

## Done when

- Each requirement it covers (R19) is shown in production after the deploy, by the check its issue names.
- Its PR deployed green and its covered issues are closed with a comment naming the PR.
- No EventBridge, message bus or polling was added (initiative C1).

## Boundaries

- Only the requirements it covers, plus every bug or defect found on the way: fixed in this epic's PR, in its own commit, test first (C9). No GitHub issue and no deferred-work entry for it.

## References

- parent — _bmad-output/initiative-issue-sweep-2026-11/initiative-issue-sweep-2026-11.md, section Requirements
- source — _bmad-output/initiative-issue-sweep-2026-11/issue-triage-2026-11-15.md
