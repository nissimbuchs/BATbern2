---
type: epic
title: "Deleting a person reaches every service"
parent: initiative-issue-sweep-2026-11
covers: [R20]
sources: []
companions: []
after: []
refined: false
risk: high
---

# Deleting a person reaches every service

## Description

A GDPR delete in CUMS removes or anonymizes the person's rows in EMS and PCS through the user-references contract, per the owner-approved row list, and the gateway stubs no longer claim deletions that did not happen. The GitHub issues it covers are the detailed spec; the initiative's triage says which parts are still open.

## Outcome

The owner sees the covered issues (R20) closed with the fix live in production, or closed by the recorded decision.

## Done when

- Each requirement it covers (R20) is shown in production after the deploy, by the check its issue names.
- Its PR deployed green and its covered issues are closed with a comment naming the PR.
- No EventBridge, message bus or polling was added (initiative C1).

## Boundaries

- Only the requirements it covers, plus every bug or defect found on the way: fixed in this epic's PR, in its own commit, test first (C9). No GitHub issue and no deferred-work entry for it.

## References

- parent — _bmad-output/initiative-issue-sweep-2026-11/initiative-issue-sweep-2026-11.md, section Requirements
- source — _bmad-output/initiative-issue-sweep-2026-11/issue-triage-2026-11-15.md

## Notes

- Waits on epic 3 because: outreach history keyed by username (R16), which the delete row list relies on
- Waits on epic 4 because: the company-merge additions to cums-internal.openapi.yml, so both contract changes do not collide
- Postponed by the owner 2026-11-20; #1120 closed as not planned with the restart notes. A restart begins with a fresh inventory of the migrated schema (columns added since 2026-10-29). Its #1215 part shipped with epic-docs-and-spec-hygiene.
