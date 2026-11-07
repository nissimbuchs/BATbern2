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
   - Replace CUMS→EMS merge call with a `UserMerged` domain event (SQS). **owner**: accept a few
     seconds of eventual consistency after a merge? Merge-request mail-center call likewise.
5. **One upload pipeline** (ADR-002): shared-kernel upload service, `POST /uploads` +
   `/uploads/{id}/confirm` with `purpose`. Migrate one flow per release. Current presign
   classes: ConversationAttachmentService, EventPhotoService, EventTeaserImageService,
   GenericLogoService, MaterialsUploadService, ProfilePictureService, SessionMaterialsService,
   SpeakerPortalMaterialsService.
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

8. **Follow-up reviews (TODO, one per area)**: not covered by this review.
   - Frontend architecture (role trees, route registry, service layer, bundle boundary, state).
   - DB schema (per-service schemas, cross-service string refs per ADR-003, indexes, 137 EMS migrations).
   - Spring API gateway (routing, auth, rate limiting, what it still does after ADR-008).
   - Watch app API (`event-watch-api`, pairing/auth in CUMS, `/events/current`).
