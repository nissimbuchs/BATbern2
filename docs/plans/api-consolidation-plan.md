# API Consolidation Plan

**Status:** Proposed (branch `api-consolidation`)
**Date:** 2026-06-25
**Governing standard:** [ADR-013 — REST API CRUD Conventions](../architecture/ADR-013-rest-api-crud-conventions.md)
**Scope:** CUMS (companies, users), EMS (events, topics), Partner (partners + 4 sub-specs),
plus the shared-kernel contract that underpins all 13 specs.

This plan is the actionable counterpart to ADR-013. Each item is tagged
`[REMOVE] / [MERGE] / [RENAME] / [KEEP-BUT-FIX]` and ordered by risk so the low-risk,
high-clarity wins land first. **Staging IS production** — every contract-removing step must
verify no live caller first.

---

## Execution status — branch `api-consolidation` (updated 2026-06-26)

Landed = committed + pushed, green on what the local hooks actually run.
⚠️ **Correction (Phase 6):** the active hooks live in **`.githooks/`** (not `.husky/`, which
is stale). `.githooks/pre-push` *did* run per-service tests — but with **`-PskipIntegration`**,
which excludes the Testcontainers `*IntegrationTest` classes. So integration tests were skipped
locally and only ran in CI on push-to-`develop`/PR→`develop`. This is how the Phase 4
`7f761a31` PUT→POST flip left `EventWorkflowControllerIntegrationTest` sending PUT (→405)
undetected — it compiles fine (runtime routing mismatch) and no local gate executed it until
Phase 6 ran the full EMS suite. **Fixed in Phase 6:** `-PskipIntegration` removed from
`.githooks/pre-push` + `docs/api/*` mapped to owning services, so pre-push now runs the
integration suite for changed components (needs Docker).
Branch HEAD: `e9dd536a`. Commits this far (newest first): `e9dd536a` ($ref shared schemas
+ partner orphan FE cleanup), `7f761a31` (EMS lifecycle), `fb44bc5d` (CUMS removals),
`eb4d1fb6` (deprecate-half, now superseded), `af3cd875` (Phase 3 stub, now superseded),
`a7802ef3` (Phase 1), `4bce02ef` (Phase 0).

> **Phases 9a + 7 are on branch `api-consolidation-phase7`** (forked off `api-consolidation`).
> It carries the generator upgrade (9a) and the Phase-7 controller wiring. Phase 9a is the
> deliberate pre-req: upgrade the generator first so the Phase-7 controllers bind to the final
> 7.14.0 interface shape once.
>
> ✅ **Rebased onto `develop` 2026-06-27** — `api-consolidation` landed as squash **PR #815**
> (`refactor(api): ADR-013 consolidation …`). Because #815 is a squash, the branch was rebased
> with `git rebase --onto origin/develop <fork-point 5e15eeb3>` so only the **13 phase7-unique
> commits** replay on top (the duplicate `URL-encode listUsers filter` commit auto-dropped as
> "already upstream"; the one Bruno conflict resolved to develop's percent-encoded form). Verified
> post-rebase: CUMS + EMS compile, `UserControllerIntegrationTest` + `EventWorkflowControllerIntegrationTest`
> green. Backup: `backup/phase7-pre-rebase-3b43fe17`.
>
> ▶ **RESUME (next session):** continue EMS Phase 7 per-controller — pick the next controller
> (e.g. Sessions, Registrations, Analytics, Newsletter), **split its op-group out of the shared
> domain tag into a per-controller tag** (the owner-chosen re-tag strategy — see Phase 7 note),
> regenerate, wire `implements <Ctrl>Api`, consolidate any hand-written DTO twins, run that
> service's integration suite + Bruno live, commit. CUMS contract-first is DONE (6/6 documented
> prod controllers); EMS is 13/49 (EventTypes, SpeakerOutreach, AiPrompts, EmailTemplates,
> EventWorkflow, Analytics, Deregistration, TeaserImages, AiAssist, AgendaConfig, Participants,
> SessionQna, OrganizerThanks), Partner 5/10. **Autonomous run 2026-06-27 night:** wiring remaining EMS
> per-controller, Bruno+Playwright+commit each. **Key findings about the REMAINING ~24 EMS controllers
> (most are NOT clean per-controller wires):** (1) **Shared-DTO cluster** — ~7 hand DTOs (SpeakerPoolResponse,
> SessionResponse, TimetableResponse, SessionSpeakerResponse, SessionMaterialResponse, ContentSubmitResponse,
> SlidesOnlineSendResponse) are returned by MULTIPLE controllers + services; wiring any one to its generated
> return type needs a coordinated consolidation (or controller-level mapping) — not independent. (2) **Undocumented**
> controllers need spec authoring first: SpeakerPortal*, EventTask, TaskTemplate, SpeakerStatus, SpeakerReminder,
> VenueCoordination, Admin, AdminSettings, TopicSessionData, TopicSimilarity, EventQna, GlobalSession. (3) **Ad-hoc
> returns** (`ResponseEntity<?>`/`Map`) need typed contracts designed: SlotAssignment(6), Session(1), SessionMaterials(3),
> SpeakerReminder(2), EventController(5). (4) **Email-triggering** ops (RegistrantNotice/SpeakerInvitation/SlidesOnline/
> VenueCoordination send) — smoke read-only paths only. (5) **Defer**: Watch* (external watch-app contract, Phase 8);
> **skip**: DevEmail/TestFixtureCleanup (dev/test-only). **Lessons:** ALWAYS give Bruno the long Bash timeout (a
> SIGTERM'd Bruno run took EMS down mid-suite → misleading 500s); never re-list deleted files in `git add` (it aborts
> staging → a broken version got committed; use `git rm`/`git add -A`, and don't trust pre-commit checkstyle when the
> index diverged from the working tree).
>
> ⚠️ **PENDING PUSH (updated 2026-06-28):** the newest phase7 commits (AgendaConfig → OrganizerThanks →
> SlidesOnline + ContentSubmit consolidations + this plan note; remote tip stuck at `cf039e9c` = AiAssist) are
> **committed + individually verified**
> (each: its integration test + EMS restart + live gateway smoke + full Bruno 14/14 + FE type-check) but **NOT
> pushed** — the `.githooks/pre-push` gate (full backend integration + frontend vitest, ~12 min) was repeatedly
> **killed by a background-task wall-clock limit** in the agent runner before it could finish (it was PASSING both
> times). **Action:** run `git push origin api-consolidation-phase7` from a normal interactive terminal (not the
> agent's background runner) — it will complete uninterrupted. Do NOT `--no-verify`. The gate genuinely passes.
>
> ✅ **DONE (2026-06-28): `SpeakerInvitationController` → `SpeakerInvitationApi`** (event-speakers, 3 ops
> inviteSpeaker/inviteSpeakerBatch/sendSpeakerInvitation; tag was already 1:1, NO re-tag). Consolidated 6 hand
> DTOs (InviteSpeaker{Request,Response}, BatchInvite{Request,Response}+BatchInviteResponseErrorsInner,
> SendInvitation{Request,Response}) → generated `speakers.dto.generated` twins threaded through
> `SpeakerInvitationService` (record accessors→getters, builder-build responses); hand DTOs deleted. Class
> `@RequestMapping /api/v1`, bare override params, methods renamed to operationIds. Conversions: status
> `SpeakerWorkflowState`→`StatusEnum.fromValue(name())`, `Instant`→`OffsetDateTime`, `LocalDate` stays.
> **🐛 Same UPPER_CASE status-enum correction as SpeakerPoolResponse** — the spec enums were lowercase
> (`InviteSpeakerResponse.status` 11-value, `SendInvitationResponse.status` 'invited'); the wire is `.name()`
> UPPER_CASE (`SpeakerWorkflowState` has no `@JsonValue`) and the integration test asserts `IDENTIFIED`/`INVITED`
> → fixed both spec enums to UPPER_CASE (8-state / INVITED). **🐛 `@Future` lost:** the hand `SendInvitationRequest`
> had `@Future` on responseDeadline; OpenAPI can't express future-date, so restored the past-deadline 400 with a
> service-side guard. **Drift fix:** `SendInvitationRequest.locale` relaxed from a `de/en` enum back to free string
> (deployed behaviour accepts any locale, falls back to EN — avoids a contract narrowing). **Verified:** compile;
> SpeakerInvitationServiceTest + SpeakerInvitationControllerIntegrationTest (18 green, incl. send path w/ mocked
> mail + past-deadline 400); EMS restart + 5 safe-path live smokes (400/401 validation+auth — NO email sent);
> FE type-check + targeted FE invite tests (38). **Live send + `speaker-portal-api` Bruno deliberately NOT run**
> (those send real invitation emails; staging=prod → no-comms rule). RegistrantNotice is NOT clean (returns the
> shared `SlidesOnlineSendResponse`).
>
> ✅ **DONE (2026-06-28): `SelfNominationController` → `SpeakerSelfNominationApi`** (clean 1-op). **Re-tagged**
> `selfNominateSpeaker` out of the `Event Actions` grab-bag into a new `Speaker Self-Nomination` tag → 1:1
> interface (the rest of `EventActionsApi` — pool CRUD + promote — stays unimplemented until EventController/
> SpeakerStatusController are wired). Consolidated the hand `SelfNominateSpeakerRequest` → generated twin
> (`getAbstractText()`→`getAbstract()`, JSON field `abstract` via `@JsonProperty`); controller `implements`,
> class `@RequestMapping /api/v1`, bare param, `@PreAuthorize('ATTENDEE')` kept; returns the already-generated
> `SpeakerPoolResponse`. **🐛 `additionalProperties:false` → 400-on-unknown-field:** the hand DTO enforced it via
> `@JsonIgnoreProperties(ignoreUnknown=false)` (there is NO global FAIL_ON_UNKNOWN_PROPERTIES in EMS); openapi-gen
> doesn't translate `additionalProperties:false` to that annotation, so added it surgically via
> **`x-class-extra-annotation`** on just this schema (first use of that vendor extension in the repo — verified it
> lands on the generated class). **Verified:** compile; SelfNominationIntegrationTest 13/13 (incl. unknown-field→400,
> wrong-role→403, not-found→404, happy-path create); EMS restart + safe-path live smokes (401/403); FE type-check +
> SpeakerSelfNominatePanel 7/7; Bruno speaker-pool-api 34/34 (regression). Hand DTO deleted.
>
> ✅ **DONE (2026-06-28): `SpeakerStatusController` (8 ops) → `SpeakerStatusApi`** — the big one; retires the
> last speaker grab-bag. **Only `promoteSpeakerToReady` was documented**; the other 7 ops were undocumented, so
> **authored 7 ops + 9 schemas in event-speakers** by porting the proven, FE-validated definitions from the dormant
> `speakers-api` spec (ADR-014 copy pattern) under a 1:1 **Speaker Status** tag (re-tagged promote into it). Ops:
> updateSpeakerStatus, getSpeakerStatusHistory, getStatusSummary, getSpeakerContent, submitSpeakerContent,
> getReviewQueue, reviewSpeakerContent. **🎯 review-queue cleaned up (owner-approved Option A):** it returned the RAW
> SpeakerPool JPA entity (ADR-violating leak); now maps entity→`SpeakerPoolResponse` via `SpeakerPoolMapper` (same
> shape as GET /pool) — safe because the FE is cache-only on it and only id+status were asserted. **🔑 Mapped the spec
> `SpeakerWorkflowState` → `ch.batbern.shared.types.SpeakerWorkflowState`** via the generator import/schemaMappings, so
> the generated DTOs use the DOMAIN enum directly (zero status conversion in the services). **Consolidated 8 hand DTOs**
> (SpeakerStatusResponse, StatusHistoryItem, StatusSummaryResponse, SpeakerContentResponse, UpdateStatusRequest,
> SubmitContentRequest, ReviewRequest, PromoteSpeakerRequest) → generated, threaded through SpeakerStatusService +
> ContentSubmissionService + the controller; deleted the 8 hand DTOs. submitContent keeps returning the
> already-generated `ContentSubmitResponse` (deployed reality; the dormant spec's SpeakerContentResponse there was
> stale). SubmitContentRequest got the same `x-class-extra-annotation` unknown-field guard. Conversions:
> `Instant`→`OffsetDateTime` (changedAt/submittedAt), `StatusHistoryItem.Kind`→`KindEnum`,
> `ReviewRequest.ReviewAction`→top-level `ReviewAction`, statusCounts `Map<enum,Long>`→`Map<String,Long>` (keys = enum
> name). Controller: `implements SpeakerStatusApi`, class `@RequestMapping /api/v1`, bare override params, methods
> renamed to operationIds, `@PreAuthorize` + cache-evict kept. **Verified:** compile; SpeakerStatusServiceTest +
> SpeakerStatusControllerIntegrationTest (24) + QualityReviewServiceIntegrationTest (42 green); EMS restart + live read
> smokes (status-summary 11 fields, review-queue=SpeakerPoolResponse 34-field shape); full Bruno **dev** 14/14
> (mutations exercised, emails→/dev/mails); FE type-check + 283 FE tests (status/content/drawer). **EMS contract-first
> +1.** Note: tests run against **local dev** (staging = old code); single-editor repo → no backward-compat shims (the
> review-queue shape changed outright).
>
> ✅ **DONE (2026-06-28): `SessionSpeakerController` (5 ops) → `SessionSpeakersApi`** (commit `ef80da69`).
> First clean per-controller wire **unblocked by the now-complete shared-DTO cluster** — it returns the
> already-consolidated generated `SessionSpeaker`/`List<SessionSpeaker>`, no ad-hoc `Map`. **Re-tagged**
> the 5 ops (listSessionSpeakers / assignSpeakerToSession / removeSpeakerFromSession / confirmSpeaker /
> declineSpeaker) out of the broad `Sessions` tag into a new **Session Speakers** tag → 1:1 interface.
> Class `@RequestMapping` `/api/v1/events/.../speakers`→`/api/v1`; methods renamed to operationIds; bare
> override params (inherit `@Valid`/`@RequestBody`/`@PathVariable`/`@Pattern`); `@PreAuthorize('ORGANIZER')`
> kept on mutations. **Consolidated 2 hand DTOs** (`AssignSpeakerRequest`, `SpeakerConfirmationRequest`) →
> generated `AssignSpeakerToSessionRequest` + `DeclineSpeakerRequest`; deleted both. Conversions: generated
> `SpeakerRoleEnum`→domain `SpeakerRole` via `valueOf(name())` (identical UPPER constants); declineSpeaker
> body is `@Nullable required=false` → null-guard `getDeclineReason()`. **No FE change** — re-tag preserves
> all paths/schemas (`generate:api-types` diff empty). eventCode/sessionSlug/username `@Pattern` now enforced
> via method-validation (malformed eventCode → 400; consistent w/ prior EMS wirings). **Verified (local dev):**
> `SessionSpeakerControllerIntegrationTest` 9/9; EMS restart + live gateway smoke (GET speakers 200 w/ full
> `SessionSpeaker` shape, assign no-auth 401, bad-role 400, malformed-eventCode 400); **Bruno sessions-api
> 20 req / 62 tests / 24 assertions PASS** incl. assign/confirm/decline/remove/duplicate/multiple live
> mutations (no emails — controller triggers no comms); FE type-check clean. **EMS contract-first +1 (18 wired).**
>
> ✅ **DONE (2026-06-28): `SessionMaterialsController` (5 ops) → `SessionMaterialsApi`** (commit `10ae292a`).
> Heaviest category so far — the 5 material ops were **entirely undocumented** AND returned ad-hoc `Map`s.
> **Authored 5 ops + 4 schemas** in event-sessions under a new **Session Materials** tag (associateMaterials,
> getSessionMaterials, deleteSessionMaterial, getMaterialDownloadUrl, uploadMaterialFromUrl; schemas
> MaterialUploadItem, SessionMaterialAssociationRequest, SessionMaterialsResponse, MaterialDownloadUrlResponse,
> UploadMaterialFromUrlRequest). **Path params kept permissive** (eventCode decorative → NO `@Pattern`; the
> controller looks up by sessionSlug only; materialId uuid) — deliberately avoids the cross-controller
> `@Pattern` fixture trap (the test uses non-conforming `bat-bern-2026-spring`). **Typed the 3 ad-hoc shapes:**
> `Map<String,Object>{"materials":[]}` → `SessionMaterialsResponse`, `Map<String,String>{"downloadUrl"}` →
> `MaterialDownloadUrlResponse`, `Map<String,String>` request → `UploadMaterialFromUrlRequest` (controller keeps
> its defensive blank-url/filename→400 guard + DOCUMENT default). **Consolidated 2 hand DTOs**
> (SessionMaterialAssociationRequest, MaterialUploadItem) → generated twins through `SessionMaterialsService`
> (identical getters, `fileSize` Long) + both tests (import swap only — generated `builder()` API matches Lombok);
> deleted the hand DTOs. **🐛 Spec-truth fix surfaced by the live smoke:** `getSessionMaterials` (list) actually
> **requires auth** (SecurityConfig permits anonymous only on `.../materials/{id}/download`) → marked it
> `bearerAuth`, kept download public. **Verified (local dev):** full EMS suite **1750/0/0** (confirms the permissive
> params caused zero cross-controller breakage); SessionMaterials controller+service+**schema-validation 33/33**;
> EMS restart + live gateway smoke (list authed 200 typed wrapper / anon 401, associate anon 401 + empty-list 400
> via `@Size`, download bogus-uuid 404, upload anon 401); Bruno sessions-api 20/20 (0 skipped); FE regen + type-check
> clean. **EMS contract-first +1 (19 wired).**
>
> ✅ **DONE (2026-06-28): `EventPhotoController` (5 ops) → `EventPhotosApi`** (commit `6b15affa`).
> Clean wire — event-media-api is photos-only so the `Event Photos` tag is already 1:1 (NO re-tag),
> ops fully documented, typed returns (no ad-hoc `Map`). Methods→operationIds; bare params; dropped
> the unused `Authentication` param; `@PreAuthorize` kept. **Consolidated 4 hand `EventPhoto*Dto`** →
> generated twins through `EventPhotoService` + both tests (type-name swap; generated `builder()`
> matches Lombok); deleted. Only conversion: `EventPhotoResponse.uploadedAt` Instant→OffsetDateTime
> (`.atOffset(UTC)`, wire stays `…Z`). **🐛 Spec drift-fix:** `EventPhotoUploadRequest.contentType`
> enum(jpeg/png/webp)→plain string — the deployed contract validates content-type **server-side**
> (`ALLOWED_TYPES`→`InvalidFileTypeException`→**422 + friendly message**); a wire enum would change
> that to a Jackson 400 (losing the message) and make the service's `image/gif`/`null`→422 unit tests
> impossible to construct. Keeping it a string preserves behaviour + keeps the service the single
> validation authority. **Verified (local dev):** full EMS suite **1750/0/0**; EventPhoto controller+
> service tests 23/23; EMS restart + live smoke (recent-photos/list 200 w/ `uploadedAt …Z`, upload-url
> no-auth 401, invalid-type 422 w/ friendly msg, valid jpeg 200 w/ presigned url + s3Key); FE regen +
> type-check clean. No Bruno photos collection (FE consumes via eventApiClient). **EMS contract-first
> +1 (20 wired).**
>
> ✅ **DONE (2026-06-28): `SessionController` (7 ops) → `SessionsApi`** (event-sessions). The core session
> CRUD controller. **Re-tagged** the lone `assignSessionToSlot` op (SlotAssignmentController) out of the broad
> `Sessions` tag into a new **Slot Assignment** tag → `SessionsApi` == SessionController's 7 ops exactly 1:1
> (listSessions / getSession / createSession / patchSession / deleteSession / batchImportSessions /
> generateStructuralSessions). Class `@RequestMapping /api/v1/events/{eventCode}/sessions`→`/api/v1`; bare
> override params; methods already matched operationIds; `@PreAuthorize`/`@CacheEvict`/`@Transactional` kept.
> **Consolidated 4 hand DTOs** (CreateSessionRequest, BatchImportSessionRequest, BatchImportSessionResult,
> SessionImportDetail) → generated twins through SessionController + SessionBatchImportService; deleted all 4 +
> the dead `SessionMapper.toEntity` (prod-unused, only tested). **Typed the 2 ad-hoc shapes:** list
> `Map<String,Object>{data,pagination}` → generated `ListSessions200Response` (data mapped via the **pure**
> `SessionMapper.toDto` — no speaker enrichment / N+1, list stays lean; replaces a raw-JPA-entity leak); patch
> `Map<String,Object>` request → generated `PatchSessionRequest`. Conversions: `OffsetDateTime`→`Instant` on
> create (`.toInstant()`), `request.getSessionAbstract()`→`getAbstract()`, `SessionImportDetail.status`
> String→`StatusEnum`, nested `BatchImportSessionRequest.LegacySpeaker`→top-level generated `LegacySpeaker`;
> the 4 hand static factories (`success/updated/skipped/failed`) → private builder helpers in the service.
> **5 spec drift-fixes (all truthful-to-deployed):** (1) `createSession` 201 `Session`→`SessionResponse`
> (controller has always returned the full SessionResponse w/ speakers/materials). (2) list `data` items
> `Session`→`SessionResponse` + `page` default `0`→`1` (**the deployed `PaginationUtils.parseParams` throws on
> page≤0** — a `0` default would 400 every default list call). (3) `generateStructuralSessions` request-**body**
> `GenerateStructuralSessionsRequest`→**query param** `overwrite` (the FE `slotAssignmentService` + the integ
> test both send `?overwrite=`; the body schema was never real — schema dropped). (4) `CreateSessionRequest`:
> marked `sessionType`/`startTime`/`endTime` **required** (the deployed hand DTO `@NotBlank`/`@NotNull` rejected
> them missing; the spec falsely advertised placeholder support that never worked) and `sessionType` enum→**string**
> (DB has `aperitif`; mirrors the `Session.sessionType` decision, avoids narrowing). (5) batch: added
> `BatchImportSessionRequest.materialUrl` + `BatchImportSessionResult.updated` + `SessionImportDetail.status`
> enum value `updated` (all live in the deployed hand DTOs but absent from the spec → generated twins would have
> dropped them). **🐛 `@Pattern` fixture trap (3 cross-controller tests):** the now-enforced eventCode
> `^BATbern[0-9]+$` turns malformed not-found probes into 400 — fixed `StructuralSession` (`BATbernXXX`→`BATbern888`,
> not `999` which IS the fixture), `SecurityConfig` (`NON_EXISTENT_EVENT`→`BATbern888`), `EventController`
> (`non-existent-id`→`BATbern888`). **Verified (local dev):** full EMS suite green (1747 run / 0 fail after the 3
> fixture fixes; SessionBatchImport + Structural + SessionMapper targeted green); EMS restart + live gateway smoke
> (list real BATbern55 → `{data:[SessionResponse],pagination}` 1-indexed, no `id`, `…Z` timestamps; single-get w/
> 1 enriched speaker; malformed eventCode→400, valid-absent→404, malformed slug→400, structural/create no-auth→401);
> **Bruno sessions-api 20 req / 62 tests / 24 assertions PASS** (live create/update/delete on fixture event + speaker
> sub-resources, with cleanup); FE regen (only event-sessions types) + type-check + session vitest 50/50. Hand DTOs
> deleted. **EMS contract-first +1 (21 wired).** Note: `SlotAssignmentApi` (1 op) now exists, unimplemented until
> SlotAssignmentController is wired.
>
> ✅ **DONE (2026-06-28): `NewsletterController` (16 ops) → `NewsletterApi`** (event-newsletter). The
> meatiest EMS wire so far. **Re-tagged** the 3 foreign ops sharing the `Newsletter` tag —
> `sendSlidesOnline` → **Slides Online**, `previewRegistrantNotice`+`sendRegistrantNotice` → **Registrant
> Notice** — so `NewsletterApi` == NewsletterController's 16 ops 1:1 (SlidesOnlineApi + RegistrantNoticeApi
> created, unimplemented until those controllers wire). **Authored the previously-undocumented
> `getSubscriberCount` op** (`GET /newsletter/subscribers/count`) + `SubscriberCountResponse` schema.
> `implements NewsletterApi`; bare override params; methods renamed to operationIds; `@PreAuthorize` kept.
> **Consolidated 9 hand DTOs** (NewsletterPreviewResponse, NewsletterSendRequest, NewsletterSendResponse,
> NewsletterSendStatusResponse, NewsletterSubscribeRequest, NewsletterSubscriptionStatusResponse,
> NewsletterUnsubscribeRequest, PatchMySubscriptionRequest, SubscriberResponse) → generated twins threaded
> through `NewsletterSubscriberService` + `NewsletterEmailService`; deleted all 9. **Typed the 2 ad-hoc
> Maps:** verifyUnsubscribe `Map<String,String>` → `VerifyUnsubscribeToken200Response`; subscribers/count
> `Map<String,Long>` → `SubscriberCountResponse`; list `PaginatedResponse` → `ListNewsletterSubscribers200Response`
> (identical `{data,pagination}` wire). **🐛 Spec drift-fixes (truthful-to-deployed):** (1) **added `testMode`
> to NewsletterSendRequest** — the FE sends it and the controller reads it to gate whether real recipient
> emails go out; the spec omitted it, so the generated request would have **silently dropped testMode → a
> "test" send would have mailed all active subscribers**. Live-verified: a `testMode:true` preview now reports
> `recipientCount:1` (organizer only) vs the full active count. (2) added `language` to
> `PatchNewsletterSubscriptionRequest` (controller reads it). (3) response `reminder`→`isReminder` — the
> deployed hand DTO emitted `reminder` (Lombok `boolean reminder`→Jackson strips `is`), but the spec/FE types
> declare `isReminder`; consolidating **aligns the wire to the spec** (no FE reads the old key). The unused
> response `testMode` field drops (no consumer). Conversions: `Instant`→`OffsetDateTime` (sentAt/startedAt/
> completedAt/subscribedAt/…, `…Z` byte-identical), status `String`→`StatusEnum.fromValue`, request `locale`
> `LocaleEnum`→`.getValue()` (kept the de/en enum — newsletter is DE+EN only per the localization rule).
> **Verified (local dev):** 73 newsletter tests (`NewsletterControllerIntegrationTest` 36 / `NewsletterSubscriberServiceTest`
> 3 / `NewsletterEmailServiceTest` 34); EMS restart + live gateway smoke — getSubscriberCount 200 {totalActive},
> list {data,pagination}, verify-bogus 404, my-subscription no-auth 401, preview testMode 200 (recipientCount 1),
> **full `testMode:true` SEND cycle → PENDING→COMPLETED sent=1/failed=0 to /dev/mails** (EMS log confirmed), status
> poll + history; **full Bruno suite 14/14** (incl. the email-sending `speaker-portal-api`, run because local dev
> sandboxes mail to /dev/mails); FE regen (additive: count op, testMode, language) + type-check; Playwright harness
> ran against local dev (3 roles authed) + smoke 2/2 — the one newsletter-touching spec (`progressive-publishing`)
> is `describe.skip`-disabled in the repo so it asserts nothing. Full EMS suite deferred to the pre-push gate (the
> agent runner's wall-clock killed it at 107/0-fail; this change is newsletter-confined — no shared @Pattern/enum/
> schema, no cross-controller path overlap). **EMS contract-first +1 (22 wired).**
>
> ✅ **DONE (2026-06-28): `SlidesOnlineController` (1 op) + `RegistrantNoticeController` (2 ops)** → their
> generated `SlidesOnlineApi` / `RegistrantNoticeApi`. Fast follow-ups to the Newsletter wire — both tags
> (`Slides Online`, `Registrant Notice`) were already split out there, and the interfaces already existed.
> `SlidesOnlineController` was a trivial wire (already returned the generated `SlidesOnlineSendResponse`):
> `implements SlidesOnlineApi`, bare param, drop `@PostMapping`, keep `@PreAuthorize`. `RegistrantNoticeController`
> renamed `preview`→`previewRegistrantNotice` / `send`→`sendRegistrantNotice`; **consolidated 3 hand DTOs**
> (RegistrantNoticePreviewRequest, RegistrantNoticePreviewResponse, RegistrantNoticeSendRequest) → generated
> twins (threaded `RegistrantNoticePreviewResponse` through `SlidesOnlineEmailService`); deleted all 3. Only
> conversion: preview `locale` `LocaleEnum`→`.getValue()` (de/en). No spec edit (the re-tag landed with Newsletter)
> → no FE regen. **Verified (local dev):** `SlidesOnlineIntegrationTest` 8 + `SlidesOnlineEmailServiceTest` 7
> green (the integ test covers both controllers' send paths end-to-end incl. the 409 double-send guard); EMS
> restart + live gateway smoke — slides-online/registrant send no-auth 401, registrant preview (organizer, de)
> 200 {subject,htmlPreview,recipientCount=168}, non-REGISTRANT_NOTICE template → 400 (category guard). Live
> send deliberately NOT triggered (would blast 168 real registrant mails to /dev/mails; the integration test
> exercises the send path instead). **EMS contract-first +2 (24 wired).**
>
> ✅ **PARTIAL DONE (2026-06-28): `EventController` speaker-pool ops → new `EventSpeakerPoolController` →
> `EventActionsApi`** (4 ops: getSpeakerPool / addSpeakerToPool / deleteSpeakerFromPool / patchSpeakerPoolEntry).
> Rather than re-path the 2,573-line public-facing EventController to wire 4 self-contained ops, **extracted**
> them into a dedicated `EventSpeakerPoolController implements EventActionsApi` (class `/api/v1`), leaving
> EventController's CRUD/registration/EventDetail paths 100% untouched (zero public-site risk). Consolidated the
> 2 hand request DTOs (AddSpeakerToPoolRequest, PatchSpeakerPoolRequest) → generated twins (perfect field parity;
> threaded through `SpeakerPoolService`, both methods only called from here); deleted both. **Preserved the
> unknown-field→400 guard** (`additionalProperties:false`) by adding `x-class-extra-annotation`
> `@JsonIgnoreProperties(ignoreUnknown=false)` to both schemas in event-speakers-api (the SelfNomination pattern)
> — verified the `email`→400 tests still pass. `speakerId` path param is now `UUID` (interface) →
> `.toString()` for the service. Removed `speakerPoolService` from EventController. **🐛 `@Pattern` fixture fix:**
> `SpeakerPoolWorkflowIntegrationTest` not-found probe `INVALID999`→`BATbern888` (eventCode `@Pattern` now enforced).
> **Verified (local dev):** `SpeakerPoolWorkflowIntegrationTest` 12 + `SpeakerPoolServiceTest` green; EMS restart +
> live smoke (GET pool 200, malformed eventCode→400, add-with-email→400 guard, patch ORGANIZER-gated; GET-nonexistent
> →400 is pre-existing service behavior, not a regression); FE regen empty (x-class-extra is Java-only). **EMS
> contract-first +1 (25 wired).**
>
> ⛔ **REMAINDER STILL DEFERRED — needs a dedicated FE-coordinated design story: `EventController` CRUD/EventDetail.**
> The 2,573-line central controller still owns `EventsApi` (7 CRUD), `EventReportingApi` (2), `BulkOperationsApi`
> (1) plus ~15 registration/topic/publish ops whose tags are shared across controllers (so they'd stay
> hand-rolled). **Two independent blockers found 2026-06-28:**
> 1. **EventsApi core response model** — the deployed hand `EventResponse` uses `Map<String,Object>` topic/venue
>    + `List<Map>` sessions (dynamic `?include=` expansion) and embeds a RICH session/speaker shape carrying
>    `materials`, session `id`, and speaker `bio`/`company`/`companyDisplayName`/portraits that the FE reads
>    (e.g. `SessionEditModal` reads `session.materials`). The generated `EventDetail.sessions` is the lean typed
>    `Session` (no materials/id) + `SessionSpeaker` (no bio/company). Live verification showed the rest is
>    ALREADY wire-compatible (`EventType`/`EventWorkflowState` are enums serialising to the deployed strings;
>    `topic`/`venue` Maps already emit the typed `EventTopic`/`Venue` shape; `Instant`→`OffsetDateTime` is
>    `…Z`-identical) — so the ONLY real divergence is the rich embedded session/speaker/materials shape + the
>    per-verb `Event`(mutations) vs `EventDetail`(getEvent) split.
>    **▶ Owner-chosen approach (Nissim, 2026-06-28): slim the embedded sessions to the base typed `Session` and
>    have the FE LAZY-LOAD materials + speaker company/portrait details from the dedicated APIs**
>    (SessionMaterialsApi, the user/company clients) on demand (e.g. when opening `SessionEditModal`), instead of
>    fat-embedding them in every event GET. That makes `EventDetail.sessions = Session` clean AND shrinks the
>    event payload. Needs the FE drawer/modal refactor (fetch-on-open) → its own FE-coordinated story.
> 2. **BulkOperationsApi.batchUpdateEvents** — generated `BatchUpdateResponse` is `{successful:int, failed:int}`
>    but the deployed batchUpdate emits `{successful:[…], failed:[…], summary}` (lists + summary). Wiring as-is
>    would break the FE; needs the spec response schema made truthful first (or the FE adjusted). **EventReportingApi**
>    `getEventAnalytics` returns a freeform `Map` → typing to `EventAnalytics` is a separate ad-hoc-shape mapping
>    (`getAttendanceSummary` is fine — just needs the hand→generated `AttendanceSummaryDTO` swap).
> **Recommendation:** file the EventsApi piece as the lazy-load FE story above; do BulkOps/EventReporting as small
> follow-ups once their response schemas are made truthful. Do NOT bundle into the incremental Phase-7 sweep.
>
> ✅ **DONE (2026-06-29): `TopicController` (8 ops) → `TopicsApi`** (commit `fb426795`, EMS 26 wired).
> Clean wire — the controller already returned generated `dto.generated.topics.*` types. **Merged** the
> single-controller `Topic Analysis` tag (getSimilarTopics / getTopicUsageHistory / calculateSimilarities)
> **into `Topics`** so `TopicsApi` == TopicController's 8 ops 1:1 (`selectTopicForEvent` stays under the
> `Event Topics` tag → generated `EventTopicsApi`, unimplemented until the deferred EventController wires).
> **Enabled api-interface generation for the topics spec** (build.gradle `openApiGenerateTopics`:
> `interfaceOnly false→true`, `useTags false→true`, `apis false→''`) — it was previously models-only.
> Controller: `implements TopicsApi`, class `@RequestMapping /api/v1/topics`→`/api/v1`, bare override params,
> `getAllTopics`→`listTopics`, `getUsageHistory`→`getTopicUsageHistory`; `@PreAuthorize` kept (listTopics stays
> public for archive filtering). **🐛 calculateSimilarities clean-up:** controller returned a bare JSON
> string (`ResponseEntity<String>` "Similarity scores…") but the spec/FE generated type already declared
> `{message}` (latent mismatch) → now returns the generated `CalculateSimilarities200Response{message}`; FE
> `topicService.calculateSimilarities()` reads `.message` (no FE callers beyond the service+test, so safe).
> **Spec drift-fix:** `listTopics` `limit` default `20`→`50` (truthful to the deployed controller default);
> the generated interface now enforces `@Max(100)` on limit + `@Pattern ^[a-z0-9-]+$` on topicCode via
> method-validation (the not-found UUID probe `123e4567-…` is all-lowercase-hex so it still passes the pattern
> → 404, not 400). No FE type diff (re-tag is tags-only; the calc-sim type was already `{message}`).
> **Verified (local dev):** `TopicControllerIntegrationTest` + `TopicServiceTest`/`StalenessScoreServiceTest`/
> `TopicMapperTest` green; EMS restart + live gateway smoke (list typed `{data,pagination}` w/ UPPER status
> enum, limit>100→400, getTopicByCode no-auth→401, malformed code→400, calc-sim `{message}`, usage-history
> 404 probe, create→delete cycle); full Bruno **14/14** (no EMS topics collection — regression only); FE
> type-check + `topicService` 29/29; Playwright organizer `topic-selection` + `blob-topic-selector` **11/11**
> (incl. the create-topic @smoke). **EMS contract-first +1 (26 wired).**
>
> ✅ **DONE (2026-06-29): `TopicSimilarityController` (1 op) + `TopicSessionDataController` (1 op)** → generated
> `TopicSimilarityApi` / `TopicSessionDataApi` (commits `1c3b0f76`, `10b90009`; EMS 27 then 28 wired). Both are
> the blob-topic-selector canvas endpoints (Story 10.4), previously **undocumented**. **Authored the ops +
> schemas in topics-api** under two new 1:1 tags (`Topic Similarity`, `Topic Session Data`), ported from the
> FE-validated hand DTOs. **eventCode kept permissive (NO @Pattern)** on both — it is contextual only (the
> services ignore it: similarity classifies on topic text, session-data aggregates globally). Consolidated all
> hand DTOs → generated topics twins: TopicSimilarity{Request,Response} (2); TopicSessionDataResponse + its 4
> static nested types → top-level `PartnerTopicGroup`/`TopicEntry`/`BacklogItem`/`PastEventEntry` (builder API
> identical, nested refs flattened to imports); deleted all. Conversions: similarity `topic` @NotBlank→@Size(min=1)
> (blank ""→400; whitespace-only untested); session-data `TopicEntry.createdAt` Instant→OffsetDateTime
> (`.atOffset(UTC)`, wire `…Z` byte-identical). **Verified (local dev):** TopicSimilarity 6/6 + TopicSessionData
> 4/4 integration; EMS restart + live gateway smoke (similarity AI→AI_ML 0.85+events, blank→400, no-auth→401;
> session-data full nested payload w/ `createdAt …Z`, no-auth→401); FE regen (additive only — blob hand-types
> unchanged, wire byte-identical) + type-check; Playwright `blob-topic-selector` 8/8 (exercises both ops). The
> **topics-api spec is now fully contract-first** — only `selectTopicForEvent` (Event Topics) remains, owned by
> the deferred EventController. **EMS contract-first +2 (28 wired).**
>
> ✅ **DONE (2026-06-29): `TaskTemplateController` (4 ops) → `TaskTemplatesApi`** (commit `0aea4fac`, EMS 29 wired).
> **Bootstrapped a new `event-tasks-api` spec + generator task** (`openApiGenerateTasks` →
> `ch.batbern.events.tasks.{api,dto}.generated`, wired into compileJava/sourceSets/clean — modelled on the
> sessions task) for the previously-undocumented Configurable Task System (Story 5.5). Authored 4 ops under a
> 1:1 `Task Templates` tag (listAllTemplates/createTemplate/updateTemplate/deleteTemplate) + the 3 schemas,
> ported from the FE-validated hand DTOs. **triggerState/dueDateType kept as free strings (NOT enums)** to match
> the deployed contract (the service validates them). Controller `implements TaskTemplatesApi`: class
> @RequestMapping /api/v1, bare override params, @PreAuthorize(ORGANIZER) + the IllegalState→403 default-template
> guards kept. Consolidated the 3 hand DTOs → generated twins; **replaced the `TaskTemplateResponse.fromEntity`
> static factory with a private `toResponse` mapper** (Instant→OffsetDateTime `.atOffset(UTC)`, wire `…Z`); deleted
> all 3. name/triggerState/dueDateType @NotBlank → required + @Size(min=1). **Verified (local dev):**
> TaskTemplateController + Service + TestFixtureCleanup template tests 24/24; EMS restart + live gateway smoke
> (list 9 w/ full shape + `…Z`, no-auth→401, missing-name→400, create→201, delete→204); FE type-check clean (the
> FE `taskService` uses hand types matching the wire byte-for-byte — `event-tasks` is NOT in the FE generate list,
> consistent with the other EMS-internal specs, so no FE-types regen); Bruno tasks-api 16 req / 39 tests / 0
> skipped; Playwright `admin-task-templates-crud` @smoke 1/1 (full UI CRUD). **EMS contract-first +1 (29 wired).**
> Follow-up unblocked: `EventTaskController` (10 ops, Story 5.5) can now extend this same `event-tasks-api` spec.
>
> ✅ **DONE (2026-06-29): `EventTaskController` (10 ops) → `EventTasksApi`** (commit `cfa2a684`, EMS 30 wired).
> Extended the just-created `event-tasks-api` with the 10 event-task ops under a 1:1 `Event Tasks` tag
> (listEventTasks/createAdHocTask/createTasksFromTemplates/getMyTasks/getAllTasks/completeTask/reassignTask/
> updateTaskStatus/updateTask/deleteTask) + 7 schemas (EventTaskResponse + the 5 request DTOs +
> CreateTasksFromTemplatesRequest with nested TemplateConfig), ported from the FE-validated hand DTOs. All ops
> return typed `EventTaskResponse`/`List`/`Void` — **no ad-hoc Maps**. eventCode kept permissive (controller
> resolves by code → 404 via EntityNotFoundException); status validated via @Pattern
> (pending|todo|in_progress|completed); `critical` query defaults false. Controller `implements EventTasksApi`:
> class @RequestMapping /api/v1 (replaced the per-method full `/api/v1/...` paths), bare override params,
> @PreAuthorize(ORGANIZER) kept. Consolidated 7 hand DTOs → generated twins; replaced the
> `EventTaskResponse.fromEntity` factory with a private `toResponse` mapper; deleted all 7. Conversions: request
> `dueDate` OffsetDateTime→Instant (`.toInstant()`) at the service boundary; response Instant→OffsetDateTime
> (`.atOffset(UTC)`, `…Z` byte-identical) on dueDate/completedDate/createdAt/updatedAt. **Verified (local dev):**
> EventTaskController + Service + fixture-cleanup tests 21/21; EMS restart + live gateway smoke (my-tasks/
> all-tasks 200, event-scoped list 200 w/ 6 tasks + `…Z`, missing event→404, no-auth→401, invalid status→400
> @Pattern); FE type-check clean (taskService hand types byte-identical, event-tasks not in FE generate list);
> Bruno tasks-api 16 req / 39 tests / 0 skipped; Playwright test-task-creation-from-templates 2/2. **The
> Configurable Task System (Story 5.5) is now fully contract-first — event-tasks-api owns both controllers.**
> **EMS contract-first +1 (30 wired).**
>
> ✅ **DONE (2026-06-29): `SpeakerPortalDashboardController` (1 op) → `SpeakerPortalDashboardApi`** (EMS 31 wired).
> First of the 3 speaker-portal controllers. Authored the undocumented GET /speaker-portal/dashboard op in
> **event-speakers-api** (reuses that spec — submitContent already returns its generated `ContentSubmitResponse`)
> under a 1:1 `Speaker Portal Dashboard` tag + 3 schemas (SpeakerDashboardDto + nested DashboardUpcoming/Past,
> all string/boolean/int — no dates/enums). Controller `implements`: class @RequestMapping /api/v1 (was
> /api/v1/speaker-portal), class @PreAuthorize(SPEAKER) kept. **Dropped the HttpServletRequest method param**
> (not in the generated signature) — IP-for-audit-logging now via a new package-private
> `SpeakerPortalHttp.clientIp()` (RequestContextHolder), shared by the 2 follow-up portal controllers, so the
> log lines stay unchanged. Consolidated 3 hand DTOs → generated (builder API identical; sort comparators
> `::eventDate`→`::getEventDate`); updated SpeakerDashboardServiceTest accessors→getters; deleted all 3.
> **Verified (local dev):** SpeakerDashboardServiceTest 5/5; EMS restart + live smoke (speaker 200 shape,
> no-auth→401, organizer→403); FE type-check clean (FE speakers types come from the DORMANT speakers-api spec,
> not event-speakers — wire byte-identical); Bruno speaker-portal-api 24/24. **EMS contract-first +1 (31 wired).**
>
> ⏳ **REMAINING speaker-portal (2 controllers, deeper than Dashboard — needs careful work, NOT a quick wire):**
> - **`SpeakerPortalResponseController`** (1 op, POST /speaker-portal/events/{eventCode}/respond). Blast radius
>   beyond the controller: the request's nested **`SpeakerResponsePreferences`** propagates into the **workflow
>   layer** — `TransitionPayload.responsePreferences` (field type), `SpeakerResponseService.storePreferences`,
>   and `SpeakerResponseReceivedEvent` — and consolidating it changes `technicalRequirements` `String[]`→
>   `List<String>`. The `response` field is the shared `SpeakerResponseType` enum (ACCEPT/DECLINE) → map via
>   importMappings+schemaMappings (mirror the `SpeakerWorkflowState` precedent in the speakers generator). 2 test
>   files touch these DTOs (SpeakerResponseServiceTest, SpeakerPortalAuthIntegrationTest). The respond path
>   triggers acceptance/decline emails — exercise only on local dev (mail → /dev/mails).
> - **`SpeakerPortalContentController`** (4 ops: getContentInfo / submitContent / materials presigned-url +
>   confirm). Most complex: record DTOs (ContentSubmitRequest has `@JsonIgnoreProperties(ignoreUnknown=false)` →
>   needs `x-class-extra-annotation`; consumed via record accessors `.title()`/`.contentAbstract()` → getters),
>   SpeakerContentInfo has a `noSession(...)` static factory to relocate, material DTOs (SpeakerMaterial{Upload,
>   Confirm}{Request,Response}) with `Map<String,String> requiredHeaders` + `Instant uploadedAt`→OffsetDateTime,
>   and submitContent already returns the generated `ContentSubmitResponse` (reuse, no re-author). Touches
>   ContentSubmissionService + SpeakerPortalMaterialsService. All 3 controllers share the new `SpeakerPortalHttp`
>   IP helper. Bruno speaker-portal-api already covers all of these for regression.
>
> ℹ️ **Skipped (orphan — flag for removal, not wiring): `GlobalSessionController`** (`GET /api/v1/sessions?companyName`).
> Investigated 2026-06-28: **no FE consumer, no Bruno coverage, no integration test** — effectively dead.
> Candidate for Phase-1-style dead-endpoint removal (or a deliberate decision to keep+document+test), NOT a
> Phase-7 wire. Returns the shared `PaginatedResponse<CompanySessionResponse>` (hand DTO, only consumer is this
> controller).
>
> 🗒️ **Verification cadence (Nissim, 2026-06-28):** per-controller wire → run the **targeted slice**
> (`--tests <Ctrl>IntegrationTest [+ServiceTest]`) + live gateway smoke + FE type-check, commit; the
> `.githooks/pre-push` gate runs the **full** `:ems:test` (~10 min) on Nissim's machine as the authoritative
> cross-controller check (the agent runner's ~10-min background limit kills it ~half the time). Run the full
> suite locally myself only for cross-controller-risky changes (added/changed path `@Pattern` or a shared
> schema/enum). `maxParallelForks` is NOT an option — already tried, OOMs on the multiplied Spring context-cache
> (hence single fork + `maxHeapSize=2g`).

## Shared-DTO consolidation (design + progress — 2026-06-28)

**Goal:** the ~7 hand DTOs returned by multiple controllers block per-controller `implements`-wiring.
Consolidate each to its generated twin (delete the hand DTO), then the consumers can be wired.
**Strategy:** mapper-centric, dependency-ordered (leaves first), ONE DTO per commit, each verified
(compile + affected integration tests + FE regen/type-check + smoke + Bruno), committed LOCALLY
(push gate is unusable in the agent runner — see PENDING PUSH above).

**Dependency graph:** `SessionMaterial + SessionSpeaker + TimetableSlot → SessionResponse → TimetableResponse`;
`SpeakerPoolResponse`, `ContentSubmitResponse`, `SlidesOnlineSendResponse` independent.

| Shared DTO | Status | Notes |
|---|---|---|
| **SlidesOnlineSendResponse** | ✅ DONE | generated (newsletter); `STATUS_PENDING`→`StatusEnum.PENDING`; service + SlidesOnline/RegistrantNotice ctrls + test; hand DTO deleted. 18/18. |
| **ContentSubmitResponse** | ✅ DONE | schema COPIED from dormant speakers-api → event-speakers-api (ADR-014); ContentSubmissionService + SpeakerStatus/SpeakerPortalContent + integ test (record accessors→getters); hand DTO deleted. 40/40. |
| **SpeakerPoolResponse** | ✅ DONE (2026-06-28, Option A) | Consolidated to the generated `speakers.dto.generated.SpeakerPoolResponse` (34 fields). **Extracted `mapper.SpeakerPoolMapper`** (`@Component`: `toResponse`/`toResponse(pool,session)`/`toResponseWithContent`/`applyProposal`) replacing the 3 static factories; injected into `SpeakerPoolService` (+5 call sites) + `SpeakerStatusController:156`. Both overlay paths re-typed (import-only): `PrimarySpeakerResolver.applyOverlay` + `SpeakerPoolService.applySessionIdentityOverlay` (setters byte-identical). `EventController` (3 endpoints) + `SelfNominationController` re-pointed (import/FQN). **Trimmed 5 fields** (FE-unread off the pool entry, grep-verified): `isPublishable`, `contentStatus`, `materialCloudFrontUrl`, `remindersDisabled`, `materialFileName` — which also let the **material-enrichment block (+`sessionMaterialsRepository` dep + N-per-row query) be removed** from the list path (3 dedicated `SpeakerPoolServiceTest` cases deleted; the `remindersDisabled` toggle endpoint/entity keep the field). Conversions: `SpeakerWorkflowState`→`StatusEnum.fromValue(state.name())`, `String source`→`SourceEnum`, 6× `Instant`→`OffsetDateTime` (`…Z` byte-identical), `LocalDate` unchanged. **🐛 DESIGN CORRECTION — status wire is UPPER_CASE, not lowercase.** The design assumed lowercase_snake + `.name().toLowerCase()`; reality: the hand DTO emitted `getStatus().name()` (UPPER) and the ENTIRE FE compares uppercase literals (`'CONTENT_SUBMITTED'`…). DB stores lowercase but a JPA converter maps it; the **wire was always uppercase**. Authored the spec enum as the 8 UPPER_CASE states (dropped slot_assigned/confirmed/withdrew/overflow, added INVITED). FE `speakerPool.types.ts`: `SpeakerPoolEntry`/`SpeakerWorkflowState` repointed to the generated `event-speakers-api` type (was the dormant `speakers-api`). **Verified:** compile; SpeakerPoolServiceTest + PrimarySpeakerResolverTest + SpeakerStatusControllerIntegrationTest(24) + SelfNominationControllerIntegrationTest (39 green); EMS restart + live kanban smoke (BATbern57/73 — uppercase enum, 34 fields, dropped absent, overlays live); Bruno speaker-pool-api 78/78 (fixed `36-list` isPublishable assert); FE type-check + full vitest 5334. Hand DTO deleted. **⏸️ Controller `implements`-wiring deferred** (per consolidation precedent): `SpeakerStatusController` (8 ops, many other hand DTOs) needs a full re-tag + all-ops-documented pass; `SelfNominationController` (clean 1-op) is a small follow-up. The other two enum blocks in event-speakers-api (a username+status schema @ ~1535 [stale 11-value lowercase], `SendInvitationResponse.status` @ ~1699) are separate schemas left out of scope. |
| **SessionSpeakerResponse → `SessionSpeaker`** | ✅ DONE | generated twin `SessionSpeaker` (sessions). Single ctor site = `SessionUserService.enrichWithUserData` (×2) → builds generated; conversions `profilePictureUrl` String→`URI` (pass `user.getProfilePictureUrl()` directly), domain `SpeakerRole`→`SpeakerRoleEnum` via `fromValue(name())` (extracted `toSpeakerRoleEnum` helper), `isConfirmed` boolean→Boolean. Consumers re-typed: `SessionResponse`/`CompanySessionResponse` embed `List<SessionSpeaker>`, `SessionService`/`GlobalSessionController`/`SessionSpeakerController`/`NewsletterEmailService` + 2 tests. Wire byte-stable (all `@JsonProperty` identical, `@JsonValue` enum; additive `companyLogoUrl:null`). Test getter shift `isConfirmed()`→`getIsConfirmed()`, response enum asserts→`SessionSpeaker.SpeakerRoleEnum.*` (kept entity `captured`/`sessionUser` on domain `SpeakerRole`). Verified: compile + SessionUserServiceTest/NewsletterEmailServiceTest + SessionSpeaker/StructuralSession integ + EMS restart + live smoke (real BATbern1 speaker) + Bruno 14/14 + FE type-check. Hand DTO deleted. |
| **SessionMaterialResponse** | ✅ DONE | NO twin existed → authored a `SessionMaterialResponse` schema in event-sessions (NOT `SessionMaterial` as first planned — collides with domain entity `domain.SessionMaterial` in the `toResponse` mapper; `*Response` also matches ContentSubmit/SlidesOnline naming). 14 fields; `cloudFrontUrl` plain string, `id` string/uuid→`UUID`, only conversion `createdAt`/`updatedAt` Instant→`OffsetDateTime` via `.atOffset(UTC)` (byte-identical wire `…Z`, verified live). Single ctor site `SessionMaterialsService.toResponse`; consumers re-pointed import/FQN only — name unchanged (`SessionResponse` needed an explicit import — was same-package; `SessionService`, `SessionMaterialsController`, `EventController` FQN, + test). `globalProperties models:''` generates the unreferenced schema. Verified: regen+compile main+test; SessionMaterialsServiceTest + Controller/SchemaValidation integ; FE regen+type-check; EMS restart + live smoke (BATbern36 material); Bruno 14/14. Hand DTO deleted. |
| **SessionResponse** | ✅ DONE | twin was `allOf:[Session]` and DROPPED `materials`/`materialsCount` → added them to the SessionResponse schema (inline `allOf` extension). **Ride-along drift fixes (both REQUIRED — the stale enums would 500 on real data):** `Session.sessionType` enum→**string** (DB has `aperitif`, absent from the enum — verified a live aperitif session now returns 200, would've been `fromValue` 500) and `Session.materialsStatus` enum→**string** (DB has `PARTIAL`, absent from the enum). Zero backend blast radius (no Java consumes the generated `Session`/its enums); FE widens enum→string (type-check clean). Mapper `SessionMapper.toDto`: timestamps Instant→`OffsetDateTime` via `.atOffset(UTC)` (wire `…Z` byte-identical, live-verified), sessionType/materialsCount/materialsStatus stay String/Integer (now match generated). `SessionService.toSessionResponse` builder type `SessionResponseBuilder`→`Builder`. Consumers re-pointed (import/FQN): `TimetableResponse` (new import — was same-package), `TimetableService`, `StructuralSessionService`, `SessionController`, `EventController` (FQN), + `SessionMapperTest` (4 timestamp asserts String→OffsetDateTime), Structural/Timetable test import/FQN (`new SessionResponse()` + setters intact). Verified: compile main+test; SessionMapper/Structural/Timetable unit + StructuralSession/SlotAssignment integ; FE regen+type-check; EMS restart + live smoke (full shape incl. aperitif-200); Bruno 14/14. Hand DTO deleted. |
| **TimetableResponse (+TimetableSlot)** | ✅ DONE (boundary-mapper, owner-chosen) | **Not a delete-the-hand-DTO consolidation** — investigation showed `TimetableSlot` is a dual-purpose internal **scheduling/keying** model, not a pure DTO: `Instant` arithmetic + a `Map<Instant,Session>` exact-match in `TimetableService`, ~25 arithmetic sites, 32 `Type` refs, and `SlotReorderService` consumes `getTimetable().getSlots()` internally for reorder logic. A full Instant→OffsetDateTime + Type→TypeEnum rewrite (~60 sites) was high-risk for working scheduling code with no current wiring payoff. **Owner chose boundary-mapper:** keep the internal hand `TimetableSlot`/`TimetableResponse` (Instant) untouched; `getTimetable` still returns the internal model (SlotReorder still works); a new `TimetableMapper.toWire` converts internal→generated (`Instant→OffsetDateTime` UTC, `Type→TypeEnum` by name) only at the controller boundary. **Wired `TimetableController` → generated `TimetableApi`** (re-tagged `getEventTimetable` Sessions→**Timetable** so the 1-op interface is 1:1; class `@RequestMapping /api/v1/events/{eventCode}`→`/api/v1`, `@PreAuthorize` kept on the override). `SlotAssignmentController.assignSessionToSlot` maps at its boundary too. Internal types + their tests (`TimetableServiceTest`, `SlotReorderServiceTest`) untouched. Verified: compile main+test; new `TimetableMapperTest` + internal-service unit + SlotAssignment integ; FE regen (no change — re-tag only) + type-check; EMS restart + live smoke (`GET /timetable` 200, correct slot shape incl. `…Z` timestamps); Bruno 14/14. **EMS contract-first +1 (TimetableController).** |

> ⚠️ **SpeakerPoolResponse — DESIGN RESOLVED (Winston/Nissim, 2026-06-28).** The generated
> `SpeakerPoolResponse` spec schema has **15 fields**; the deployed hand DTO has **39**. **Owner question
> answered: yes, keep ~35 of them — this is NOT a dead-field grab-bag.** Field-level FE consumption analysis
> (every reader traced across `web-frontend/src`, non-test) shows **~35 of 39 fields are live**, and the
> "fatness" is structural, not accidental: the organizer **kanban board** (`SpeakerStatusLanes.tsx`) is one
> LIST endpoint whose cards **expand into an inline drawer that reads off the already-loaded board entry**
> (`DetailsTabPanel`, `ContentSubmissionSubView`, `QualityReviewSubView`, `PromoteSpeaker*` all read
> `speaker.*` off the list item — no per-card refetch). That is a legitimate "collection + inline detail"
> read model. The 24 hand-DTO-only fields have exactly **one** consumer — the FE's hand-written
> `SpeakerPoolEntry` (`web-frontend/src/types/speakerPool.types.ts`); the Swift watch app carries only the
> 15-field generated model and never reads it; no other backend/Bruno consumer.
>
> **Field verdicts (verified, not from the subagent's first pass — it under-counted by 6):**
> - **LIVE (~35)** — core/identity (id, eventId, speakerName, company, companyDisplayName, expertise,
>   assignedOrganizerId, status, notes, createdAt, updatedAt, username), session (sessionId, sessionSlug),
>   brainstorm provenance (source, proposedByUsername, proposedSessionTitle, proposedAbstract), invitation
>   (email, invitedAt, responseDeadline, contentDeadline), response-prefs (acceptedAt, declinedAt,
>   declineReason, preferredTimeSlot, travelRequirements, technicalRequirements, initialPresentationTitle,
>   preferenceComments — all read in `SpeakerStatusLanes.tsx:1043-1091`), content/derived (contentSubmittedAt,
>   submittedTitle, submittedAbstract, isSlotAssigned).
> - **DEAD in FE → TRIM (4)** — `isPublishable` (FE derives from `isSlotAssigned`+`status` in
>   `getPrimaryAction.ts:160`; backend still computes per ADR-009 §0.5), `contentStatus` (derived server-side,
>   never read off the pool entry), `materialCloudFrontUrl` (pool copy unread), `remindersDisabled` (no reader).
> - **BORDERLINE → verify then likely trim (1)** — `materialFileName`: read on the speaker-portal's *own*
>   `SpeakerEventContent` type and on `QualityReviewSubView`'s `content.*`, but **not** off the pool entry.
>
> **⚠️ Ride-along correctness fix (do NOT skip):** the generated `SpeakerPoolResponse.StatusEnum` still
> carries **11 status values** including the four ADR-009 §0.1 DELETED (`SLOT_ASSIGNED`, `CONFIRMED`,
> `WITHDREW`, `OVERFLOW`) + `INVITED`. The spec is stale vs the 8-state ADR; the FE already moved to the
> 8-state union (`speakerPool.types.ts:9-13`, "legacy widenings dropped per Story 11.E.4"). **Author the
> consolidated schema with the 8 ADR-009 states — do not copy the stale 11 forward.**
>
> **Decision: Option A (consolidate-whole + trim dead), NOT a split.** A board-summary / item-detail split
> (Option B — lean list + `GET …/speakers/{id}` detail fetched on drawer-open) is the "cleaner REST" design
> and a real improvement, but it is **orthogonal to and heavier than** the Phase-7 consolidation: it forces an
> FE data-flow change (drawer must fetch-on-open instead of reading the loaded entry) across 4 sub-views, a
> new endpoint, all under staging=prod. Bundling it would make the consolidation commit un-reviewable.
> **→ File Option B as its own FE-coordinated follow-up story; do Option A now to unblock per-controller wiring.**
>
> **▶ Option A mechanics (ready to implement):**
> 1. **Spec** — in `docs/api/event-speakers-api.openapi.yml`, expand the `SpeakerPoolResponse` schema to the
>    ~35 live fields (drop `isPublishable`, `contentStatus`, `materialCloudFrontUrl`, `remindersDisabled`;
>    confirm `materialFileName` unused off the entry then drop). Set `status` to the **8-state** enum
>    (`identified, contacted, ready, invited, accepted, content_submitted, quality_reviewed, declined`) and
>    keep `source` as the 2-value enum. Mark only the 6 always-present fields required (id, eventId,
>    speakerName, status, createdAt, updatedAt); everything else nullable. Regen FE types
>    (`npm run generate:api-types`) → `event-speakers-api.types.ts`, then repoint
>    `web-frontend/src/types/speakerPool.types.ts` `SpeakerPoolEntry` to the generated `components['schemas']
>    ['SpeakerPoolResponse']` (it already imports the workflow-state from generated — collapse the hand-written
>    interface onto the generated type, keeping `SpeakerPoolUI extends` for the resolved `assignedOrganizerName`).
> 2. **Mapper extraction** — the mapping is embedded as static `fromEntity(SpeakerPool)` /
>    `fromEntity(SpeakerPool, Session)` / `fromEntityWithContent(SpeakerPool, Session, SessionContentVersion)`
>    + instance `applyProposal(SessionProposal)` on the hand DTO, PLUS **two** identity-overlay paths that
>    must both be preserved: `PrimarySpeakerResolver.applyOverlay(response, pool)` (single-entry paths) and
>    `SpeakerPoolService.applySessionIdentityOverlay(response, primarySessionUser, userResponse)` (the batch
>    list path, `SpeakerPoolService.java:356-379`). Extract the 3 factories + `applyProposal` into a
>    `SpeakerPoolMapper` (`@Component`, returns the generated DTO via its `builder()`); leave the two overlay
>    methods where they are but re-type them to mutate the generated DTO (their setters are byte-identical).
> 3. **Type conversions** (hand → generated):
>    - `status`: `SpeakerWorkflowState` (Java enum) → `SpeakerPoolResponse.StatusEnum` via
>      `StatusEnum.fromValue(state.name().toLowerCase())` (wire values are lowercase_snake — see §Enum flow).
>    - `source`: `String` ('organizer_added'/'self_nomination') → `SourceEnum.fromValue(source)` (null-safe).
>    - **6× `Instant` → `OffsetDateTime`**: createdAt, updatedAt, invitedAt, acceptedAt, declinedAt,
>      contentSubmittedAt → `instant.atOffset(ZoneOffset.UTC)` (null-guard each).
>    - **2× `LocalDate` stays `LocalDate`**: responseDeadline, contentDeadline (generated emits `LocalDate`).
>    - Derived flags `isSlotAssigned` (and the trimmed `isPublishable`) computed exactly as today
>      (ADR-009 §0.5: `session.startTime != null`; `isPublishable = QUALITY_REVIEWED ∧ isSlotAssigned`).
> 4. **Call sites (5 builders)** — `SpeakerStatusController:156` (promote → `fromEntity` + `applyOverlay`),
>    `SelfNominationController` (self-nominate → `fromEntity` + `applyOverlay` + `applyProposal`),
>    `SpeakerPoolService:136/263/459/509` (add/self-nominate/patch/× → `fromEntity` + overlay) and `:364`
>    (the list, `fromEntityWithContent` + `applyProposal` + `applySessionIdentityOverlay`). After the mapper
>    swap these call `speakerPoolMapper.toResponse(...)`; record accessors → generated getters/builders.
>    `EventController` only imports/embeds — no SpeakerPoolResponse build there (its `enrichWith*` are for
>    `EventResponse`).
> 5. **Wire `SpeakerStatusController` + `SelfNominationController` `implements <Ctrl>Api`** once the DTO is the
>    generated type (the actual Phase-7 unblock this consolidation exists to enable).
> 6. **Verify** (per §Strategy): compile; `SpeakerPoolServiceTest` + `SpeakerStatusControllerIntegrationTest`
>    + `SelfNominationControllerIntegrationTest`; EMS restart + **live gateway smoke of the kanban LIST**
>    (`GET /events/{code}/speakers`, real BATbern event — read-only, no email) confirming all ~35 fields
>    serialize with the 8-state enum; full Bruno 14/14; FE `npm run generate:api-types` + type-check + full
>    vitest (the `SpeakerStatusLanes`/drawer suites exercise the trimmed fields). Commit LOCALLY (push gate
>    unusable in agent runner — see PENDING PUSH).
>
> **Follow-up (separate story): Option B board/detail split** — lean `SpeakerPoolSummary` for the board +
> `GET /events/{eventCode}/speakers/{id}` → `SpeakerPoolDetail`; drawer fetches on open. Real ADR-013
> collection/item improvement + smaller list payload, but needs the FE drawer-fetch refactor; do not couple
> to the consolidation above.

| Phase | Status | Notes |
|---|---|---|
| **0 Shared-kernel** | ✅ **DONE** | Deleted dead duplicate `utils.ErrorResponse` + `ErrorHandlingUtils` (+test); added `docs/api/_shared.openapi.yml`; fixed `04-api-core.md`. |
| **1 Dead-spec removal** | ✅ **DONE** | events-api stale `/topics*` block + partners-api orphan voting removed (spec). **Frontend completion (in `e9dd536a`):** removed the dead orphan-voting client `getPartnerVotes`/`usePartnerVotes` (+tests) that called the never-implemented 404. |
| **2 Document live routes / re-enable generators** | ❌ **WON'T DO** | Owner decision (2026-06-26), now **ADR-014**: speaker/attendee services stay dormant (consolidating toward an EMS/CUMS few-service backend), so **do NOT re-enable their generators**. The route-documentation bit is dropped too (low value, codegen risk on the wired partners-api). Phase closed. |
| **3 Convention conformance** | ✅ **DONE** | ✅ **Shared schema solution DONE** (`e9dd536a`): shared `$ref` `_shared.openapi.yml` for both generators. ✅ **List-query collapse DONE**: `listUsers` (CUMS) `role`/`company`/`sortBy`/`sortDir` → `filter`/`sort`; **5 live callers migrated** (email-forwarder Lambda ×2 + partner `getUsersByRole` + EMS `getOrganizerUsernames`/`getPartnerUsernames` — the latter 3 were **undocumented in the original plan**) using fully-encoded `URI`s. `listNewsletterSubscribers` (EMS) `sortBy`+`sortDir` → `sort` (typed `status`/`search` kept). Both controllers now use shared `SortParser`. **🐛 Bug fixed:** users-list sort was a no-op — all 8 paginated `UserRepository` queries hardcoded `ORDER BY u.lastName ASC`, overriding the `Pageable` sort; removed so server-side sort actually works (+ fixed a `Set.of(...).contains(null)` NPE it exposed). **🐛 Bug fixed (2026-06-27, re-diagnosed):** NOT a missing predicate. The `filter` parser + `UserRepository` correctly apply the singular `role` key (proven by `UserControllerIntegrationTest.should_filterByRole_when_roleFilterProvided`, which passes with `filter={"role":"ORGANIZER"}`, and used by the FE `userManagementApi` + the email-forwarder Lambda). The real failure: Bruno `users-api` 15/19/20 still sent the **removed** top-level `?role=` param, so `listUsers` ignored it and returned role-unfiltered results. The plan's `{"roles":…}` (plural) was a misdiagnosis — that plural key is the response **projection** field, not the filter key. Fixed by migrating the 3 Bruno tests to `?filter={"role":…}`. See §Phase 3. |
| **4 Mutation-model fixes** | ✅ **DONE** | ✅ **CUMS removals DONE** (`fb44bc5d`). ✅ **EMS lifecycle DONE** (`7f761a31`). ✅ **Session PUT removed** (`ed59fa0b`): dead full-replace PUT twin (no caller; field-nulling footgun) deleted + dead `UpdateSessionRequest` DTO/`SessionMapper.applyUpdateRequest`; spec now documents the live `patch:` (`PatchSessionRequest`). ✅ **Partner deactivation DONE** (`d6fdc5b0`): dropped dead `isActive` from `UpdatePartnerRequest` (backend ignored it; FE toggle unwired) — DELETE is the canonical soft-deactivate. ✅ **Registration-cancel resolved** — NOT a merge (the two are distinct flows). Investigated the legacy JWT `/cancel`: confirmed **dead** (no email template renders `cancellationUrl`; all use the Story-10.12 `/deregister` UUID flow) and **removed** it end-to-end — endpoint, `generate/validateCancellationToken`, the dead `cancellationToken`/`cancellationUrl` threaded through the registration-confirmation email path, spec path, FE `CancelRegistrationPage` + route + `eventApiClient.cancelRegistration`, and all tests. The shared `RegistrationService.cancelRegistration(Registration)` (used by `/deregister` + waitlist) stays. ✅ **Spec polish DONE:** named the inline `object` request bodies as `AssignSpeakerToSessionRequest`/`DeclineSpeakerRequest`/`PatchNewsletterSubscriptionRequest` (batchImportSessions already used a named items schema). Reconciled `UpdateEventSlotConfigurationRequest` ⟷ `UpdateEventAgendaConfigRequest` by **documenting the distinction** (cross-referenced descriptions: event-type-level defaults vs per-event copy-on-edit, differing required-field strictness) rather than a structural `allOf` merge — they are genuinely distinct contracts on different endpoints, and `UpdateEventAgendaConfigRequest` is a hand-written backend DTO, so an `allOf` merge would risk a live feature's generated types for no real gain. **Phase 4 COMPLETE.** |
| **5 Partner consolidation 5→2** | ✅ **DONE** | Folded `partner-notes-api` + `partner-analytics-api` + `partner-topics-api` into the generator-wired `partners-api.openapi.yml` (5→2; `partner-meetings-api` kept separate + brought to parity: shared `$ref` `ErrorResponse`, documented `GET /partner-meetings/{id}/rsvps` + the internal RSVP callback, bounded-list note). `/attendees/topics` relocated under a dedicated **Attendee Topics** tag (documented alias). **Spec made truthful**: added the live-but-undocumented `PATCH`/`DELETE /partners/topics/{topicId}` (updateTopic/deleteTopic) and `eventTitle` on `AttendanceSummaryRecord`; clarified `getPartnerStatistics` (portfolio summary) vs `analytics/dashboard` (attendance) boundary; normalized tags + relative `/api/v1` server + global `bearerAuth`. **Controllers rewired** to implement the generated interfaces: `PartnerNoteController`→`PartnerNotesApi`, `PartnerAnalyticsController`→`PartnerAnalyticsApi` (export now returns `Resource`), `TopicController`→`PartnerTopicsApi` (role resolved from `SecurityContextHolder`, no injected `Authentication`), `AttendeeTopicController`→`AttendeeTopicsApi`. Hand-written record/Lombok DTOs (PartnerNoteDTO, CreateNoteRequest, UpdateNoteRequest, TopicDTO, TopicSuggestionRequest, TopicStatusUpdateRequest, PartnerDashboardDTO) deleted — generated DTOs thread through the service layer (Instant→OffsetDateTime, String→inner enums). FE: deleted `partner-notes/partner-topics` generate scripts + stale `.types.ts`, repointed `partnerNotesApi.ts` to `partner-api.types`. Full BE partner-coordination suite + full FE vitest (5334) + FE type-check green. |
| **6 events-api decomposition** | ✅ **DONE** | Carved the 10.3k-line `events-api.openapi.yml` (94 paths / 122 ops / 131 schemas / 16 tags) into **9 per-domain specs** — `events-core` + `event-{sessions,speakers,registrations,newsletter,media,ai,analytics,watch}-api` — driven by a deterministic carve script (path→domain map + computed schema ownership; report-only validated first). **Paths preserved verbatim** (122/122 ops, 0 dangling refs, no dup ops). Only cross-spec coupling is `core → sessions` (Event embeds `List<Session>`); every other spec depends only on shared-kernel. **Full per-domain Java + TS packages** (owner's explicit choice): 9 `openApiGenerate<Domain>` Gradle tasks (each → `ch.batbern.events.<domain>.{api,dto}.generated`), 9 FE `event*-api.types.ts`. **165 Java FQN re-points** across 102 files (`dto.generated.X` → `<domain>.dto.generated.X`) + 4 controller interface re-points (EventTypes→core, SpeakerOutreach→speakers, AiPrompts→ai, EmailTemplates→newsletter). **54 FE files re-pointed** (51 single-domain swap + 3 multi-domain aliased imports). **🐛 openapi-generator bug #17647 worked around:** `schemaMappings` to a cross-package type emits illegal `List<@Valid <FQN>>`; switched core's Session/SessionSpeaker to `importMappings` (import + simple name) + a `doLast` that deletes the dead duplicate copy. 2 defined-but-unreachable schemas (`Speaker`, `RegistrationAdminResponse`) **removed** as dead-code cleanup (follow-up): `Speaker` was an ADR-004-violating User-field duplicate with only a dead `SpeakerUI` alias; `RegistrationAdminResponse` had no path/code use. Updated: security-scan matrix (1→9 entries), BATbern-watch `generate-types.sh` (loops 9 specs), FE generated README. Clean Java compile (main+test) + FE type-check (0 errors) + EMS suite + FE vitest green. Single PR.
| **7 Contract-first completion** | 🚧 **IN PROGRESS** (branch `api-consolidation-phase7`, after 9a) | **Platform-wide** (see §Phase 7), root cause of the Phase 4 PUT→POST drift. Few controllers `implements` their generated `*Api` interface: **EMS 5/49, CUMS 6/13 (all documented prod controllers), Partner 5/10**, speaker/attendee dormant. Wire the rest + consolidate hand-written DTOs shadowing generated schemas (EMS has ~51 shadows). Slice by service (CUMS→EMS→Partner); medium risk. **Generator upgraded to 7.14.0 first (9a)** so controllers wire against the final interface shape (plain `@Nullable` params, no `Optional` unwrapping). **CUMS progress:** ✅ PresentationSettingsController→`PresentationSettingsApi` (`cbade6c4`); ✅ PublicUserController→`PublicApi` (`707ecfdc`) — both with hand-written→generated DTO consolidation, integration + Bruno + Playwright verified. ⏸️ **Deferred** (documented): Watch controllers (WatchAuthController returns `ResponseEntity<?>` + ad-hoc `{"message":...}` body — typed-contract clash affecting the external watch app; record→class DTO migration), PublicOrganizerController (undocumented — no spec/generated iface). **Partner progress:** ✅ PartnerContactController→`PartnerContactsApi` (`2abe6b38`, no DTO migration — already used the generated DTO); integration + full partner suite + Bruno partners-api verified. Remaining partner controllers (PartnerMeeting/RsvpController) need the partner-meetings-api generator wired first. ✅ **CompanyController + UserController wired** (see §Phase 7 progress note for the full record): CUMS is now **4/13** (Company, PublicUser, PresentationSettings, User). ⏳ **Next:** UserPreferences/UserSettings; PublicOrganizer + Logo are undocumented (spec-first); then EMS (4/45). |
| **8 Domain-boundary corrections** | ⏳ **TODO** | Relocate misfiled endpoints (`/public/settings/features` off `AiAssistController`; `/attendee-portal/dashboard` → attendee domain) + normalize Watch paths that hard-code `/api/v1/`. Client-affecting (watch app); see §Phase 8. |
| **9a Generator upgrade** | ✅ **DONE** (branch `api-consolidation-phase7`) | Upgraded openapi-generator **7.2.0 → 7.14.0** (both pins: `settings.gradle` + root `build.gradle`). **#17647 is fixed** → removed the EMS `core→sessions` workaround (`importMappings`+`doLast`); `Session`/`SessionSpeaker` now use plain `schemaMappings` and `Event.java` emits the legal `pkg.@Valid Session`. Did this **before Phase 7** so controllers wire against the final interface shape once. Churn was tiny: param type `Optional<X>`→`@Nullable X` (fixed `EmailTemplateController`); enum prefix no longer stripped, `WELCOME`→`AFTER_WELCOME` (wire values unchanged; fixed 4 refs in `EventTeaserImageServiceTest`); Java-client models-only now injects an unsatisfied `ApiClient` import → switched partner's `generateCompanyClientDtos`/`generateUserClientDtos` to the `spring` generator (consistent w/ EMS). **🐛 Wire-behaviour fix:** 7.14 default-initialises collection fields (`= new ArrayList<>()`) instead of leaving them null, so an unset list serialised as `[]` instead of being absent — broke the `?include=` sparse-fieldset contract (`TopicControllerIntegrationTest` saw `usageHistory: []` when not embedded). Fixed by adding `containerDefaultToNull: 'true'` to **every** generator `configOptions` block (CUMS ×2, EMS ×11, partner ×3) → restores pre-7.14 null-default. All other changes additive (per-DTO inner `Builder`, `@Nullable` field annots). Whole Java monorepo compiles main+test; FE unaffected (its types come from `openapi-typescript`, not this plugin). See §Phase 9. |
| **9 Tooling & spec hygiene (rest)** | ⏳ **TODO** | Generator upgrade done (→ 9a). Remaining: declare top-level `tags`; fix stale `workflowService.ts` PUT comments. Low risk; see §Phase 9. |

---

## Shared-schema standard (decided + verified 2026-06-26 — use for ALL specs)

**One canonical definition in `docs/api/_shared.openapi.yml`** (full `properties` **+**
`x-java-type`), `$ref`'d by every spec:
```yaml
PaginationMetadata:
  $ref: './_shared.openapi.yml#/components/schemas/PaginationMetadata'
```
Why it works for both generators (proven on companies-api against both):
- **`openapi-typescript` 7.x** resolves the external `$ref` (built-in Redocly bundling) → **full TS type**.
- **`openapi-generator` (Gradle)** resolves the ref AND maps by *name* via the existing
  `schemaMappings`/`importMappings` → shared-kernel class, **no duplicate DTO**.
- ❌ Do NOT use a body-less `x-java-type` stub (the abandoned Phase 3 approach): it's fine
  for Java but `openapi-typescript` emits `Record<string, never>` (empty type) → breaks the FE.

**Latent landmines still to fix (peripheral specs, optional consistency pass):**
`events-api`/`partners-api` were already done in `e9dd536a`. But `speakers-api`,
`attendees-api`, `partner-meetings/notes/topics/analytics`, `file-upload`, `auth-endpoints`
still have bespoke/empty `ErrorResponse` (and some `Pagination`) shapes — convert them to
`$ref` `_shared` too when convenient (low risk; most aren't generator-wired).

---

## Versioning rule for this repo (decided 2026-06-26)

**Single-user system, we own backend + frontend + the email-forwarder Lambda.** So: **remove
redundant endpoints outright and adapt all callers in the same commit — NO `deprecated:`
period.** (This supersedes the deprecate-then-remove guidance in the "Versioning & deprecation
approach" section below; that section is kept for historical context only.)

---

## ▶ RESUME GUIDE — finish Phase 3 & 4, then 5 & 6

Workflow per change: edit spec → `npm run generate:api-types` (FE) + Gradle regen/compile
(BE) → fix tests → restart the touched service (`make dev-native-restart-service SERVICE=…`)
→ Bruno + Playwright @smoke → commit → push. `.githooks/pre-push` now runs the FULL
integration suite (incl. Testcontainers) for changed components — Docker must be up, expect
multi-minute pushes on backend/spec changes; bypass with `git push --no-verify` only in an
emergency.
**Env gotchas:** macOS has no `timeout` (use the Bash tool's own param); dev Postgres is the
existing `batbern-dev-postgres` container (`docker start` it); refresh tokens with
`./scripts/auth/refresh-token.sh staging [role] </dev/null`; run the **FULL** FE suite
(`npx vitest run`) before pushing — a targeted run missed `partnerApi.test.ts` once and the
pre-push hook rejected the push.

### Phase 3 — list-query collapse ✅ DONE (one open bug, see below)
**Landed** (see status table above): `listUsers` + `listNewsletterSubscribers` collapsed to `filter`/`sort`,
5 live `?role=` callers migrated, shared `SortParser` adopted, and the dead users-list server-side
sort (hardcoded JPQL `ORDER BY`) fixed. Original notes retained below for context.

> ✅ **Bug fixed — re-diagnosed 2026-06-27 (was: "role filter not applied").** The original
> diagnosis (filter parser doesn't apply the role predicate) was **wrong**. The singular `role`
> filter key works end-to-end: `UserController.listUsers` → `extractFilterString(filter, "role")`
> → `UserService.listUsersPaginated` → `UserRepository.findByRolesContainingWithRoles`. Proven by
> `UserControllerIntegrationTest.should_filterByRole_when_roleFilterProvided` (passing,
> `filter={"role":"ORGANIZER"}` → exactly the ORGANIZER user) and exercised in prod by the FE
> `userManagementApi` (`filterObj.role = …`) + the email-forwarder Lambda. The spec, FE, and
> controller all standardize on the **singular** `role` key (`{"role":"SPEAKER"}`).
> **Actual root cause:** the Bruno tests `15-list-users-filter-by-role`,
> `19-list-users-combined-filters`, `20-list-users-filter-by-role-organizer` still sent the
> **removed** top-level `?role=…` param (the pre-collapse contract), which `listUsers` no longer
> reads — so the result set was role-unfiltered and the tests' `roles`-array assertions failed. The
> `{"roles":…}` (plural) in the earlier note conflated the response **projection** field with the
> filter key. **Fix applied:** migrated the 3 Bruno tests to `?filter={"role":…}` (combined test
> folds `active` into the same JSON object). No production/code change. (The other documented
> filter keys — `company`, `active`, `search` — already have backing predicates and pass.)
>
> ⚠️ **Encoding gotcha (2026-06-27, cost a failed PR re-run).** A JSON `filter` with raw `{ } "`
> in a Bruno `.bru` URL is rejected by the **API gateway with 400** (RFC 3986 disallows unencoded
> braces), and Bruno also silently DROPS a `params:query` value containing raw braces (→ `filter=null`
> → unfiltered → role-mismatch). Neither the parse-only check nor a `params:query` block catches this.
> **Working form:** percent-encode the filter in the `url:` line
> (`?filter=%7B%22role%22%3A%22ATTENDEE%22%7D`) and disable the readable `params:query` twin with
> `~filter:`. **Always run filter-based Bruno tests against a LIVE service, not just `--env unreach`
> parse checks.** Validated live: full Bruno suite **14/14** on both `api-consolidation` and
> `api-consolidation-phase7`; `listUsers` ATTENDEE→20, ORGANIZER→6, combined→12/13 (role predicate
> confirmed applied). Same encoding fix landed on both branches.

Goal (ADR-013 §3): list endpoints expose **one** vocabulary (`filter`/`sort`), not ad-hoc
`role`/`company`/`search`/`status`/`sortBy`/`sortDir`. Lowest-risk approach: **keep the
service/repository logic; change only the controller param surface** and parse `filter`/`sort`
into the existing service args.
- **`listUsers`** (`UserController.listUsers` + `users-api`): drop `role`/`company`/`sortBy`/`sortDir`.
  ⚠️ **`?role=` is an infrastructure contract** — the email-forwarder Lambda calls
  `GET /api/v1/users?role=ORGANIZER` unauthenticated (`infrastructure/lambda/email-forwarder/
  sender-auth.ts:96`, `address-resolver.ts:130`). Update BOTH Lambda files to the new
  `filter` form in the same commit (+ their unit tests `infrastructure/test/unit/email-forwarder.test.ts`).
- **`listNewsletterSubscribers`** (`NewsletterController` + events-api): drop `search`/`status`/`sortBy`/`sortDir`.
  ⚠️ Current params are **enum-validated** (`status: [all,active,unsubscribed]`, `sortBy: [...]`);
  prefer keeping that validation (don't dump everything into a stringly JSON blob — fold sort
  into a single `sort=-subscribedAt` and keep typed filter params, or document the filter keys).
- Frontend callers: `web-frontend/src/services/api/userManagementApi.ts` (builds role/company/sortBy/sortDir),
  `web-frontend/src/services/api/newsletterApi.ts` (builds search/status/sortBy/sortDir).
- Tests: backend controller/integration tests for both list endpoints; FE `newsletterApi.test.ts`, user-mgmt tests.

### Phase 4 — remaining EMS items (CUMS + lifecycle already done)
- **`session` PUT vs PATCH** (`events-api` `PUT /events/{eventCode}/sessions/{sessionSlug}`):
  `UpdateSessionRequest` is byte-identical to create & mostly optional → make PUT a true
  full-replace (require full shape) OR demote to PATCH (recommended: PATCH). Backend `SessionController`.
- **Name the inline `object` request bodies** (events-api): `assignSpeakerToSession`,
  `declineSpeaker`, `patchMyNewsletterSubscription`, `batchImportSessions`. Spec typing →
  regen FE types (gives named TS types). Low risk.
- **Merge slot-config schemas** (events-api): `UpdateEventSlotConfigurationRequest` ⟷
  `UpdateEventAgendaConfigRequest` overlap → reconcile to one schema or base+extension.
- **Registration-cancel merge** (events-api): `POST /registrations/cancel` (email token) vs
  `POST /registrations/deregister` (deregistration token) are two token-cancel flows → keep
  one (likely-legacy = `cancelRegistration`). Keep `DELETE …/my-registration` (authed) +
  `/registrations/deregister/by-email` (request-link). FE callers: `deregistrationService.ts`,
  `eventApiClient.ts` (`registrations/cancel`), `registrationService.ts`. Behavioral — verify with Bruno + PW.
- **Partner deactivation — pick one path** (partners-api): `isActive=false` in
  `PATCH /partners/{companyName}` AND `DELETE /partners/{companyName}` (soft-delete) both
  deactivate. Choose one (recommend DELETE = soft-deactivate; drop `isActive` from the PATCH body),
  update `PartnerController` + FE `partnerApi.ts` + tests.

### Then Phase 5 (partner 5→2) and Phase 6 (events-api decomposition)
Full specs are in the Phase 5 and Phase 6 sections below. Both are large, single-PR efforts.
Recommended order: finish 3 + 4 (above), then 6 (decomposition — unblocks cleaner per-domain
specs), then 5 (partner consolidation).

---

## Versioning & deprecation approach

All specs are under `/api/v1`. We are **not** cutting `/api/v2`. The cleanups fall in three
buckets:

1. **Dead-spec removal (no behaviour change).** Endpoints that the server never implemented
   or no longer serves (events-api UUID `/topics/{id}`, partners-api voting subsystem).
   These are spec-only deletions — zero runtime risk once verified the server doesn't serve
   them. **Do these first.**
2. **Documentation-truth fixes (no behaviour change).** Inline `ErrorResponse` /
   `PaginationMetadata` → shared stub; doc corrections; spec for undocumented live routes.
   No client impact.
3. **Genuine contract changes (client impact).** Removing a redundant *live* endpoint
   (second upload initiator), dropping a PUT twin, collapsing a list query vocabulary.
   These get: (a) a usage check against access logs / frontend service layer, (b) the
   redundant endpoint marked `deprecated: true` in the spec for one release, (c) removal in
   the following release once callers are migrated.

Rule: prefer **deprecate-then-remove** for anything a client could call; **delete outright**
only for verified dead spec.

---

## Phase 0 — Shared-kernel contract (do first; unblocks everything)

The shared contract is the foundation; fixing it makes every per-service fix a stub
reference instead of a bespoke schema.

- `[REMOVE]` `shared-kernel/.../utils/ErrorResponse.java` (the `traceId` duplicate). Migrate
  callers (`shared/utils/ErrorHandlingUtils.java`) to `dto.ErrorResponse`.
- `[KEEP-BUT-FIX]` Make `ch.batbern.shared.dto.ErrorResponse` the sole wire shape:
  `timestamp, path, status, error, errorCode, message, correlationId, severity, details`.
- `[KEEP-BUT-FIX]` Reconcile the `errorCode` **field** with the `ErrorCode` **enum**
  (`ERR_*`) — currently unrelated. Either type the field to the enum or document them as
  separate namespaces.
- `[KEEP-BUT-FIX]` Promote `ValidationError {field, message}` into shared-kernel (today only
  `auth-endpoints` defines it).
- `[MERGE]` Create `docs/api/_shared.openapi.yml` holding the single `ErrorResponse`,
  `PaginationMetadata`, `PaginatedResponse`, `ValidationError` — `$ref`/stub-referenced by
  every spec so the wire schema has one literal source, not 13 copies.
- `[KEEP-BUT-FIX]` Rewrite `docs/architecture/04-api-core.md` error + pagination sections to
  match reality (flat error, page-based pagination). It currently documents a
  nested-error/offset-pagination contract nothing implements — the single most misleading
  doc in the set.

**Risk:** low (internal types). **Client impact:** none.

---

## Phase 1 — Dead-spec removal (no runtime risk, big clarity win)

### EMS — kill the duplicate topic surface in events-api
The live `TopicController` serves `{topicCode}` (verified); the events-api `/topics/{id}`
UUID block is stale. `topics-api.openapi.yml` is canonical.

- `[REMOVE]` from events-api: `GET/POST /topics`, `GET /topics/{id}`,
  `GET /topics/{id}/similar`, `GET /topics/{id}/usage-history`,
  `PUT /topics/{id}/override-staleness`, `POST /topics/recalculate-staleness`,
  `POST /topics/calculate-similarities`, `POST /events/{eventCode}/topics` (inline body).
- `[KEEP-BUT-FIX]` `topics-api` becomes the single canonical topic spec. Standardize
  operationIds (`getTopicByCode`, `calculateSimilarities`). If override/recalculate
  staleness are still wanted, re-add them to **topics-api** as
  `POST /topics/{topicCode}/override-staleness` (action, not PUT) + `POST /topics/recalculate-staleness`.

### Partner — kill the orphaned voting subsystem in partners-api
Zero implementation exists (verified: no `voteValue` / `TopicVoteResponse` / `getPartnerVotes`
in code). The live voting API is the toggle in `partner-topics-api`.

- `[REMOVE]` `GET/POST /partners/{companyName}/votes`, `GET/POST /partners/{companyName}/suggestions`.
- `[REMOVE]` schemas `CastVoteRequest`, `TopicVoteResponse`, `SubmitSuggestionRequest`,
  `TopicSuggestionResponse`, `SuggestionStatus` and the `Topic Voting` tag.
- This also auto-resolves the duplicate `operationId: castVote` and the conflicting
  slug-vs-UUID `topicId` definitions.

**Risk:** low (verified dead). **Action:** confirm no frontend service-layer or Bruno test
references these paths before deleting.

---

## Phase 2 — Document the real surface (specs under-document live routes)

- `[KEEP-BUT-FIX]` Add to partner specs the **undocumented live endpoints**:
  `GET /partners/me`, `DELETE /partners/topics/{topicId}`,
  `GET /partner-meetings/{id}/rsvps` (+ note the `/internal/...rsvps` callback).
- `[KEEP-BUT-FIX]` Re-enable the **commented-out generators** in
  `services/speaker-coordination-service/build.gradle` and
  `services/attendee-experience-service/build.gradle` (2 commented blocks each), adding the
  shared `importMappings`/`schemaMappings` block. Until then, speakers/attendees contracts
  are unenforced.
- `[KEEP-BUT-FIX]` Add `importMappings`/`schemaMappings` to the partner-coordination client-
  DTO tasks (`generateCompanyClientDtos`, `generateUserClientDtos`).
- `[KEEP-BUT-FIX]` Decide owners + generator wiring for the 6 orphan specs (`auth-endpoints`,
  `file-upload-api`, `partner-analytics-api`, `partner-meetings-api`, `partner-notes-api`,
  `partner-topics-api`) — none feed any generator today.

**Risk:** low–medium (re-wiring may surface latent drift — that is the point).

---

## Phase 3 — Convention conformance (documentation-truth, no behaviour change)

### Reuse shared types instead of redefining
- `[KEEP-BUT-FIX]` Convert `companies-api`, `users-api`, `topics-api` `ErrorResponse` +
  `PaginationMetadata` from full inline bodies to the **stub** form (`x-java-type` +
  `x-java-type-import`, events-api style). They already codegen via name-matched
  `schemaMappings`; this removes silent doc-vs-codegen drift.
- `[RENAME]` Standardize the stub annotation to `x-java-type` everywhere; drop `x-java-class`
  (partners).
- `[RENAME]` `speakers-api`: rename `Pagination` → `PaginationMetadata`, add `hasNext`/`hasPrev`;
  replace its bespoke inline `ErrorResponse` with the stub.
- `[KEEP-BUT-FIX]` Give the no-error-schema specs (`attendees-api`, `partner-analytics`,
  `-meetings`, `-notes`, `-topics`) a referenced `ErrorResponse` on 4xx/5xx.

### One list-query vocabulary
- `[KEEP-BUT-FIX]` `users-api listUsers`: drop ad-hoc `role`/`company`/`sortBy`/`sortDir`;
  keep JSON:API `filter`/`sort`.
- `[KEEP-BUT-FIX]` `events-api GET /newsletter/subscribers`: replace ad-hoc
  `search`/`status`/`sortBy`/`sortDir` with `filter`/`sort`.
- `[KEEP-BUT-FIX]` Add `sort` to `events-api GET /events/{eventCode}/registrations`,
  `partners GET /partners`, and add pagination to `listPartnerMeetings`/`listPartnerNotes`/
  `listTopics` (or document them as bounded).

### get-or-create
- `[RENAME]` `companies-api`: `POST /companies:get-or-create` → `POST /companies/get-or-create`
  (slash), matching users. Mark the colon form `deprecated` for one release if any client
  uses it.

**Risk:** low (mostly spec text); the list-query changes are client-affecting → deprecate-then-remove.

---

## Phase 4 — Mutation-model fixes (genuine contract changes; deprecate-then-remove)

### CUMS — collapse the PUT/PATCH twins
- `[REMOVE]` (deprecate first) `PUT /companies/{name}` and `PUT /users/me` — they reuse the
  all-optional `UpdateXRequest` and are functionally identical to the PATCH. Keep PATCH as
  the single mutation. *Or*, if a true full-replace is wanted, give PUT a required-field
  `Replace<Entity>Request` per ADR-013 §1 — but only if the difference is real.
- `[KEEP-BUT-FIX]` Reconcile `PATCH /users/me` (broad `UpdateUserRequest`) vs
  `PATCH /users/{username}/profile` (narrow `PatchUserProfileRequest`): document the
  ownership boundary or unify the field set. Pick one patch philosophy.

### CUMS — remove the duplicate upload initiator
- `[REMOVE]` (deprecate first) `POST /users/me/picture` (`uploadProfilePicture`). Keep
  `POST /users/me/picture/presigned-url` (clearer name, supports SVG). Both currently
  "generate a presigned S3 upload URL."

### EMS — one event-lifecycle transition model
- `[MERGE]` `POST …/publish` + `POST …/workflow/advance` + `PUT …/workflow/transition` → one
  model. Keep `POST /events/{eventCode}/workflow/transition` (TransitionStateRequest) as the
  general transition (POST, matching the speaker confirm/decline action style); demote
  `publish`/`advance` to documented convenience shortcuts or remove.
- `[KEEP-BUT-FIX]` `PUT /events/{eventCode}/sessions/{sessionSlug}`: `UpdateSessionRequest`
  is byte-identical to create and mostly optional → either make it a true full-replace
  (require the full session shape) or demote to PATCH.
- `[KEEP-BUT-FIX]` Name the inline `object` request bodies (`assignSpeakerToSession`,
  `declineSpeaker`, `patchMyNewsletterSubscription`, `batchImportSessions`).
- `[MERGE]` Reconcile `UpdateEventSlotConfigurationRequest` ⟷ `UpdateEventAgendaConfigRequest`
  (overlapping slot vocabularies) into one schema or base+extension.

### EMS — registration cancellation (4 entry points)
- `[MERGE]` `POST /registrations/cancel` (email token) and `POST /registrations/deregister`
  (deregistration token) → one token-cancel endpoint (the likely-legacy one is
  `cancelRegistration`). Keep `DELETE …/my-registration` (authed) and
  `/registrations/deregister/by-email` (request-link) — distinct and justified.

### Partner — pick one deactivation path
- `[KEEP-BUT-FIX]` Deactivation exists twice: `isActive=false` in `PATCH /partners/{companyName}`
  AND `DELETE /partners/{companyName}` (soft-delete). Choose one (recommend DELETE = soft
  deactivate; remove `isActive` from the PATCH body, or vice-versa) and document it.

**Risk:** medium (live endpoints). **Process:** usage check → `deprecated: true` one release
→ remove. Regenerate frontend types (`npm run generate:api-types`) and update the service
layer after each spec change.

---

## Phase 5 — Partner spec consolidation (5 specs → 2)

The partner resource is fragmented: core CRUD + contacts live in `partners-api`, but
`notes` and `analytics` are literal `/partners/{companyName}/*` sub-resources living in
*separate* files. No single source of truth for "what can I do with a partner."

- `[MERGE]` Fold `partner-notes-api`, `partner-analytics-api`, `partner-topics-api` into
  `partners-api.openapi.yml` (all under the `/partners/*` tree). Wire the single spec to the
  generator — today only `partners-api` is generated; the others are hand-implemented and
  drift-prone.
- `[KEEP]` `partner-meetings-api` stays separate (top-level `/partner-meetings` aggregate),
  brought to parity (shared error schema, pagination, documented RSVP routes).
- `[KEEP]` `POST /attendees/topics` (suggestCommunityTopic) — relocate into the merged spec
  (served by partner-coordination-service); document the `/attendees` prefix as an
  intentional attendee-facing alias.
- `[RENAME]` Clarify `getPartnerStatistics` vs `analytics/dashboard` boundary (e.g.
  `partnership-summary` vs attendance `analytics`).
- `[KEEP-BUT-FIX]` Normalize tags (top-level `tags` in every spec; `Topic Voting` →
  `Partner Topics`) and servers blocks (relative `/api/v1` everywhere).

**Risk:** low (spec reorganization + generator wiring); behaviour unchanged.

---

## Phase 6 — events-api decomposition (one pass, strategy B) — ✅ DONE

> **Landed as described below, with two judgment-call deviations recorded during the carve:**
> 1. **teaser-images stayed in `events-core`** (not `event-media`). `Event` embeds `TeaserImageItem`,
>    and teaser DTOs have no shared-kernel class to "lift to `_shared`" — so the plan's own fallback
>    ("keep teaser in events-core to avoid the split") was taken. `event-media-api` = Event Photos only.
> 2. **`core → sessions` is a real cross-spec `$ref`** (Event embeds `List<Session>`). openapi-generator
>    7.2.0 emitted illegal `List<@Valid <fully.qualified.Name>>` for a `schemaMappings` cross-package type
>    (bug [#17647](https://github.com/OpenAPITools/openapi-generator/issues/17647)). Was worked around by
>    referencing Session/SessionSpeaker via `importMappings` (import + simple name) and deleting the dead
>    duplicate copy the core task then emitted, via a `doLast` on `openApiGenerate`. **⚠️ This workaround
>    was REMOVED in Phase 9a** (generator upgraded to 7.14.0, which fixes #17647 → emits the legal
>    `pkg.@Valid Type`); Session/SessionSpeaker are now plain `schemaMappings`. All other 7 specs depend
>    only on shared-kernel — a clean DAG.
>
> Two defined-but-unreachable schemas (`Speaker`, `RegistrationAdminResponse`) were initially preserved
> (pure-reorg caution), then **removed** as a follow-up once confirmed dead: `Speaker` duplicated User
> profile fields (firstName/lastName/company/profilePhoto) — an **ADR-004 violation** with no endpoint
> and no live consumer (its only FE alias `SpeakerUI` was itself unused; the real type is `SessionSpeaker`
> = enriched User + speakerRole/presentationTitle/isConfirmed). `RegistrationAdminResponse` had no path,
> no hand-written code reference (only a Javadoc comment), and no FE use. Both deletions verified: 122/122
> ops still intact, 0 dangling refs, backend compiles, FE type-check clean.


`events-api.openapi.yml` is a single flat **11,276-line file: 104 paths, 134 schemas, 17
tags, 0 external `$ref`s**, bundling ~9 unrelated sub-domains (newsletter, AI, watch,
email-templates, analytics have nothing to do with the event lifecycle). It mirrors the
EMS "ball-of-mud" — and is *behind* the code, which is already **43 domain controllers**.

**This decomposes the spec, NOT the service.** It is fully compatible with the hard
anti-goal in [`ems-modularization-extension-points.md`](./ems-modularization-extension-points.md)
("❌ do not split EMS into more microservices"): one deployable stays; the contract gains
vertical modules to match the controller layout. Precedent already exists — `topics-api` is
a separate per-domain spec generated by the same service (`openApiGenerateTopics`).

**Why a clean cut (measured):** of 132 path-reachable schemas, only **10 are shared across
more than one tag-cluster** — 2 are the shared-kernel types (handled in Phase 0) and the
other 8 (`Event`, `Session`, `SessionSpeaker`, `EventType`, `Venue`, `EventWorkflowState`,
2× teaser) live entirely within the core-event family. **~92% of schemas are single-domain.**

### Target spec set (carve in one PR)

Replace `events-api.openapi.yml` with the lean **`events-core-api`** + the siblings below.
Each becomes its own `docs/api/*.openapi.yml`, generator-wired like `topics-api`. Paths are
**preserved verbatim** — this is reorganization, so no client impact.

| New spec | Source tags / paths | ~ops |
|---|---|---|
| **events-core-api** | Events, Event Types, Bulk Operations, + lifecycle from Event Actions (`/publish`, `/workflow/*`, `/thanks*`) | ~17 |
| **event-sessions-api** | Sessions (`/sessions*`, `/agenda-config`, `/timetable`, slot, session-speakers confirm/decline) + QnA (`/sessions/{slug}/qna*` from Event Actions) | ~14 |
| **event-speakers-api** | Event-scoped speaker coordination: pool, `/self-nominate`, `/promote` (Event Actions) + Speaker Invitation + Speaker Outreach | ~9 |
| **event-registrations-api** | Registrations (registrations, participants + exports, deregister, distribution-list, batch) | ~15 |
| **event-newsletter-api** | Newsletter (incl `/registrant-notices/*`, `/slides-online/send`, subscriber lifecycle) | ~17 |
| **event-media-api** | Event Photos + teaser-images (`/teaser-images/*`, currently under Events tag) | ~8 |
| **event-ai-api** | AI Assist (`/ai/*`, `/analyze-abstract`) + AI Prompts (`/ai-prompts*`) | ~8 |
| **event-analytics-api** | Analytics (`/analytics/*`, `/attendance-summary`, `/{eventCode}/analytics`) | ~7 |
| **event-watch-api** | Watch (`/live-timing*`, `/watch/*`) | ~4 |
| **(topics-api)** | already separate — the dead `/topics/{id}` block (Phase 1) is **not** carried over | — |

### Placement judgment calls (decide during the carve)

- `[MERGE]` **Email Templates** (2 ops) — too small for its own spec. Fold into
  `event-newsletter-api` (templates power newsletters/notices) or a small `event-admin-api`.
- `[RENAME]` **teaser-images** shares the `TeaserImageItem`/`Event` schemas with core. Put it
  in `event-media-api` and lift the 2 shared teaser schemas to `_shared` (Phase 0), OR keep
  teaser in `events-core` to avoid the split. Recommend media + lift.
- **`/attendee-portal/dashboard`** and **`/public/settings/features`** are attendee/public
  reads served by EMS (`AttendeeDashboardController`) — flag for relocation to the
  attendee-experience domain rather than any event spec (boundary review).
- **Watch** — `watch-pairing` already lives in CUMS (`users-api`). Review whether
  `event-watch-api` belongs in EMS at all or should consolidate with CUMS watch (Phase 2
  re-wires the commented-out speaker/attendee generators — do this boundary review then).

### Generator wiring (mechanical, mirrors `openApiGenerateTopics`)

For each new spec, add an `openApiGenerate<Domain>` task in
`services/event-management-service/build.gradle` with:
- `apiPackage = "ch.batbern.events.<domain>.api.generated"`
- the shared `importMappings`/`typeMappings`/`schemaMappings` block (→ `_shared.openapi.yml`)
- `x-java-type` stub annotations (events-api style) for shared types

Then re-point each domain's controller(s) to the new generated `*Api` interface. Because the
code is **already split by domain** (43 controllers), this is largely a 1:1
`implements XxxApi` import swap — `SessionController` → `event-sessions-api`,
`NewsletterController` → `event-newsletter-api`, etc. Verify the build generates all specs
(`./gradlew :services:event-management-service:openApiGenerate*`) and the `*Api` interfaces
resolve before deleting `events-api.openapi.yml`.

**Do Phase 1 (dead-topic removal) and Phase 0 (shared `_shared.openapi.yml`) first** — the
carve then inherits a clean schema set and never copies the dead block forward.

**Risk:** low–medium. Paths unchanged → zero client impact; the risk is purely
build-time (generated package moves + controller re-points), caught by compilation.
Do it in **one PR** so the repo never sits in a half-carved state.

---

## Phase 7 — Contract-first completion (controllers → generated interfaces) — 🚧 IN PROGRESS

Surfaced during Phase 6 but **platform-wide, not EMS-only** (verified 2026-06-27). The single
most valuable follow-up, and the root cause of the Phase 4 PUT→POST drift that Phase 6 had to fix.

> **Progress (2026-06-27, branch `api-consolidation-phase7`, after Phase 9a):**
> - ✅ **Wired:** `PresentationSettingsController`→`PresentationSettingsApi` (`cbade6c4`),
>   `PublicUserController`→`PublicApi` (`707ecfdc`) — both with hand-written→generated DTO
>   consolidation; `PartnerContactController`→`PartnerContactsApi` (`2abe6b38`, no DTO change).
>   Each verified by its integration test + the owning service's full suite; PublicUser also via
>   Bruno `users-api`, PartnerContact via Bruno `partners-api`, PresentationSettings via Playwright
>   `presentation.spec.ts`.
> - ⏸️ **Deferred (documented):** Watch controllers (`WatchAuthController.pair` returns
>   `ResponseEntity<?>` + ad-hoc `{"message":...}` body → typed-contract clash affecting the external
>   watch app; record→class DTO migration; method renames) — needs an owner decision + watch-app
>   coordination. `PublicOrganizerController` + `LogoController` are **undocumented** (no spec
>   operation / generated interface) → require a spec addition first (Phase-2-style).
> - ✅ **Wired:** `CompanyController` → `CompaniesApi` + `CompanySearchApi` + `CompanyVerificationApi`
>   (3 interfaces, 9 ops). MERGED the former `CompanyGetOrCreateController` (the `/companies:get-or-create`
>   colon path combines correctly once the class prefix is `/api/v1`, not `/api/v1/companies`). Consolidated
>   all 9 hand-written DTOs → generated twins across `CompanyController` + `CompanyService`/`QueryService`/
>   `SearchService` + `UserService`; deleted the hand-written DTOs. `verified`→`isVerified` and
>   `Instant`→`OffsetDateTime` (mapper-boundary `.atOffset(UTC)`, entity/DB untouched) done; `CompanyLogo.url`
>   stays `URI` (no maxLength) via a guarded `toUri` mirroring `UserResponseMapper`. **5 spec/contract-drift
>   bugs surfaced by the wiring + fixed (all behaviour-preserving):** (1) `website` was `format:uri`+`maxLength`
>   → generated `@Size URI` → `UnexpectedTypeException` 500 on every @Valid body with a website; dropped
>   `format:uri` (string + @Size, mirrors users-api profilePictureUrl). (2) `name` `pattern:^[A-Za-z0-9]+$`
>   was never enforced by the hand-rolled controller (which allowed spaces, "Swisscom AG") → relaxed spec to
>   `@Size(2..255)` to match deployed behaviour. (3) `validate-uid` had `@Pattern` on the `uid` *query param*,
>   which would 400 every malformed UID before the handler → made `valid:false` unreachable (contradicting the
>   spec's own example); removed it (format check stays in `SwissUIDValidationService`). (4) generated DTOs
>   lacked the hand-written `@JsonInclude(NON_NULL)` → null fields leaked, breaking `?fields=`/`?include=`
>   sparse contracts; restored via generator `additionalModelTypeAnnotations`. (5) the interface's `@Min(1)`
>   on `page` now fires (method validation) → `ConstraintViolationException` was unmapped → 500; added a
>   `GlobalExceptionHandler` mapping it → 400. Full CUMS suite green (730 tests). `@WebMvcTest`
>   `CompanyControllerTest` removed — SB4 slice doesn't register interface-inherited `@RequestMapping`; its
>   unique validate-uid/verify coverage moved into `CompanyControllerIntegrationTest` (full context), matching
>   the PublicUser/PresentationSettings convention. **Validated live against local dev:** full CUMS
>   suite 730 green; full Bruno suite 14/14 green (incl. companies-api); manual smoke of list /
>   validate-uid (`INVALID-UID`→200 `valid:false`) / search all correct. Playwright @smoke gate (incl.
>   `company-creation.spec.ts`) deferred to CI/staging — local Playwright is blocked by a pre-existing
>   dev-native frontend-port quirk (Vite serves :3000, config/status expect :8100) + documented
>   local-dev divergence.
> - ✅ **Wired:** `UserController` → 7 generated interfaces (`UserManagementApi`, `UserAccountApi`,
>   `ProfilePictureApi`, `RoleManagementApi`, `UserSearchApi`, `DomainIntegrationApi`,
>   `GdprComplianceApi`) — 24 ops. Class `@RequestMapping` `/api/v1/users`→`/api/v1` (interface paths
>   carry `/users/…`). `provisionUser`→`provisionUserWithRole` (interface name); `searchUsers` now
>   returns the slim generated `UserSearchResponse` (matches the already-deployed FE
>   `userManagementApi.ts` type) via a controller-side projection. Consolidated the 4 hand-written
>   profile-picture DTOs: `ProfilePictureService` now returns the **generated** `PresignedUploadUrl`
>   (`uploadUrl`/`profilePictureUrl` → `URI`); deleted the 3 dead hand-written
>   `ProfilePictureUpload{Request,ConfirmRequest,ConfirmResponse}`. The hand-written `PresignedUploadUrl`
>   stays for the **logo** flow (`LogoController`/`GenericLogoService`, undocumented — deferred). **Five
>   endpoints with no `users-api` operation remain hand-rolled in the same class** (re-pathed to
>   `/users/…`): `GET /by-company`, `PUT /{username}`, `POST /{username}/profile-picture/upload-from-url`,
>   `POST /admin/reconcile`, `GET /admin/sync-status` → flagged for a spec addition (Phase-2-style).
>   `ActivityHistoryApi.getUserActivity` is a **dead spec op** (no impl anywhere in CUMS) → deliberately
>   NOT implemented (spec-cleanup follow-up). **3 spec/contract-drift fixes surfaced + applied
>   (behaviour-preserving):** (1) `ProfilePictureUploadRequest.mimeType` added optional — the FE sends
>   `file.type` but the spec omitted it (server derives type from extension regardless); (2) `fileSize`
>   `maximum: 5242880` dropped — generated `@Max` pre-empted the service's friendlier `exceeds 5MB limit`
>   400; the service stays the gate (the pre-wiring hand DTO had no max); (3) the `{username}` `@Pattern`
>   (already in the spec, now enforced via interface method-validation) turns malformed usernames into
>   400 not 404 — fixed one stale test that probed with the non-conforming `no.such.user`→`ghost.user`.
>   **🐛 Wiring gotcha (cost one full red run, 94→0):** keeping `@Valid` on the override params made
>   Spring read parameter annotations from the *implementation* method (only `@Valid`, no `@RequestBody`)
>   and STOP inheriting the interface's `@RequestBody` → request bodies never bound → 500 before the
>   handler on every POST/PATCH/PUT. Fix: **bare override params** (inherit `@Valid @RequestBody` from
>   the interface), exactly like `CompanyController`. **Validated live against local dev:** full CUMS
>   suite 730 green; Bruno `users-api` 35/35 green (list / get / provision additionalProperties-guard /
>   issue-credentials / patch-profile / delete / public projection); FE type-check + `userManagementApi`
>   + `userAccountApi` (54) green. **Playwright 35/35 green** (organizer project, web on :8100) across
>   `workflows/user-management/*` (create/delete/list+search/roles), `user-account/*`
>   (profile/photo-upload/settings/additional-emails), `organizer/profile-page`, and
>   `workflows/user-sync/reconciliation-drift-fix` (the hand-rolled `/admin/reconcile` +
>   `/admin/sync-status`) — the deferred UI gate is now satisfied.
> - ✅ **CUMS contract-first DONE** (audit correction): all **6 documented** production controllers now
>   `implements` their generated `*Api` — Company, PublicUser, PresentationSettings, User, **and
>   UserPreferences + UserSettings (which were already wired** — the earlier "CUMS 2/13" / "4/13" counts
>   undercounted these two). Remaining CUMS controllers are out of contract-first scope: WatchAuth/WatchPairing
>   (deferred — external watch-app contract), Logo/PublicOrganizer (undocumented → spec-first, separate task),
>   DevEmail/TestFixtureCleanup (dev/test-only, not production API).
> - 🧭 **EMS strategy decided (owner, 2026-06-27): RE-TAG specs per controller** (not merge controllers).
>   EMS controllers are **finer-grained than the Phase-6 spec tags**, so one generated `*Api` aggregates ops
>   across many controllers (`EventActionsApi` = 8 ops / 4 controllers; `SessionsApi` = 14 ops / 5 controllers;
>   `AnalyticsApi` = 7 ops / ~3). The plan's earlier "EMS is a 1:1 `implements SessionsApi` swap" premise was
>   **wrong** (the 4 pre-wired EMS controllers only worked because their tags were single-controller). Fix:
>   split each controller's op-group into its own tag → regenerate → 1:1 interface → wire incrementally
>   (spec-only; no controller merges; also satisfies Phase-9 tag hygiene).
> - ✅ **Wired (first EMS via re-tag):** `EventWorkflowController` → `EventWorkflowApi` (2 ops) — the very
>   controller whose PUT→POST drift motivated Phase 7. Split the 2 workflow ops out of the `Event Actions`
>   grab-bag into a dedicated `Event Workflow` tag (publish/thanks/dashboard stay under `Event Actions` until
>   their controllers are wired). Class `@RequestMapping` `/api/v1/events`→`/api/v1`. Named the inline
>   transition 200 → `WorkflowTransitionResponse` (Phase-4 inline-body precedent). Consolidated the 2 hand-written
>   DTOs (`TransitionStateRequest`, `WorkflowStatusDto`) → generated; deleted the hand-written twins.
>   **Drift fix:** the spec's `EventWorkflowState` enum is **stale/incomplete** (8 values; the domain enum has
>   ~20, and the spec's own `WorkflowStatusDto` example uses `SPEAKER_OUTREACH`, which the enum lacks) — so the
>   generated typed enum would be a regression. Typed the workflow state fields as `string` in
>   `WorkflowStatusDto`/`TransitionStateRequest`/`WorkflowTransitionResponse`, matching the deployed String DTOs
>   (state validity is enforced by the workflow state machine, not the wire enum). **Drift fix:** the eventCode
>   `@Pattern ^BATbern[0-9]+$` (pre-existing in the spec, now enforced via the interface) rejected the stale test
>   fixture `BAT-2024-Q4` → updated to `BATbern142` (real codes are all `BATbern{n}`); same for the not-found
>   probe `NON-EXISTENT`→`BATbern999`. **Verified:** full EMS suite green (10m, Testcontainers);
>   `EventWorkflowControllerIntegrationTest` 12/12.
> - ✅ **Wired (2nd EMS via re-tag):** `AnalyticsController` → `AnalyticsApi` (event-analytics spec, 5 ops:
>   getAnalyticsOverview/Attendance/Topics/Companies + getCompanyDistribution). The `Analytics` tag was
>   shared with EventController's 2 event-scoped ops (`getAttendanceSummary`, `getEventAnalytics`); split
>   those out into a new **Event Reporting** tag (→ unimplemented `EventReportingApi`, wired when EventController
>   is) so `AnalyticsApi` == AnalyticsController exactly. Class `@RequestMapping` `/api/v1/analytics`→`/api/v1`
>   (interface carries `/analytics/...`); controller methods renamed to the operationIds; override params made
>   **bare** (inherit the interface's `@RequestParam`/`@Valid`/`@Min`/`@NotNull` — declaring them on the impl
>   would stop Spring inheriting the interface binding annotations, the UserController gotcha). **No DTO
>   consolidation** — the controller already consumed the generated `analytics.dto.generated.*` DTOs.
>   New spec-enforced validation now fires via method-validation: `fromYear` `@Min(2000)` + `eventCode`
>   `@NotNull`/`required=true` → `ConstraintViolationException`, already mapped → 400 by the EMS
>   `GlobalExceptionHandler` (no real input <2000, no test breakage). **Verified:** EMS main+test compile;
>   `AnalyticsControllerIntegrationTest` 18/18 green (Testcontainers), incl. the unauthenticated-403 and
>   per-event-distribution cases. FE unaffected (re-tag doesn't change paths/schemas; `openapi-typescript`
>   generates by path).
> - ✅ **Wired (3rd EMS via re-tag):** `DeregistrationController` → `DeregistrationApi` (event-registrations
>   spec, 3 public ops: verifyDeregistrationToken / deregisterByToken / requestDeregistrationByEmail). Split
>   the 3 `/registrations/deregister/*` ops out of the shared `Registrations` tag into a new **Deregistration**
>   tag (the rest stay `Registrations` → still the unimplemented `RegistrationsApi`). Class `@RequestMapping`
>   `/api/v1/registrations/deregister`→`/api/v1`; methods renamed to operationIds (verifyToken→verifyDeregistrationToken,
>   deregisterByEmail→requestDeregistrationByEmail); bare override params. No DTO consolidation (already used
>   `registrations.dto.generated.*`). **🐛 Two contract-drift fixes on the verify `token` query param, both
>   required to preserve the deployed 2026-06-16 truncated-link fix** (a mail client truncating the link at
>   `?token=` must yield 404, never 400/500): (1) spec had `required: true` → generated `@RequestParam(required=true)`
>   would throw `MissingServletRequestParameterException`→500 on an absent token; changed to `required: false`.
>   (2) spec had `format: uuid` → generated a `UUID` param → a malformed value fails Spring conversion
>   (`MethodArgumentTypeMismatchException`, not 404); dropped to plain `string` so the controller's defensive
>   `parseTokenOrNotFound` (absent/blank/non-UUID → `NoSuchElementException` → 404) stays the gate. Spec
>   description updated to document the defensive contract. **Verified:** EMS main+test compile;
>   `DeregistrationControllerIntegrationTest` 13/13 green (Testcontainers) incl. the NO-token / empty-token /
>   malformed-token → 404 regression cases + anti-enumeration + waitlist-promotion. **FE types regenerated**
>   (`event-registrations`: token→optional; also picked up pre-existing `events-core` drift — commit `081d43fd`
>   changed that spec but never regenerated FE types); FE type-check green.
> - ✅ **Wired (4th EMS via re-tag):** `EventTeaserImageController` → `TeaserImagesApi` (events-core spec, 5 ops:
>   listTeaserImages / generateTeaserImageUploadUrl / confirmTeaserImageUpload / updateTeaserImage /
>   deleteTeaserImage). The 4 documented teaser ops were under the catch-all `Events` tag; split all into a new
>   **Teaser Images** tag → `TeaserImagesApi` == the controller. **🐛 Spec made truthful:** the live
>   `GET /events/{eventCode}/teaser-images` (listImages) was **undocumented** (UserController pattern) → added
>   it to events-core (op `listTeaserImages`, returns `array` of `TeaserImageItem`) so all 5 wire to one
>   interface. Methods renamed to operationIds; bare override params; method-level `@PreAuthorize`/`@CacheEvict`
>   kept on the overrides (orthogonal to mapping). **No `@Pattern` on the teaser `eventCode`** (deliberate — the
>   controller accepts the sentinel `_global`); the new list op also omits it. No DTO consolidation (already used
>   `core.dto.generated.*`); `imageId` path param is `format: uuid` → generated `UUID`, matching the controller.
>   **Note:** the core generator is the plugin's default `openApiGenerate` task (not `openApiGenerateCore`).
>   **Verified:** EMS main+test compile; `EventTeaserImageControllerIntegrationTest` 12/12 green (Testcontainers);
>   FE types regenerated (events-core gains `listTeaserImages`) + type-check green.
> - ✅ **Wired (5th EMS):** `AiAssistController` → `AiAssistApi` (event-ai spec, 5 ops: getFeatureFlags /
>   generateEventDescription / generateThemeImage / applyThemeImage / analyzeAbstract). **No re-tag needed** —
>   event-ai already has `AI Assist` as a controller-specific tag (`AI Prompts` is separate, already wired to
>   AiPromptController). `generateDescription`→operationId `generateEventDescription`; bare override params;
>   method `@PreAuthorize`/`@CacheEvict` kept. **DTO consolidation:** deleted the nested hand-written record
>   `ApplyThemeImageRequest`, threaded the generated `ai.dto.generated.ApplyThemeImageRequest` (`getImageUrl()`)
>   through controller + the LLM08 security test. No spec change (interface + DTO already existed) → no FE regen.
>   `/public/settings/features` stays here for now (Phase 8 flags it for relocation off the AI controller — wiring
>   it to AiAssistApi today doesn't block that move). **Verified (full live cycle):** EMS compile;
>   `AiAssistControllerIntegrationTest`+`AiAssistControllerSecurityTest` 14/14 green; EMS restarted + live gateway
>   smoke — public feature-flags 200, ai/description no-auth 401, theme-image/apply bad+lookalike URL 400 (LLM08
>   guard intact, body bound to generated DTO); no real LLM calls.
> - ✅ **Wired (6th EMS, first with real DTO consolidation):** `AgendaConfigController` → `AgendaConfigApi`
>   (event-sessions spec, 2 ops: getEventAgendaConfig / updateEventAgendaConfig). Split the 2 agenda-config ops
>   out of the broad `Sessions` tag into a new **Agenda Config** tag. Methods renamed to operationIds; bare params;
>   `@PreAuthorize` kept. **PUT stays PUT** (legit full-replace: all knobs required, copy-on-edit upsert — not a
>   Phase-4 PATCH demotion). **DTO consolidation (the real work):** deleted hand-written records
>   `dto.EventAgendaConfigResponse` + `dto.UpdateEventAgendaConfigRequest`; threaded the generated
>   `sessions.dto.generated` twins through `AgendaConfigService`. Boundary mapping: `source` String →
>   `AgendaConfigSource` enum; `aperitifPosition` String ↔ inner `AperitifPositionEnum` (`.getValue()` → DB String
>   column, `.fromValue()` back); `typicalStart/EndTime` stay String (generated as String, NOT LocalTime — one
>   less mapping than feared); record accessors → generated getters throughout. **Wire values unchanged**
>   (`@JsonValue`) — verified live: GET returns `source=TEMPLATE`, `aperitifPosition=end` exactly as before.
>   **Verified (full live cycle):** `AgendaConfigControllerIntegrationTest` 10/10 green; EMS restarted + gateway
>   smoke (GET auth 200 w/ correct enum serialization, GET/PUT no-auth 401); no spec schema change → no FE regen.
> - ✅ **Wired (7th EMS, heterogeneous returns + DTO consolidation):** `ParticipantsController` → `ParticipantsApi`
>   (event-registrations spec, 4 ops: getEventDistributionList / addParticipant / exportParticipantsXlsx /
>   exportParticipantsDocx). Split the 4 ops out of the broad `Registrations` tag into a new **Participants** tag.
>   Methods renamed to operationIds; bare params; `@PreAuthorize` kept (none on the anonymous-reachable
>   distribution-list route used by the in-VPC forwarder Lambda). **DTO consolidation:** `Map<String,Object>` →
>   generated `DistributionListResponse` (Set→List, `kind` String → inner `KindEnum`) + `AddParticipant201Response`;
>   deleted hand-written `dto.AddParticipantRequest`, threaded the generated twin (nullable `Boolean` force/notify
>   null-guarded to preserve spec defaults false/true). **`byte[]` exports → `ResponseEntity<Resource>`**
>   (`ByteArrayResource`) keeping the Content-Disposition/Content-Length headers. **🐛 Drift fix:**
>   `DistributionListResponse.kind` enum was missing `participants` (had only speakers/moderator) though it's a
>   valid path-param kind the controller resolves → added it (FE `kind` union gains `participants`). eventCode
>   `@Pattern` now enforced via the interface. **Verified (full live cycle):** `ParticipantsControllerIntegrationTest`
>   19/19 green; EMS restarted + gateway smoke — distribution-list participants/speakers 200 (kind=participants now
>   serializes), bogus kind 404, export.xlsx 200 valid ZIP (504b0304) via Resource, POST no-auth 401; FE type-check
>   green. ⏳ **Next EMS:** continue per-controller wire (e.g. Sessions, Newsletter, GlobalSession — the last needs
>   a spec addition since `/sessions` search is undocumented).
> - ✅ **Fixed 2026-06-27 (re-diagnosed):** the `users-api` 15/19/20 failures were NOT a missing
>   role predicate — the singular `role` filter works (see §Phase 3 note + passing
>   `UserControllerIntegrationTest.should_filterByRole_when_roleFilterProvided`). The tests still sent
>   the removed top-level `?role=` param; migrated them to `?filter={"role":…}`. Same stale-contract
>   fix also corrected `sessions-api 10` (`PUT`→`PATCH`, per Phase 4). Test-only; no code change.

**Problem:** most controllers only *consume* the generated DTOs — they hand-roll
`@RequestMapping`/`@PostMapping`/`@GetMapping` instead of `implements`-ing the generated `*Api`
interface. So the OpenAPI spec is **not an enforced contract**: a controller can map a different
verb/path than the spec declares and nothing fails to compile (exactly how `EventWorkflowController`
PUT→POST silently diverged from `EventWorkflowControllerIntegrationTest`). ADR-006's intent is
controller-implements-generated-interface. Audit of `implements <X>Api` coverage:

| Service | Controllers implementing generated `*Api` | Notes |
|---|---|---|
| **EMS** | 4 / ~45 | EventTypes, SpeakerOutreach, AiPrompts, EmailTemplates only |
| **CUMS** | 2 / 13 | the worst ratio — **not** "mostly wired" (earlier assumption was wrong) |
| **Partner** | 5 / 10 | Phase 5 wired the notes/topics/analytics controllers; ~half remain |
| Speaker / Attendee | 0 / 0 | dormant; generators off per ADR-014 — out of scope until reactivated |

- `[KEEP-BUT-FIX]` **EMS** — wire each controller to `implements <Domain>Api` (the 9 per-domain
  interface sets now exist post-Phase 6 → largely a 1:1 import + `@Override` swap, e.g.
  `SessionController implements` event-sessions `SessionsApi`). The generated interface carries the
  mapping annotations, so the controller drops its own `@RequestMapping`s.
- `[KEEP-BUT-FIX]` **CUMS** — 11 of 13 controllers unwired (`CompanyController`, `UserController`,
  etc. hand-roll mappings against `companies-api`/`users-api`). Same fix; this is where the gap is
  widest. `users-api` is also the next-largest spec (~94 KB) — a candidate for its own decomposition
  if it keeps growing.
- `[KEEP-BUT-FIX]` **Partner** — finish the remaining ~5 controllers against `partners-api`/
  `partner-meetings-api` (Phase 5 did notes/topics/analytics).
- `[KEEP-BUT-FIX]` **Consolidate hand-written DTOs that shadow generated schemas.** Verified in EMS
  (`AttendeeDashboardResponse`, `SessionImportDetail`, `SpeakerPoolResponse`, `SpeakerContentInfo`, …
  live in `ch.batbern.events.dto` next to a generated twin; the generated one is emitted but unused).
  **Not yet audited in CUMS/partner — audit them too** as part of wiring each. Thread the generated
  DTO through and delete the hand-written twin (the deleted `Speaker` schema was the same disease).

**Risk:** medium — behaviour-sensitive (verb/path/response shape must match the spec exactly;
mismatches surface as 404/405/400). Do per-controller, run that service's integration suite each
time (the Phase 6 pre-push hook fix now runs them). NOT a single big-bang PR — slice by service,
then by domain within the service. Suggested order: CUMS (widest gap) → EMS → finish Partner.

---

## Phase 8 — Domain-boundary corrections (endpoint relocation + path normalization) — ⏳ TODO

Behaviour/contract-affecting, so deliberately deferred from the Phase 6 reorg.

- `[RENAME]` **Relocate misfiled endpoints** (Phase 6 homed them pragmatically):
  - `GET /public/settings/features` (feature flags) is served by `AiAssistController` — flags are
    not AI. Move to a public/app-settings controller + its own spec section.
  - `GET /attendee-portal/dashboard` is attendee-domain logic in EMS. Plan + ADR-014 flag it for
    relocation to attendee-experience; revisit when/if that service is reactivated.
- `[KEEP-BUT-FIX]` **Normalize the Watch paths.** `event-watch-api` paths hard-code the
  `/api/v1/` prefix (`/api/v1/events/{eventCode}/live-timing`, `/api/v1/watch/...`) while every
  other spec relies on the server base path. Strip the literal prefix so the watch contract matches
  the rest. ⚠️ This is a real URL change for the BATbern-watch app client — coordinate + version it
  (and update `apps/BATbern-watch`).

**Risk:** medium — client-affecting (watch app, feature-flag callers). Usage-check → migrate
callers in the same change.

---

## Phase 9 — Tooling & spec hygiene — ⏳ TODO

Low-risk cleanup; no contract change.

- ✅ **DONE (Phase 9a, branch `api-consolidation-phase7`)** — **Upgraded openapi-generator
  7.2.0 → 7.14.0.** The cross-package `@Valid` bug
  ([#17647](https://github.com/OpenAPITools/openapi-generator/issues/17647)) **is fixed** in
  7.14.0: it now emits the legal `pkg.@Valid Type`, so the `core→sessions` workaround
  (`importMappings` + the `doLast` deleting the dead duplicate `Session`/`SessionSpeaker`) was
  **removed** — `Session`/`SessionSpeaker` are back on plain `schemaMappings`, no duplicate, no
  `doLast`. Spike outcome (regression-tested before adopting): file set identical, whole Java
  monorepo compiles main+test, full suites green. Migration churn was tiny — (a) optional
  query/body params changed from `Optional<X>` to `@Nullable X` (one wired controller,
  `EmailTemplateController`, adapted); (b) generator no longer strips a common enum-value prefix
  (`WELCOME`→`AFTER_WELCOME`, `JPEG`→`IMAGE_JPEG`) — **wire values unchanged**, only the Java
  constant identifier (4 refs in `EventTeaserImageServiceTest` adapted); (c) the `java`/`native`
  models-only client generator now injects an unsatisfied `ApiClient` import, so partner's
  `generateCompanyClientDtos`/`generateUserClientDtos` were switched to the `spring` generator
  (consistent with EMS's `openApiGenerateUsersClient`); and (d) 7.14 default-initialises collection
  fields to empty (was null), which made unset lists serialise as `[]` instead of absent and broke the
  `?include=` sparse-fieldset contract — restored to pre-7.14 behaviour by adding
  `containerDefaultToNull: 'true'` to **every** generator `configOptions` block (CUMS ×2, EMS ×11,
  partner ×3). Everything else additive (per-DTO inner `Builder`, `@Nullable` field annotations). Frontend types
  are generated by the separate `openapi-typescript` npm tool and are **not affected** by this
  Gradle-plugin bump. Done **before Phase 7** so the ~50 controllers wire against the final 7.14.0
  interface shape exactly once.
- `[KEEP-BUT-FIX]` **Declare top-level `tags`** in every spec for the tags used on operations
  (`event-media-api` currently declares none; `Event Photos`/`Speaker Invitation`/`Organizer` are
  used but undeclared). Fold the single-op `Organizer` tag into a neighbour.
- `[KEEP-BUT-FIX]` Fix stale Javadoc in `web-frontend/src/services/workflowService.ts` (comments
  still say "PUT …/workflow/transition" after the POST change).

**Risk:** low.

---

## Patterns to standardize on (the org reference, by example)

These already exist in the codebase and become the canonical examples in ADR-013:

1. **PUT-full-replace vs PATCH-partial** — `events-api` `/events/{eventCode}`
   (`UpdateEventRequest` 9-required vs `PatchEventRequest` 0-required, controller enforces it).
2. **POST-create + PATCH-update split** — `events-api` speaker pool.
3. **Action sub-resources for state changes** — EMS speaker `…/confirm`/`…/decline`/`…/promote`;
   CUMS `…/verify`/`…/roles`.
4. **Idempotent toggle** — `partner-topics-api` `POST`/`DELETE /partners/topics/{id}/vote`.
5. **PATCH-only, no PUT** — the entire partner domain (never shipped PUT/PATCH twins).
6. **Single `ErrorResponse` for 100% of error responses** — `events-api`/`topics-api`.
7. **POST-create + explicit `select`/`assign` instead of get-or-create** — EMS topic selection.

---

## Suggested execution order

| Phase | Risk | Client impact | Gate |
|---|---|---|---|
| 0 Shared-kernel | low | none | unit tests green; both ErrorResponse callers migrated |
| 1 Dead-spec removal | low | none (verified dead) | grep frontend + Bruno for the paths; confirm server 404s |
| 2 Document live routes | low–med | none | generators re-enabled, build green |
| 3 Convention conformance | low | low (list query) | codegen unchanged; deprecate list-vocab |
| 4 Mutation-model fixes | med | yes | deprecate-then-remove; regen FE types + service layer |
| 5 Partner consolidation | low | none | merged spec generates; controllers unchanged |
| 6 events-api decomposition | low–med | none (paths preserved) | all per-domain specs generate; controllers re-point; build green; one PR |
| 7 Contract-first completion | med | none (verb/path must match spec) | per-domain slices; each service's integration suite green |
| 8 Domain-boundary corrections | med | yes (watch app, flag callers) | usage-check + migrate callers; version watch path change |
| 9 Tooling & spec hygiene | low | none | generator upgrade regression-tested; build green |

Land 0→1→2→3 as the low-risk consolidation pass; schedule 4 and 5 as follow-ups with the
deprecate-then-remove cycle so no live client breaks. Phase 6 (events-api decomposition)
depends on 0 + 1 (clean shared schemas, dead block gone) and is done as a single PR.

Phases 7–9 were surfaced *during* Phase 6 and deliberately left out of the pure-reorg PR.
Recommended order: **7 first** (contract-first completion — highest value, closes the
drift class that bit Phase 4), then 8 (boundary corrections, client-coordinated), then 9
(tooling/hygiene, anytime). 7 is enabled by 6 (the per-domain `*Api` interfaces now exist).

> **EMS note:** this plan is the input for applying ADR-013 to EMS. The EMS-specific work
> (Phase 1 dead-topic removal, Phase 4 EMS mutation fixes, Phase 6 spec decomposition) is
> the API-contract complement to the internal-module work in
> `ems-modularization-extension-points.md` — the spec carve (Phase 6) and the controller
> vertical modules should land on the same domain boundaries.
