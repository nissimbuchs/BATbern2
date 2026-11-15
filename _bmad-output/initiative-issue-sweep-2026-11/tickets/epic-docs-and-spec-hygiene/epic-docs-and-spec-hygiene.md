---
type: epic
title: "Docs and specs describe the running system"
parent: initiative-issue-sweep-2026-11
covers: [R21, R22, R28]
sources: []
companions: []
after: []
refined: false
risk: low
---

# Docs and specs describe the running system

## Description

The remaining doc-audit findings are fixed, the doc-drift check detects drift again, a restore runbook exists, and every spec operation has an implementation. The GitHub issues it covers are the detailed spec; the initiative's triage says which parts are still open.

## Outcome

The owner sees the covered issues (R21, R22, R28) closed with the fix live in production, or closed by the recorded decision.

## Done when

- Each requirement it covers (R21, R22, R28) is shown in production after the deploy, by the check its issue names.
- Its PR deployed green and its covered issues are closed with a comment naming the PR.
- No EventBridge, message bus or polling was added (initiative C1).

## Boundaries

- Only the requirements it covers; anything found on the way is fixed in its own commit or filed (CLAUDE.md Ownership).

## References

- parent — _bmad-output/initiative-issue-sweep-2026-11/initiative-issue-sweep-2026-11.md, section Requirements
- source — _bmad-output/initiative-issue-sweep-2026-11/issue-triage-2026-11-15.md

## Notes

- Waits on epic 1 because: the code fixes the docs describe (versions, dev tooling)
