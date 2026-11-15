---
type: epic
title: "Dependencies follow the BOM, logs carry no addresses, alarms mean something"
parent: initiative-issue-sweep-2026-11
covers: [R11, R12, R24]
sources: []
companions: []
after: []
refined: false
risk: medium
---

# Dependencies follow the BOM, logs carry no addresses, alarms mean something

## Description

No version is pinned below the Spring Boot BOM, the log encoders mask e-mail addresses for every service with every CloudWatch metric filter still matching, and the latency and availability alarms stop firing on deploys and single night-time errors; all three touch the runtime of every service, so they ship and are watched together. The stale EventBridge comment in SharedObjectMapperConfig goes too (owner kept the mapper as is, #1254). The GitHub issues it covers are the detailed spec; the initiative's triage says which parts are still open.

## Outcome

The owner sees the covered issues (R11, R12, R24) closed with the fix live in production, or closed by the recorded decision.

## Done when

- Each requirement it covers (R11, R12, R24) is shown in production after the deploy, by the check its issue names.
- Its PR deployed green and its covered issues are closed with a comment naming the PR.
- No EventBridge, message bus or polling was added (initiative C1).

## Boundaries

- Only the requirements it covers; anything found on the way is fixed in its own commit or filed (CLAUDE.md Ownership).

## References

- parent — _bmad-output/initiative-issue-sweep-2026-11/initiative-issue-sweep-2026-11.md, section Requirements
- source — _bmad-output/initiative-issue-sweep-2026-11/issue-triage-2026-11-15.md

## Notes

- Waits on epic 1 because: the final GATEWAY_API_REQUEST log line of R7, which the masking must keep matching
