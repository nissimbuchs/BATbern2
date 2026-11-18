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

- Only the requirements it covers, plus every bug or defect found on the way: fixed in this epic's PR, in its own commit, test first (C9). No GitHub issue and no deferred-work entry for it.

## References

- parent — _bmad-output/initiative-issue-sweep-2026-11/initiative-issue-sweep-2026-11.md, section Requirements
- source — _bmad-output/initiative-issue-sweep-2026-11/issue-triage-2026-11-15.md
- Evidence 2026-11-18 (epic 1 sweep, read-only Logs Insights): EMS `RateLimitFilter` keys registrations on `ClientIp.resolve(X-Forwarded-For, remoteAddr)`, and every refusal in 14 days logged IP `127.0.0.1` (the Service Connect sidecar), so the 10-per-minute registration limit is one global bucket for all visitors. All 3,371 refusals (2026-11-09 10:xx: 1,712; 2026-11-16 10:xx: 1,659) fall inside the weekly ZAP scan runs; no real visitor was refused in that window, but any 11 registrations within a minute (a newsletter blast) or a scan would refuse real visitors. The trusted client-IP header of R23 must reach EMS's filter too; test: two clients with different IPs each get their own 10.
