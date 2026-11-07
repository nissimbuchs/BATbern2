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
6. **EMS internal walls**: ArchUnit rules for `mailcenter`, `forwarding`, `usermerge`, `watch`,
   `notification`; then extract registration from `EventController` into its own controller
   implementing the generated registrations interface. Follow
   `docs/plans/ems-modularization-extension-points.md`, incrementally.
7. **Conventions (owner decides)**
   - Amend ADR-003: UUIDs allowed for owned sub-resources (61 ops already do this).
   - One speaker path key (`{speakerId}` vs `{speakerPoolId}` under `/speaker-pool/` vs `{username}`).
   - Tasks: single `PATCH /tasks/{id}` replaces `PUT complete|reassign|status`.
   - Settings: one app-settings resource; merge `users/me/settings` + `/preferences`.
   - `me` as alias of `{username}` to collapse the picture/profile twins.
   - Rename `/events/batch_registrations` (snake_case); fold it and `enroll-stakeholders` into `participants`.

## Not covered by the review

Frontend architecture, DB schema, gateway, Watch app API.
