---
id: 5
type: spike
title: "Pre-deploy gate (P5): local spike on rack"
parent: none
covers: []
sources:
  - docs/plans/pre-deploy-gate-plan.md
  - docs/plans/test-fidelity-proposal-2026-11.md
companions: []
after: []
refined: false
hitl: true
risk: high
---

# Pre-deploy gate (P5): local spike on rack

## Description

Owner decision needed (P5 of the test-fidelity proposal). The only layers that run real code end to
end, Bruno and Playwright, run after the deploy, against production. So mail-sending and destructive
flows are never tested end to end, and replayed tests have damaged real data (#1069). 17.0a-d moved
most checks onto harnesses, but that structural cause is untouched.

P5 was parked on 2026-11-07 "until 17.1-17.5 and 17.0a-17.0d are implemented". They are (Epic 17
closed 2026-11-15), so the parking condition is met. The plan is
`docs/plans/pre-deploy-gate-plan.md`; this spike is its step 1 and builds nothing in GitHub.

## Spike (plan step 1)

1. Build gateway, CUMS, EMS and PCS with the production Dockerfiles (amd64 on rack).
2. `docker-compose.gate.yml`: Postgres, MinIO, a fake SES, the four images in the production profile.
3. SES endpoint override in `AwsSesConfig`, test first.
4. Start without AWS credentials; the startup failures are the list of hidden AWS dependencies.
   Report to the owner after this step.
5. Run the Bruno collections against the local gateway, plus one mail-sending flow checked against
   the fake SES. Measure the time from start to result.

Heavy build: nothing else heavy runs on rack in parallel.

## Answers the spike must give

- the startup dependencies with the proposed override for each (Cognito admin calls, SQS
  listeners, schedulers);
- the measured time per run (the plan's 15-25 minutes is an estimate);
- the Bruno result;
- a recommendation whether to build the GitHub job (plan step 2) and make it a required check.

## Owner decides

- go for the spike now, later, or not at all (option b in the proposal: status quo);
- after the spike: whether the extra minutes per PR are worth it.
