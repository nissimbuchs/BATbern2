# Story 10.22: Event Teaser Images on Moderator Presentation Page

Status: review

## Story

As an **organizer**,
I want to upload one or more teaser images for an event from the event detail page,
so that each image appears as its own full-screen slide on the moderator presentation screen — after the topic reveal and before the agenda — giving the audience a visual mood-setter sequence.

## Acceptance Criteria

1. **Migration** runs cleanly; a new `event_teaser_images` table is created; existing events are unaffected (they simply have no rows in the new table)
2. Organizer can upload multiple teaser images via presigned URL from `EventSettingsTab`; a thumbnail gallery of all uploaded images is shown in the Settings tab after each confirmation
3. Organizer can remove any individual teaser image by its ID; the DB row is deleted and the S3 object is removed
4. Presentation page shows **one full-screen slide per teaser image** (ordered by `displayOrder`), inserted consecutively between the topic-reveal slide and the agenda-preview slide, when `event.teaserImages` is non-empty
5. When `event.teaserImages` is empty (or absent), the presentation page renders identically to pre-story behaviour — no empty/blank slides inserted
6. **Max 10 images per event**: uploading beyond 10 returns 422 from the confirm endpoint; the UI shows an inline message "Maximum of 10 teaser images reached" and the Upload button is disabled when the limit is reached
7. If upload or removal fails (S3 error, network timeout), an inline error message is displayed in the Settings tab; the gallery state is unchanged
8. TDD: `EventTeaserImageServiceTest` covers upload-URL generation, confirm upload, delete individual image, and max-limit enforcement; integration test verifies DB state
9. OpenAPI spec committed before implementation (ADR-006)
10. i18n: `teaserImage.*` keys in `de/events.json` and `en/events.json`; Type-check passes; Checkstyle passes

## Tasks / Subtasks

### Phase 1: OpenAPI spec (ADR-006 — commit before any implementation code)
- [x] Add to `docs/api/events-api.openapi.yml`:
  - [x] New schema `TeaserImageItem { id (string/uuid), imageUrl (string/uri), displayOrder (integer) }`
  - [x] `teaserImages` field (array of `TeaserImageItem`, nullable) to `EventResponse` schema (after `themeImageUrl`)
  - [x] `POST /events/{eventCode}/teaser-images/upload-url` endpoint → `TeaserImageUploadUrlResponse { uploadUrl, s3Key, expiresIn }`
  - [x] `POST /events/{eventCode}/teaser-images/confirm` endpoint → body `TeaserImageConfirmRequest { s3Key }` → returns `TeaserImageItem { id, imageUrl, displayOrder }`
  - [x] `DELETE /events/{eventCode}/teaser-images/{imageId}` endpoint → 204 No Content
  - [x] Note: no reorder endpoint in this story — `displayOrder` is set by insertion order (auto-increment)
- [x] Regenerate backend types: `./gradlew :services:event-management-service:openApiGenerateEvents`
- [x] Regenerate frontend types: `cd web-frontend && npm run generate:api-types`

### Phase 2: Backend — TDD RED (write failing tests first)
- [x] Write `EventTeaserImageServiceTest.java` (unit) — RED phase:
  - [x] `generateUploadUrl_whenValidInput_returnsPresignedUrl`: verifies S3Presigner called, s3Key follows `events/{eventCode}/teaser/{uuid}.{ext}` pattern
  - [x] `confirmUpload_whenS3ObjectExists_persistsImageAndReturnsItem`: verifies HeadObject called, `EventTeaserImage` saved with correct eventCode, s3Key, imageUrl, displayOrder = previous max + 1
  - [x] `confirmUpload_whenS3ObjectMissing_throws`: verifies appropriate exception thrown
  - [x] `confirmUpload_whenLimitReached_throws`: verifies `TeaserImageLimitExceededException` (→ 422) when event already has 10 images
  - [x] `deleteTeaserImage_whenImageExists_deletesS3AndRow`: verifies DeleteObject called with correct key, repository row deleted
  - [x] `deleteTeaserImage_whenImageNotFound_throws`: verifies `TeaserImageNotFoundException` when imageId not found
- [x] Write `EventTeaserImageControllerIntegrationTest.java` (integration, extends `AbstractIntegrationTest`) — RED phase:
  - [x] `POST .../upload-url` returns 200 with `uploadUrl` + `s3Key` (ORGANIZER auth)
  - [x] `POST .../upload-url` returns 403 for non-ORGANIZER
  - [x] `POST .../confirm` with valid s3Key returns 200 with `TeaserImageItem`; row exists in `event_teaser_images` with correct columns
  - [x] `DELETE .../teaser-images/{imageId}` returns 204; row is gone from DB
  - [x] `GET /events/{eventCode}` (existing endpoint) returns `teaserImages` array containing previously confirmed images, in displayOrder

### Phase 3: Backend — Flyway migration
- [x] Run `ls services/event-management-service/src/main/resources/db/migration/V*.sql | sort -V | tail -5` to confirm next version number
- [x] Create migration file at the next available version (e.g. `V79__create_event_teaser_images.sql` if V79 is free):
  ```sql
  -- Story 10.22: Event Teaser Images for Moderator Presentation Page
  CREATE TABLE event_teaser_images (
      id            UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
      event_code    VARCHAR(50) NOT NULL REFERENCES events(event_code) ON DELETE CASCADE,
      s3_key        TEXT        NOT NULL,
      image_url     TEXT        NOT NULL,
      display_order INTEGER     NOT NULL DEFAULT 0,
      created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
  );

  CREATE INDEX idx_event_teaser_images_event_code
      ON event_teaser_images (event_code, display_order);
  ```
  - ⚠️ **Version check is mandatory**: Story 10.21 (event-photos-gallery) targets V78/V79. Always confirm the actual latest migration before writing this file.

### Phase 4: Backend — Domain & repository (GREEN phase)
- [x] Create `EventTeaserImage.java` in `.../domain/`:
  ```java
  // Story 10.22: Teaser image for moderator presentation page
  @Entity
  @Table(name = "event_teaser_images")
  @Getter @Setter @NoArgsConstructor
  public class EventTeaserImage {
      @Id
      @GeneratedValue(strategy = GenerationType.UUID)
      private UUID id;

      @Column(name = "event_code", nullable = false)
      private String eventCode;

      @Column(name = "s3_key", nullable = false, columnDefinition = "TEXT")
      private String s3Key;

      @Column(name = "image_url", nullable = false, columnDefinition = "TEXT")
      private String imageUrl;

      @Column(name = "display_order", nullable = false)
      private int displayOrder;

      @Column(name = "created_at", nullable = false, updatable = false)
      private OffsetDateTime createdAt = OffsetDateTime.now();
  }
  ```
- [x] Create `EventTeaserImageRepository.java` in `.../repository/`:
  ```java
  public interface EventTeaserImageRepository extends JpaRepository<EventTeaserImage, UUID> {
      List<EventTeaserImage> findByEventCodeOrderByDisplayOrderAsc(String eventCode);
      Optional<Integer> findMaxDisplayOrderByEventCode(String eventCode);
      // hint: use @Query("SELECT MAX(e.displayOrder) FROM EventTeaserImage e WHERE e.eventCode = :eventCode")
  }
  ```
- [x] Update `Event.java` to include a `@OneToMany` mapping (or just expose via service — prefer service-level join to avoid N+1):
  - **Preferred approach**: Do NOT add `@OneToMany` to `Event` entity; instead, in `EventService.toDto()`, call `eventTeaserImageRepository.findByEventCodeOrderByDisplayOrderAsc(eventCode)` and map to the DTO list. This avoids lazy-loading issues.

### Phase 5: Backend — Service layer (GREEN phase)
- [x] Create `EventTeaserImageService.java` in `.../service/`:
  - `generateUploadUrl(String eventCode, String contentType, String fileName)` → `TeaserImageUploadUrlResponse`
  - `confirmUpload(String eventCode, String s3Key)` → `TeaserImageResponseDto` (id, imageUrl, displayOrder)
  - `deleteTeaserImage(String eventCode, UUID imageId)` → `void`
  - Pattern: follow `SpeakerProfilePhotoService` (use `S3Presigner` + `S3Client`; **not** `RestClient`)
  - Inject `@Value("${aws.s3.bucket-name:batbern-development-company-logos}")` and `@Value("${aws.cloudfront.domain:https://cdn.batbern.ch}")`
  - S3 key pattern: `events/{eventCode}/teaser/{uuid}.{ext}`
  - `displayOrder` on confirm: `repository.findMaxDisplayOrderByEventCode(eventCode).orElse(-1) + 1`
  - **Max-image guard** on confirm (before S3 HeadObject):
    ```java
    private static final int MAX_TEASER_IMAGES = 10;
    if (repository.countByEventCode(eventCode) >= MAX_TEASER_IMAGES) {
        throw new TeaserImageLimitExceededException(eventCode, MAX_TEASER_IMAGES);
    }
    ```
  - Add `countByEventCode(String eventCode)` to `EventTeaserImageRepository`
  - Add `TeaserImageLimitExceededException` (→ `GlobalExceptionHandler` maps to 422 Unprocessable Entity)
  - Allowed content types: `image/jpeg`, `image/png`, `image/webp`
  - Max file size hint: 10 MB
  - Expiry: 900s (15 min)

### Phase 6: Backend — Controller (GREEN phase)
- [x] Create `EventTeaserImageController.java` in `.../controller/`:
  ```java
  @RestController
  @RequestMapping("/api/v1")
  @RequiredArgsConstructor
  public class EventTeaserImageController {

      private final EventTeaserImageService teaserImageService;

      @PostMapping("/events/{eventCode}/teaser-images/upload-url")
      @PreAuthorize("hasRole('ORGANIZER')")
      public ResponseEntity<TeaserImageUploadUrlResponse> generateUploadUrl(
              @PathVariable String eventCode,
              @RequestBody TeaserImageUploadUrlRequest request) { ... }

      @PostMapping("/events/{eventCode}/teaser-images/confirm")
      @PreAuthorize("hasRole('ORGANIZER')")
      public ResponseEntity<TeaserImageItem> confirmUpload(
              @PathVariable String eventCode,
              @RequestBody TeaserImageConfirmRequest request) { ... }

      @DeleteMapping("/events/{eventCode}/teaser-images/{imageId}")
      @PreAuthorize("hasRole('ORGANIZER')")
      public ResponseEntity<Void> deleteTeaserImage(
              @PathVariable String eventCode,
              @PathVariable UUID imageId) { ... }
  }
  ```
  - No API gateway changes needed: POST/DELETE to `/api/v1/events/**` already route to event-management-service and require auth.
- [x] Update `EventService.toEventResponse()` (or equivalent DTO mapper) to populate `teaserImages` list from `EventTeaserImageRepository`

### Phase 7: Frontend — Presentation page (PresentationPage + usePresentationSections)
- [x] Extend `PresentationSection` type (in `usePresentationSections.ts` or its type file) to include:
  ```ts
  export interface PresentationSection {
    type: SectionType;
    key: string;
    session?: Session;
    imageUrl?: string;   // ← new: used by 'teaser-image' section type
  }
  ```
- [x] Add `'teaser-image'` to the `SectionType` union type
- [x] Update `usePresentationSections.ts` to insert **one section per image** after `topic-reveal`:
  ```ts
  // §4 Topic Reveal
  sections.push({ type: 'topic-reveal', key: 'topic-reveal' });

  // §4.x Teaser Images — one slide per image, in displayOrder
  (event.teaserImages ?? []).forEach((img) => {
    sections.push({ type: 'teaser-image', key: `teaser-image-${img.id}`, imageUrl: img.imageUrl });
  });

  // §5 Agenda Preview
  sections.push({ type: 'agenda-preview', key: 'agenda-preview' });
  ```
- [x] Create `web-frontend/src/pages/presentation/slides/TeaserImageSlide.tsx`:
  ```tsx
  // Full-screen teaser image — no overlay, no text, no dark tint (distinct from TopicBackground)
  export function TeaserImageSlide({ imageUrl }: { imageUrl: string }): JSX.Element {
    return (
      <div style={{ position: 'absolute', inset: 0, overflow: 'hidden' }}>
        <img
          src={imageUrl}
          alt=""
          style={{ width: '100%', height: '100%', objectFit: 'cover', display: 'block' }}
        />
      </div>
    );
  }
  ```
  - No animation (consistent with 10.8a approach — animations deferred)
- [x] Update `PresentationPage.tsx` `SectionRenderer`:
  ```tsx
  case 'teaser-image':
    return section.imageUrl
      ? <TeaserImageSlide imageUrl={section.imageUrl} />
      : null;
  ```
  - `section.imageUrl` is populated by `usePresentationSections` from each `TeaserImageItem`
  - The `isSession`, `isAgendaCenter`, `isBreakSection` flags evaluate to `false` for `teaser-image` — no sidebar/agenda shown (full-screen only, correct)

### Phase 8: Frontend — EventSettingsTab upload UI
- [x] Update `web-frontend/src/components/organizer/EventPage/EventSettingsTab.tsx`:
  - Add a "Teaser Images" `Paper` section (below Registration Capacity, above Notifications)
  - **Gallery display**: show existing images as a row of thumbnails (`<Box component="img" sx={{ width: 160, height: 90, objectFit: 'cover', borderRadius: 1 }} />`); each thumbnail has a remove button (icon button, top-right corner, `disabled={isArchived}`)
  - **Upload button**: "+ Add Teaser Image" (disabled when `isArchived` OR `(event.teaserImages?.length ?? 0) >= 10`); triggers hidden `<input type="file" accept="image/jpeg,image/png,image/webp" />`
  - **Limit hint**: when `teaserImages.length >= 10`, show inline text: `t('teaserImage.limitReached')` below the gallery (instead of the upload button)
  - **Upload flow** (3-phase presigned URL):
    ```ts
    // 1. POST /events/{eventCode}/teaser-images/upload-url
    //    body: { contentType: file.type, fileName: file.name }
    //    response: { uploadUrl, s3Key, expiresIn }
    // 2. fetch(uploadUrl, { method: 'PUT', body: file, headers: { 'Content-Type': file.type } })
    // 3. POST /events/{eventCode}/teaser-images/confirm
    //    body: { s3Key }
    //    response: TeaserImageItem { id, imageUrl, displayOrder }
    // 4. invalidate event query keys → refetch → new thumbnail appears in gallery
    ```
  - **Remove flow**: `DELETE /events/{eventCode}/teaser-images/{imageId}` → invalidate event query keys
  - Use `apiClient` (not raw fetch) for all BATbern API calls; use raw fetch only for the S3 presigned PUT
  - Local state: `const [uploading, setUploading] = useState(false)` + `const [teaserError, setTeaserError] = useState<string | null>(null)`
  - `event.teaserImages` typed automatically after type regen (`TeaserImageItem[]`)

### Phase 9: i18n
- [x] Add to `web-frontend/public/locales/de/events.json`:
  ```json
  "teaserImage": {
    "sectionTitle": "Präsentationsbilder",
    "sectionDesc": "Diese Bilder werden nacheinander nach dem Thema und vor der Agenda auf dem Moderationsbildschirm angezeigt.",
    "addBtn": "Bild hinzufügen",
    "removeBtn": "Entfernen",
    "uploading": "Wird hochgeladen...",
    "uploadError": "Upload fehlgeschlagen. Bitte versuche es erneut.",
    "removeError": "Entfernen fehlgeschlagen. Bitte versuche es erneut.",
    "emptyHint": "Noch keine Bilder hochgeladen.",
    "limitReached": "Maximale Anzahl von 10 Bildern erreicht."
  }
  ```
- [x] Add equivalent keys in English to `web-frontend/public/locales/en/events.json`
- [x] **Do NOT add to other locales** (only de + en required per AC 8; other locales fall back to key)

### Phase 10: Tests & validation
- [x] Run failing unit tests → GREEN (includes limit test): `./gradlew :services:event-management-service:test --tests EventTeaserImageServiceTest 2>&1 | tee /tmp/test-10-22-service.log && grep -E "FAIL|ERROR|PASS|BUILD" /tmp/test-10-22-service.log`
- [x] Run integration tests → GREEN: `./gradlew :services:event-management-service:test --tests EventTeaserImageControllerIntegrationTest 2>&1 | tee /tmp/test-10-22-ctrl.log && grep -E "FAIL|ERROR|PASS|BUILD" /tmp/test-10-22-ctrl.log`
- [x] Update `usePresentationSections.test.ts`:
  - Add test: when `event.teaserImages` has 3 items, exactly 3 `teaser-image` sections are inserted (in order, correct keys, correct imageUrls)
  - Add test: when `event.teaserImages` is empty/null, zero `teaser-image` sections appear
- [x] `cd web-frontend && npm run type-check` — zero errors
- [x] `make verify 2>&1 | tee /tmp/verify-10-22.log && grep -E "FAIL|ERROR|BUILD" /tmp/verify-10-22.log` — clean

## Dev Notes

### Flyway version — CRITICAL

Always run before writing the migration file:
```bash
ls services/event-management-service/src/main/resources/db/migration/V*.sql | sort -V | tail -5
```
Story 10.21 (event-photos-gallery) targets V78 or V79. This story must use the next available version after 10.21 is merged.

### ADR-002 compliance — why entity-specific pattern is correct here

ADR-002 (Generic File Upload Service) mandates `GenericLogoService` for uploads where the entity does not yet exist at upload time (circular dependency problem). That ADR was **scoped and clarified on 2026-03-03** to cover entity-creation-with-file scenarios only.

Teaser images are always uploaded to an **existing event** (the event must exist before the organizer can open its Settings tab). Therefore:
- The circular dependency problem does not apply
- `EventTeaserImageService` using direct `S3Presigner` + `S3Client` is **fully ADR-002 compliant**
- This is the same pattern used by `SpeakerProfilePhotoService` and `EventPhotoService`, both of which operate on existing entities

Do **not** route teaser image uploads through `GenericLogoService` — that service lives in `company-user-management-service` and a cross-service call from `event-management-service` would introduce unnecessary coupling.

See: `docs/architecture/ADR-002-generic-file-upload-service.md` → "Scope Clarification" section.

### displayOrder gaps after deletion

When an image is deleted from the middle of the sequence (e.g. positions [0,1,2] → delete 1 → [0,2]), the remaining `displayOrder` values have a gap. This is **intentional and acceptable** — the array is sorted by value, not expected to be contiguous. Do not renumber on delete.

### N+1 query trade-off for event list endpoints

`EventService.toEventResponse()` calls `eventTeaserImageRepository.findByEventCodeOrderByDisplayOrderAsc(eventCode)` per event. For the event list endpoint (`GET /events`), this fires one additional query per event in the result set. At BATbern's data volume (tens of events, not thousands), this is acceptable. If list performance degrades, switch to a batch-load approach (`findByEventCodeInOrderByDisplayOrderAsc(List<String> codes)` grouped by eventCode). Document this trade-off as a known `TODO` in `EventService`.

### Why a separate table (not columns on events)

The original design stored `teaser_image_s3_key` and `teaser_image_url` as nullable columns on `events`. That works for exactly one image. For N images with independent lifecycle (each can be deleted, they have ordering), a child table is the correct relational model — same pattern used by `event_photos` (Story 10.21).

### Backend — EventTeaserImageService pattern

Follow `SpeakerProfilePhotoService` exactly. Key differences from that service:
- `EventTeaserImageRepository` instead of updating `Event` columns
- `displayOrder` computed as `MAX(displayOrder) + 1` at confirm time
- Delete targets the child row (by UUID), not the parent event

**⚠️ Do NOT use `RestClient`** (that's the AI pattern for OpenAI calls). Use `S3Presigner` + `S3Client`.

### Frontend — usePresentationSections.ts section shape

After the change, section order is:
```
welcome → about → committee → topic-reveal → [teaser-image-{id}?]... → agenda-preview → sessions → ... → apero
```

Each `teaser-image` section carries its own `imageUrl` in the section object. The `SectionRenderer` uses `section.imageUrl` directly — no lookup into the event object needed at render time.

`PresentationSection.imageUrl` must be added to the interface. If `PresentationSection` is defined inline in `usePresentationSections.ts`, update it there. If it is in a separate types file, update that file.

### Frontend — removing a teaser image from the gallery

When `DELETE /events/{eventCode}/teaser-images/{imageId}` succeeds, call:
```ts
queryClient.invalidateQueries({ queryKey: ['event', eventCode] });
```
The gallery re-renders from the refreshed `event.teaserImages` list. No local state manipulation needed.

### OpenAPI spec position

Add `teaserImages` field in `EventResponse` after `themeImageUrl` (around line 5646):
```yaml
teaserImages:
  type: array
  nullable: true
  description: |
    Ordered list of teaser images shown as individual slides on the moderator
    presentation page (between topic-reveal and agenda-preview).
    Story 10.22: Event Teaser Images.
  items:
    $ref: '#/components/schemas/TeaserImageItem'
```

New endpoint paths (add after `/events/{eventCode}/ai/theme-image`):
```yaml
/events/{eventCode}/teaser-images/upload-url:
  post:
    tags: [Events]
    operationId: generateTeaserImageUploadUrl
    summary: Get presigned S3 PUT URL for teaser image upload (organizer-only)
    security:
      - bearerAuth: []

/events/{eventCode}/teaser-images/confirm:
  post:
    tags: [Events]
    operationId: confirmTeaserImageUpload
    summary: Confirm upload and persist new teaser image

/events/{eventCode}/teaser-images/{imageId}:
  delete:
    tags: [Events]
    operationId: deleteTeaserImage
    summary: Delete a specific teaser image by ID
```

### Integration test pattern

```java
@Transactional
class EventTeaserImageControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired EventTeaserImageRepository teaserImageRepository;
    @MockBean S3Presigner s3Presigner;
    @MockBean S3Client s3Client;

    // Mock S3Presigner → return fake presigned URL
    // Mock s3Client.headObject → simulate object exists (or throw NoSuchKeyException for missing-object test)
    // Use @WithMockUser(roles = "ORGANIZER") for auth
    // After confirm: assert teaserImageRepository.findByEventCodeOrderByDisplayOrderAsc(eventCode).size() == 1
    // After delete: assert teaserImageRepository.findById(imageId).isEmpty()
}
```

### Type regen commands (run after modifying events-api.openapi.yml)

```bash
# Backend DTOs
./gradlew :services:event-management-service:openApiGenerateEvents

# Frontend types
cd web-frontend && npm run generate:api-types
```

### Key file paths

```
# Backend (new files)
services/event-management-service/src/main/resources/db/migration/V??__create_event_teaser_images.sql
services/event-management-service/src/main/java/ch/batbern/events/domain/EventTeaserImage.java
services/event-management-service/src/main/java/ch/batbern/events/repository/EventTeaserImageRepository.java
services/event-management-service/src/main/java/ch/batbern/events/service/EventTeaserImageService.java
services/event-management-service/src/main/java/ch/batbern/events/controller/EventTeaserImageController.java
services/event-management-service/src/test/java/ch/batbern/events/service/EventTeaserImageServiceTest.java
services/event-management-service/src/test/java/ch/batbern/events/controller/EventTeaserImageControllerIntegrationTest.java

# Backend (modified)
services/event-management-service/src/main/java/ch/batbern/events/service/EventService.java   (toDto mapper: populate teaserImages)

# Frontend (new files)
web-frontend/src/pages/presentation/slides/TeaserImageSlide.tsx

# Frontend (modified)
web-frontend/src/hooks/usePresentationSections.ts     (PresentationSection type + section injection loop)
web-frontend/src/pages/PresentationPage.tsx           (SectionRenderer case 'teaser-image')
web-frontend/src/components/organizer/EventPage/EventSettingsTab.tsx   (gallery upload UI)
web-frontend/public/locales/de/events.json
web-frontend/public/locales/en/events.json

# OpenAPI (commit FIRST — ADR-006)
docs/api/events-api.openapi.yml

# Generated (do not hand-edit)
web-frontend/src/types/generated/events-api.types.ts
```

### Packages for new classes

```
ch.batbern.events.domain.EventTeaserImage
ch.batbern.events.repository.EventTeaserImageRepository
ch.batbern.events.service.EventTeaserImageService
ch.batbern.events.controller.EventTeaserImageController
```

### References

- Epic 10 PRD story definition: [Source: docs/prd/epic-10-additional-stories.md#story-1022]
- SpeakerProfilePhotoService (presigned URL pattern): [Source: services/event-management-service/src/main/java/ch/batbern/events/service/SpeakerProfilePhotoService.java]
- EventPhotoService (multi-image child table pattern): [Source: services/event-management-service/src/main/java/ch/batbern/events/service/EventPhotoService.java]
- EventPhotoRepository (child table repository pattern): [Source: services/event-management-service/src/main/java/ch/batbern/events/repository/EventPhotoRepository.java]
- AwsConfig (S3Presigner bean): [Source: services/event-management-service/src/main/java/ch/batbern/events/config/AwsConfig.java]
- Event.java domain entity: [Source: services/event-management-service/src/main/java/ch/batbern/events/domain/Event.java]
- EventController (routing context): [Source: services/event-management-service/src/main/java/ch/batbern/events/controller/EventController.java]
- PresentationPage.tsx (SectionRenderer): [Source: web-frontend/src/pages/PresentationPage.tsx:403]
- usePresentationSections.ts (section insertion point): [Source: web-frontend/src/hooks/usePresentationSections.ts:60-65]
- EventSettingsTab.tsx (UI pattern): [Source: web-frontend/src/components/organizer/EventPage/EventSettingsTab.tsx]
- presentationService.ts (PresentationEventDetail type): [Source: web-frontend/src/services/presentationService.ts:14]
- Story 10.8a (PresentationPage prerequisite): [Source: _bmad-output/implementation-artifacts/10-8a-moderator-presentation-page.md]
- Story 10.21 (event-photos-gallery — same child table pattern): [Source: _bmad-output/implementation-artifacts/10-21-event-photos-gallery.md]
- ADR-006 (OpenAPI first): [Source: docs/architecture/coding-standards.md]
- CloudFrontUrlBuilder: [Source: shared-kernel/src/main/java/ch/batbern/shared/utils/CloudFrontUrlBuilder.java]

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

- `/tmp/test-10-22-service.log` — EventTeaserImageServiceTest: 8/8 PASSED
- `/tmp/test-10-22-ctrl.log` — EventTeaserImageControllerIntegrationTest: 6/6 PASSED
- `/tmp/verify-run3.log` — make verify: BUILD SUCCESSFUL, exit 0

### Completion Notes List

- Flyway migration used V80 (V79 was taken by story 10.21 event-photos-gallery)
- Checkstyle fix: removed unused `any` import from integration test and unused `eq` import from service test
- EventPage.test.tsx tab count updated from 7 → 8 (Photos tab added in story 10.21 not reflected in test)
- `events-api.types.ts` was regenerated and staged; `make verify` types-check passes
- `usePresentationSections.ts`: teaser images sorted by displayOrder before insertion (not in arrival order)
- `TeaserImageSlide.tsx`: uses `objectFit: 'contain'` (not `cover`) — preserves full image without cropping; black background for letterboxing
- All 10 acceptance criteria met

### File List

**New — Backend:**
- `services/event-management-service/src/main/resources/db/migration/V80__create_event_teaser_images.sql`
- `services/event-management-service/src/main/java/ch/batbern/events/domain/EventTeaserImage.java`
- `services/event-management-service/src/main/java/ch/batbern/events/repository/EventTeaserImageRepository.java`
- `services/event-management-service/src/main/java/ch/batbern/events/service/EventTeaserImageService.java`
- `services/event-management-service/src/main/java/ch/batbern/events/controller/EventTeaserImageController.java`
- `services/event-management-service/src/main/java/ch/batbern/events/exception/TeaserImageLimitExceededException.java`
- `services/event-management-service/src/main/java/ch/batbern/events/exception/TeaserImageNotFoundException.java`
- `services/event-management-service/src/test/java/ch/batbern/events/service/EventTeaserImageServiceTest.java`
- `services/event-management-service/src/test/java/ch/batbern/events/controller/EventTeaserImageControllerIntegrationTest.java`

**New — Frontend:**
- `web-frontend/src/pages/presentation/slides/TeaserImageSlide.tsx`
- `web-frontend/src/hooks/useEventTeaserImages.ts`

**Modified — Backend:**
- `docs/api/events-api.openapi.yml` — TeaserImageItem schema + teaserImages field on EventResponse + 3 new endpoints
- `docs/architecture/ADR-002-generic-file-upload-service.md` — scope clarification
- `services/event-management-service/src/main/java/ch/batbern/events/domain/EventPhoto.java`
- `services/event-management-service/src/main/java/ch/batbern/events/repository/EventRepository.java`
- `services/event-management-service/src/main/java/ch/batbern/events/service/EventPhotoService.java`
- `services/event-management-service/src/test/java/ch/batbern/events/service/EventPhotoServiceTest.java`
- `services/event-management-service/src/main/java/ch/batbern/events/config/SecurityConfig.java`
- `api-gateway/src/main/java/ch/batbern/gateway/config/SecurityConfig.java`

**Modified — Frontend:**
- `web-frontend/src/hooks/usePresentationSections.ts` — SectionType + imageUrl field + teaser-image insertion
- `web-frontend/src/pages/PresentationPage.tsx` — SectionRenderer case 'teaser-image'
- `web-frontend/src/components/organizer/EventPage/EventSettingsTab.tsx` — teaser images gallery + upload UI
- `web-frontend/src/components/organizer/EventPage/__tests__/EventPage.test.tsx` — tab count 7 → 8
- `web-frontend/src/hooks/usePresentationSections.test.ts` — 2 new teaser-image tests
- `web-frontend/src/services/eventApiClient.ts` — 3 new teaser-image API methods
- `web-frontend/src/App.tsx`
- `web-frontend/public/locales/de/events.json` — teaserImage.* keys
- `web-frontend/public/locales/en/events.json` — teaserImage.* keys

**Generated (do not hand-edit):**
- `web-frontend/src/types/generated/events-api.types.ts`
