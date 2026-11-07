# Architecture review 2026-11: action list

Source: architecture review of 2026-11-07 against develop `e7f76baf`. Report:
https://claude.ai/artifact/L4gaZoqWMDUnV7vLGcsdbh (private). Every item still needs the owner's
go before work starts. Items marked **owner** are decisions, not implementation.

## Measured baseline (2026-11-07)

- 21 specs, 329 operations. EMS 64,113 LOC (74% of backend), CUMS 15,404, PCS 6,825.
- EMS: 48 files in `controller/`, 104 in `service/`, 45 in `repository/`, 137 migrations.
  `EventController.java` 2,697 lines, implements 4 generated interfaces + 14 hand-mapped
  registration endpoints + `POST /events/{c}/topics`.
- ECS prod: `BATbern-staging-SpeakerCoordination-*` desired 1 / running 1;
  `AttendeeExperience` desired 0.
- Cross-service URLs: 9 via `/internal`, 12 via public API with user JWT.
  Cycles: EMS↔CUMS (CUMS→EMS = user merge + merge requests), EMS↔PCS (PCS→EMS = events,
  attendance-summary).

## Owner decisions 2026-11-07

All open questions answered. Recorded here so stories need no further sign-off on scope:
- Stop and remove the SpeakerCoordination AND AttendeeExperience ECS services and stacks in production
  (AttendeeExperience confirmed by owner 2026-11-07).
- Remove the EventBridge bus and its publishers. Keep the in-process Spring events:
  `EventBridgeEventPublisher` also publishes via `ApplicationEventPublisher`, and 2 `@EventListener`s
  consume those event types (checked 2026-11-07).
- `POST /events/{c}/slides-online/send` goes now, clearing the way for 16.8c.
- API conventions are enforced with breaking changes (frontend changes in the same story).
- S2S token rule: forward when present, never require, `/internal` authorizes by operation.
- ADR-003 UUID rule as proposed. No PR for this branch; rebase after the open #1244 work lands.

## After the rebase: done 2026-11-07 (rebased onto develop `89b3618f`, #1248)

1. Re-measured: 342 operations (+13 since `e7f76baf`, all from #1244/#1248 and the company logo
   requests), 58 with a UUID in the path, EMS 64,376 LOC and 138 migrations, EMS module check
   52 allowed / 28 violating pairs / 100 refs. Map and plan updated; the published report keeps the
   `e7f76baf` figures plus an update note.
   - New near-duplicate since the review: `GET|PUT /newsletter/my-subscriptions` (per address) next to
     `GET|PATCH /newsletter/my-subscription`; the frontend calls both. Added to 17.10.
   - New request/approve/decline flow `/users/company-logo-requests/{id}/approve|decline` beside
     `/users/merge-requests/{id}/approve|decline`, and the logo requests sit under `/users` although
     they belong to companies. Input for 17.4's spec-diff report and the 17.10 conventions.
2. ADRs written: ADR-016 (new), ADR-003 amendment, update notes in ADR-002, ADR-008, ADR-014; ADR-016
   added to `docs/architecture/index.md`.
   - Correction: ADR-002's Scope Clarification (2026-03-03) makes per-entity upload services for
     existing entities the intended pattern. The review's "only logos follow ADR-002" was wrong.
     Owner then confirmed consolidation anyway: ADR-002 "Decision 2026-11-07" supersedes
     Scope Clarification 1 with one upload contract (`/api/v1/uploads/{purpose}`).
   - EMS `GenericLogoService` is not a copy: it is a 137-line association helper on the `logos` table,
     which EMS (`V5`) and CUMS (`V10`) both create in the one shared database.
3. Ready for John (see "Brief for John" below).

## Epic 17 "Architecture hardening": suggested order

Protective and cheap first, then decoupling, then restructuring. Each story ships alone
(each PR = one prod deploy, so batch small ones). Breaking API changes update the frontend in
the same story.

| # | Story | Contents | Depends on |
|---|---|---|---|
| 17.1 | Dead API surface | delete `speakers-api`, `attendees-api` specs; 6 orphan endpoints incl. `slides-online/send`; FE `getPublishPreview` + `fetchPreview` | – |
| 17.2 | Dormant infrastructure (closes #1216, bus part of #1220) | stop + remove SpeakerCoordination and AttendeeExperience services/stacks, build-matrix and deploy entries, gateway route `/api/v1/content/**`; remove EventBridge bus, `EventBridgeEventPublisher`, `EventBridgeConfig`, publisher wiring (corrected 2026-11-07: one shared-kernel implementation of `DomainEventPublisher`, wired in CUMS/EMS/PCS config; replace it by a Spring-only one), keeping Spring events. Infra change: not on an event day | – |
| 17.3 | Frontend path contract check | CI fails when `web-frontend/src/services/**` calls a path not in `docs/api/` | 17.1 |
| 17.4 | OpenAPI lint + spec-diff report | Spectral rules (kebab-case, UUID params only nested, no `/internal` in public specs, no verb endpoints); PR comment listing added ops next to most similar existing ops | 17.1 |
| 17.5 | ArchUnit freeze | `FreezingArchRule` for the 5 vertical EMS packages + "no new client toward an upstream service" in all services | – |
| 16.8c | Mail Center consolidation (stays in Epic 16) | fold newsletter, registrant-notice, venue send/preview/retry/history into `/events/{c}/mails` | 17.1 (slides-online gone) |
| 17.6 | S2S via `/internal` | move `/users/provision`, `issue-invitation-credentials`, `PATCH /users/{u}/profile`, `/events/attendance-summary` and the other public S2S calls (12 URLs) to `/internal`; token rule in shared client helper | 17.4 |
| 17.7 | User-reference contract | `/internal/user-references/preview|merge` owned by CUMS, EMS implements, PCS added as holder; Mail Center pulls merge requests from CUMS, drop `/internal/mail-center/merge-requests*` | 17.6 |
| 17.8 | EMS↔PCS direction | topic canvas loads partner topics in the frontend; RSVPs as `/internal` feed pulled by PCS (ShedLock) | 17.6 |
| 17.9 | API conventions I (breaking) | tasks: one `PATCH`; settings: one app-settings + one user resource; nest `ai/analyze-abstract` under event | 17.4 |
| 17.10 | API conventions II (breaking) | `me` alias for `{username}` (pictures, profile); one speaker path key; `batch_registrations` + `enroll-stakeholders` folded into `participants`; `/newsletter/my-subscription` folded into `/newsletter/my-subscriptions` | 17.9 |
| 17.11 | One upload contract (ADR-002 decision 2026-11-07) | shared-kernel upload component + `uploads` table per service; `/api/v1/uploads/{purpose}` (+ `/{id}/confirm`, `/import`), gateway routes by purpose; first purpose `company-logo` incl. removing the shared `logos` table use in EMS; frontend upload hook. Follow-on stories: one purpose each (profile-picture, event-photo, teaser-image, session-material incl. speaker portal, mail-attachment), deleting old endpoints | 17.4 |
| 17.12 | EMS modularization design (Winston) | short tech design: package moves, freeze baseline, release slicing | 17.5 |
| 17.13 | Registrations out of `EventController` | own controller on the generated registrations interface | 17.12 |
| 17.14 | Domain exception base | status/code on a base class; `GlobalExceptionHandler` imports no domain exception | 17.12 |
| 17.15 | Mail core / mail center split | delivery-status projection to center; core publishes, center listens | 17.12, 16.8c |
| 17.16 | Event-core extension points | workflow guards as interface implemented by sessions/speakers; `Event→Registration` one-directional | 17.12 |

Later, not stories yet: the five remaining upload purposes after 17.11, the remaining EMS module
moves (planning, publishing, ai), follow-up reviews (item 9).

## Actions, in order

1. **Dead surface** (low effort)
   - Delete `docs/api/speakers-api.openapi.yml`, `docs/api/attendees-api.openapi.yml`
     (ADR-014 says reference-only; check the generator blocks commented out in both
     `build.gradle`, and the doc-drift mappings).
   - Orphan endpoints, no frontend/Watch/Bruno caller (confirmed by grep 2026-11-07):
     `POST /logos/fetch-from-url`, `POST /events/{c}/speakers/invite-batch`,
     `POST /admin/backfill-speaker-names`, `POST /events/{c}/slides-online/send`
     (2 Bruno refs: check first), `POST /events/{c}/sessions/{s}/materials/upload-from-url`,
     `GET /events/{c}/publish/preview`.
   - Frontend: `publishingService.getPublishPreview` calls `GET /events/{c}/publish/{phase}/preview`,
     which exists nowhere; only caller is `usePublishing.fetchPreview`, used by no component.
     Delete both.
   - **owner**: stop/remove the SpeakerCoordination ECS service + stack (prod infra change).
2. **Frontend path contract guard**: CI check that every path in `web-frontend/src/services/**`
   matches a spec path (reuse the regex scan from this review). Would have caught item 1's
   preview drift.
3. **Story 16.8c** (already gated on owner): fold newsletter, registrant-notice, venue,
   slides-online send/preview/retry/history into `/events/{c}/mails`.
4. **Break the service cycles**
   - S2S rule: `/internal` only. Move `POST /users/provision`,
     `POST /users/{u}/issue-invitation-credentials`, `PATCH /users/{u}/profile`
     (EMS→CUMS) and `GET /events/attendance-summary` (PCS→EMS) to `/internal`, out of the public specs.
   - No event bus (owner 2026-11-07: event propagation stopped for cost). Pattern instead:
     downstream PULLS upstream `/internal` feeds (`?since=cursor`) on a ShedLock job. No new infra.
     Direction: CUMS upstream of all; EMS upstream of PCS.
   - CUMS→EMS merge stays synchronous (EMS can block a merge + supplies preview counts; an
     event cannot). Turn it into a CUMS-owned contract `/internal/user-references/preview|merge`
     implemented by every username holder, config-listed in CUMS. PCS holds usernames
     (`topic_suggestions.suggested_by`, notes `author_username`, meetings `created_by`,
     vestigial `partner_contacts.username`) and is NOT covered by the merge today. Believed
     harmless now because only no-login sources are merged and those columns are written by
     logged-in users; not verified row by row.
   - Merge-request conversations: flip to Mail Center pulling open/closed requests from CUMS
     (removes `/internal/mail-center/merge-requests` + `/done`).
   - EMS→PCS: (a) `TopicSessionDataService` partner topics → frontend loads `/partners/topics`
     itself (EMS keeps cluster classification; offer batch classify); (b) calendar RSVPs from
     `InboundEmailRouter` → EMS keeps them in an `/internal` RSVP feed, PCS pulls.
     PCS→EMS (event summary, attendance-summary) stays, via `/internal`.
   - EventBridge: bus `batbern-staging` deployed with 0 rules (checked 2026-11-07); CUMS, PCS
     (PartnerService) and EMS (EventWorkflowStateMachine, SpeakerWorkflowService) still publish
     into it. **owner**: remove publishers + bus (prod infra), or keep for later.
5. **One upload pipeline** (ADR-002): shared-kernel upload service, `POST /uploads` +
   `/uploads/{id}/confirm` with `purpose`. Migrate one flow per release. Current presign
   classes: ConversationAttachmentService, EventPhotoService, EventTeaserImageService,
   GenericLogoService, MaterialsUploadService, ProfilePictureService, SessionMaterialsService,
   SpeakerPortalMaterialsService. Also: `GenericLogoService` exists twice, in CUMS and in EMS (found 2026-11-07).
6. **EMS internal walls** along the module map (report section 6). Layers: L0 platform, L1 mail core +
   notify core, L2 event core, L3 sessions/registrations/topics/tasks/media, L4 speakers,
   L5 agenda planning + publishing, L6 mail center/watch/ai/analytics/usermerge/org notify.
   Measured 2026-11-07: 50 allowed module pairs, 28 violating pairs, 101 refs; 10 classes cause 71.
   Re-measure: `scripts/architecture/ems-module-deps.py` then `ems-module-target.py`.
   - ArchUnit `layeredArchitecture()` + `FreezingArchRule` (freeze the 101, may only shrink).
     Needs classes moved into module packages first; cheaper first step: the 5 vertical packages.
   - Hotspots in order: `EventController` (27, move registration endpoints to own controller
     implementing the generated registrations interface), `GlobalExceptionHandler` (16, domain
     exception base class with status/code), `MailDeliveryStatusService`/`MailPipeline`/
     `ModeratorReplyAddress` (11, mail core publishes, mail center listens),
     `EventTimeResolver`/`EventWorkflowStateMachine` (5, workflow guard interface in event core),
     `TopicSessionDataService`→topics, `BatbernAiService`→ai (9), `Event→Registration` JPA
     association one-directional.
   Follow `docs/plans/ems-modularization-extension-points.md`, incrementally.
7. **Conventions (owner decides)**
   - Amend ADR-003 (owner agreed in principle 2026-11-07): meaningful ID for anything cross-service,
     stored elsewhere, or bookmarked; UUID allowed when no natural name AND never crosses a service
     AND nested under its meaningful parent with parent-scoped lookup. Named exception classes:
     owned child (24 ops), transient handle (6), natural key is PII, e.g. subscriber e-mail (4),
     unnamed top-level aggregate (19). One violation: nest
     `POST /speakers/{speakerId}/ai/analyze-abstract` under the event. Add a convention
     `findByIdAnd{Parent}` + one scoping test per nested resource (5 of 24 sampled, all scoped).
   - One speaker path key (`{speakerId}` vs `{speakerPoolId}` under `/speaker-pool/` vs `{username}`).
   - Tasks: single `PATCH /tasks/{id}` replaces `PUT complete|reassign|status`.
   - Settings: one app-settings resource; merge `users/me/settings` + `/preferences`.
   - `me` as alias of `{username}` to collapse the picture/profile twins.
   - Rename `/events/batch_registrations` (snake_case); fold it and `enroll-stakeholders` into `participants`.

8. **Agent guardrails** (report section 8). Context steers, gates decide.
   - DONE on this branch 2026-11-07 (owner go): map (≤4 KB) loaded by Amelia instead of source-tree.md,
     and by create-story, dev-story, quick-dev, code-review via `_bmad/custom/*.toml` overrides, each with
     an architecture-note / architecture-check step. Takes effect only once merged to develop.
     BMAD v7 (2026-11-07, branch `chore/bmad-v7`): create-story, dev-story and quick-dev are gone; their
     overrides moved to `_bmad/custom/bmad-build.toml`. v7 no longer loads project-context.md by itself,
     so the dev, build, code-review, architect and po overrides name it.
     Was: `docs/architecture/module-map.md` ≤ 4 KB: EMS modules + allowed directions, service
     upstream/downstream, reuse catalog (need X → use Y), API rules incl. ADR-003 UUID rule.
     Load via `project-context.md` (all BMAD workflows load it) + pointer in `CLAUDE.md` (non-BMAD
     sessions). Amelia already loads ~80 KB per activation (project-context 33 KB, coding-standards
     25 KB, source-tree 12 KB, tech-stack 10 KB): replace, do not add. Check `source-tree.md` is
     still accurate; candidate to drop from `_bmad/custom/bmad-agent-dev.toml`.
     Checked 2026-11-07: source-tree.md is current (#1224) and loaded ONLY by Amelia's override, not by
     project-context or any workflow. Plan: drop it from her facts, keep the doc; map got a 3-line
     Placement section instead.
   - CONTRADICTION to fix in the same change (checked 2026-11-07): source-tree.md + CLAUDE.md say S2S
     goes to `/internal` with no end-user JWT; project-context.md says clients "must propagate the
     JWT"; the code forwards it (EMS `UserApiClientImpl`, PCS `CompanyServiceClientImpl`,
     `EventManagementClientImpl`). DECIDED 2026-11-07 (owner + architect): forward the user JWT when present, never require it,
     `/internal` authorizes by operation. Aligned in CLAUDE.md, project-context.md, source-tree.md, map.
     Code still calls 12 public URLs: moving those to `/internal` is item 4.
   - Story "Architecture note" (5 lines: modules, new deps, new endpoints + nearest existing one,
     reused components): drafted by create-story, confirmed by dev-story before the first test.
     Via `_bmad/custom/bmad-create-story.toml` / `bmad-dev-story.toml` overrides (not the skill files).
   - Code review: add an architecture layer to `bmad-code-review` (diff + map only, fresh context).
   - Gates: ArchUnit (item 6) + no new client package toward an upstream service; Spectral OpenAPI
     lint (no snake_case, UUID params only nested, no `/internal` in public specs); PR spec-diff
     report listing added ops next to most similar existing ops; FE path contract check (item 2);
     monthly re-run of `scripts/architecture/*` for a trend.
   - Tooling (owner question 2026-11-07): no code-index tool or tree generator in the repo today
     (checked: `.mcp.json` has only shadcn; no script). Options: (a) generate the tree blocks in
     source-tree.md + a CI drift check, same pattern as `docs/versions.json`; keep it OUT of agent
     context, agents use `ls`/Glob live. (b) Timeboxed trial of an LSP-backed symbol index MCP
     (e.g. Serena, Java + TS) on one real EMS task, measure tokens before/after; watch rack memory
     (jdtls + 14 GB user.slice cap, see rack OOM memory). (c) Generate the map's numbers from
     `scripts/architecture/*` instead of hand-writing them.
9. **Follow-up reviews (TODO, one per area)**: not covered by this review.
   - Database review gets a head start (measured 2026-11-07): all services use the one database
     `batbern` (separate Flyway history tables). EMS and CUMS both create `logos` and `shedlock`. EMS reads
     CUMS tables in SQL: `user_profiles` and `companies` in `AnalyticsRepository` (marked "INTENTIONAL
     ARCHITECTURE BREAK"), `OrganizerThanksRepository`, `FeaturedThanksProjection`,
     `SessionUserRepository`, `SecurityContextHelper`; plus Pattern 3b in shared-kernel. Decide which stay.
   - Frontend architecture (role trees, route registry, service layer, bundle boundary, state).
   - DB schema (per-service schemas, cross-service string refs per ADR-003, indexes, 137 EMS migrations).
   - Spring API gateway (routing, auth, rate limiting, what it still does after ADR-008).
   - Watch app API (`event-watch-api`, pairing/auth in CUMS, `/events/current`).

## Brief for John (Epic 17)

**Done 2026-11-07:** [`docs/prd/epic-17-architecture-hardening.md`](../prd/epic-17-architecture-hardening.md), stories in `sprint-status.yaml` as backlog. Found while writing it: 17.7 must also cover `/internal/mail-center/logo-requests*` (#1246 added a second CUMS→EMS push of the same kind).

Create Epic 17 "Architecture hardening" and its stories from the table above, in that order. 16.8c
stays in Epic 16 and sits after 17.1. Read first: this file, `docs/architecture/module-map.md`,
ADR-016, the ADR-003 amendment, the ADR-002 and ADR-014 updates. Scope is decided (see "Owner
decisions"): breaking API changes are allowed when the frontend changes in the same story; 17.2 is a
production infrastructure change and must not land on an event day. Each story needs its 5-line
architecture note, and acceptance criteria that name the guard proving it (ArchUnit rule, lint rule,
contract check or test). 17.12 is a design task for Winston, not a dev story. Re-run
`scripts/architecture/*` when a story claims to remove violations.

