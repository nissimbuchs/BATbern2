---
id: 1
type: story
title: "Dead API surface removed"
parent: epic-dead-surface-and-gates
covers: [R1]
after: []
refined: true
hitl: false
risk: medium
---

# Dead API surface removed

## Description

17.1, the tracer bullet of the epic. It removes the two reference specs, the six operations nothing calls and the frontend publish-preview chain, all in one change through spec, backend, frontend, Bruno and production. Afterwards a developer or agent searching the API finds only endpoints something calls. 17.3's check then starts clean, 17.4's lint baseline is smaller, and 16.8c no longer meets a stray send endpoint.

## Requirements

Verify: the operation count drops by 6, each removed path answers 404 in production (405 where a surviving sibling route shares the path), and the Publishing tab's live preview still renders.

1. **Reference specs gone** (R1)
   **Given** the `speakers-api` and `attendees-api` reference specs and everything that names them (code generation, git hooks, the ZAP scan matrix, npm scripts, generated types and their README)
   **When** the build, the git hooks, the CI scans and the npm scripts run
   **Then** neither spec exists, and no build, hook, scan or script names them
   **And** the ZAP spec-coverage check, the API-type check and the type check pass. A leftover matrix entry fails the coverage check as STALE.

2. **Frontend typed against the surviving spec** (R1)
   **Given** the frontend code typed from `speakers-api` types (speaker status, outreach history)
   **When** those generated types are removed
   **Then** the same code compiles against the `event-speakers-api` types, whose shapes are identical
   **And** an outreach history entry with no notes renders without error, since `notes` is nullable there.

3. **Six orphan operations gone** (R1)
   **Given** the operations `POST /logos/fetch-from-url`, `POST /events/{c}/speakers/invite-batch`, `POST /admin/backfill-speaker-names`, `POST /events/{c}/slides-online/send`, `POST /events/{c}/sessions/{s}/materials/upload-from-url` and `GET /events/{c}/publish/preview`
   **When** the build runs and the code base is searched
   **Then** each is gone from its spec, from the generated interfaces and from the controllers, together with the schemas and service methods only it used
   **And** a search for each path and operation id across the services, the frontend, the E2E tests, Bruno and the Watch app finds nothing outside history documents
   **And** the build and the endpoint authorization guard tests pass. A leftover controller method fails compilation, because its generated interface no longer declares it
   **And** a call to each removed operation answers 404, or 405 where a surviving sibling route shares the path, so no operation runs.

4. **Slides-online mail still works through registrant notices** (R1)
   **Given** an organizer sends the slides-online notice as a registrant notice
   **When** a send for the event is already running, has already gone out, or has not happened yet
   **Then** it is refused as in progress, refused with 409 as already sent, or created as pending with the registrant count, exactly as the removed endpoint behaved
   **And** the existing tests for these three cases are moved onto the registrant-notice path, not deleted.

5. **Live preview still renders** (R1)
   **Given** an organizer opens an event's Publishing tab
   **When** the live preview loads
   **Then** it shows the generated public preview page, as it does today
   **And** no request goes to a publish-preview path.

6. **Bruno clean** (R1)
   **Given** the Bruno files that name `slides-online` only as a template key
   **When** the Bruno suite runs against local dev
   **Then** those files are unchanged, the output has no `Skipping invalid file`, and nothing fails because of a removed path.

7. **Counted** (R1)
   **Given** the operation count over `docs/api/*.openapi.yml` (paths × get, post, put, patch, delete; `_shared` excluded)
   **When** it is counted on the branch base and on the final branch
   **Then** both numbers are recorded in the plan, the difference is −6, and the plan notes that the two reference specs no longer count.

8. **Docs follow** (R1)
   **Given** architecture documents that name a removed operation or spec (data architecture, ADR-002 table, ADR-014)
   **When** a reader looks up a removed operation or spec there
   **Then** they say what 17.1 removed, and history documents (archived stories, QA, revision logs) stay as they are.

## Boundaries

- Must not change: `/logos/upload-from-url` and `/users/{u}/profile-picture/upload-from-url` stay live (batch imports call them; 17.11 replaces them).
- Must not change: registrant notices and the Mail Center composer, material import in session batch import, avatar import, and the profile-gated speaker-name backfill runner.
- Out: the stub services' modules, stacks and ECS services (17.2). Here only their commented generator blocks go.
- Out: any database migration.

## References

- parent — `_bmad-output/initiative-architecture-hardening/tickets/epic-dead-surface-and-gates/epic-dead-surface-and-gates.md`, Requirements R1
- notes — `_bmad-output/initiative-architecture-hardening/tickets/epic-dead-surface-and-gates/tickets/notes-dead-api-surface.md` (code inventory verified 2026-11-07, architecture note draft, verification commands)
- prd — `docs/prd/epic-17-architecture-hardening.md`, Story 17.1
- plan — `docs/plans/architecture-review-2026-11-actions.md`, item 1
- architecture — `docs/architecture/ADR-014-backend-service-consolidation.md`, decision 2026-11-07

## Notes

- Decision: 2026-11-07, owner. A removed operation may answer 405 instead of 404 where a surviving sibling route shares its path template (Spring knows the path, not the method). No custom handler to force 404.
- Decision: 2026-11-07, owner. 17.1 and 17.3 ship together in one PR (one production deploy). Build them on one branch, 17.1 first.
- Decision: 2026-11-07, owner. Converted from the v6 story file `17-1-dead-api-surface.md`, which was deleted. Its code-level findings are in the notes file.
- Risk: medium. Removing a live endpoint by mistake breaks a production screen, and the generated-type move touches the speaker drawer.
