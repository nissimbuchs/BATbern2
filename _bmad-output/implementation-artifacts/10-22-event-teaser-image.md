# Story 10.22: Event Teaser Image on Moderator Presentation Page

Status: ready-for-dev

## Story

As an **organizer**,
I want to upload a teaser image for an event from the event detail page,
so that it appears on the moderator presentation screen right after we introduce the topic and before the agenda slides — giving the audience a visual mood-setter.

## Acceptance Criteria

1. **V78 migration** runs cleanly; `teaser_image_s3_key` and `teaser_image_url` columns are nullable; existing events unaffected
2. Organizer can upload a teaser image via presigned URL from EventSettingsTab; thumbnail shown in Settings tab after confirmation
3. Organizer can remove teaser image; DB columns cleared, S3 object deleted
4. Presentation page shows a full-screen teaser image slide between the topic-reveal slide and the agenda-preview slide when `teaserImageUrl` is set on the event
5. When no teaser image is set, the presentation page renders identically to pre-story behaviour (slide is omitted entirely — no empty/blank slide inserted)
6. TDD: `EventTeaserImageServiceTest` covers upload-URL generation, confirm upload, delete; integration test verifies DB state
7. OpenAPI spec committed before implementation (ADR-006)
8. i18n: `teaserImage.*` keys in `de/events.json` and `en/events.json`; Type-check passes; Checkstyle passes

## Tasks / Subtasks

### Phase 1: OpenAPI spec (ADR-006 — commit before any implementation code)
- [ ] Add to `docs/api/events-api.openapi.yml`:
  - [ ] `teaserImageUrl` (nullable string/uri) field to `EventResponse` schema (after `themeImageUrl`)
  - [ ] `POST /events/{eventCode}/teaser-image/upload-url` endpoint → `TeaserImageUploadUrlResponse` schema
  - [ ] `POST /events/{eventCode}/teaser-image/confirm` endpoint → body `TeaserImageConfirmRequest { s3Key }` → returns `{ teaserImageUrl }`
  - [ ] `DELETE /events/{eventCode}/teaser-image` endpoint → 204 No Content
  - [ ] New schemas: `TeaserImageUploadUrlResponse { uploadUrl, s3Key, expiresIn }`, `TeaserImageConfirmRequest { s3Key }`
- [ ] Regenerate backend types: `./gradlew :services:event-management-service:openApiGenerateEvents`
- [ ] Regenerate frontend types: `cd web-frontend && npm run generate:api-types`

### Phase 2: Backend — TDD RED (write failing tests first)
- [ ] Write `EventTeaserImageServiceTest.java` (unit) — RED phase:
  - [ ] `generateUploadUrl_whenValidInput_returnsPresignedUrl`: verifies S3Presigner called, s3Key follows `events/{eventCode}/teaser/{uuid}.{ext}` pattern
  - [ ] `confirmUpload_whenS3ObjectExists_persistsUrlToEvent`: verifies HeadObject called, event fields set, event saved
  - [ ] `confirmUpload_whenS3ObjectMissing_throws`: verifies PhotoUploadNotFoundException (or custom equivalent)
  - [ ] `deleteTeaserImage_whenKeyPresent_deletesS3AndClears`: verifies DeleteObject called, fields nulled, event saved
  - [ ] `deleteTeaserImage_whenNoKeySet_noopAndNoS3Call`: verifies no S3 call when s3Key null
- [ ] Write `EventTeaserImageControllerIntegrationTest.java` (integration, extends `AbstractIntegrationTest`) — RED phase:
  - [ ] `POST .../upload-url` returns 200 with `uploadUrl` + `s3Key` (ORGANIZER auth)
  - [ ] `POST .../upload-url` returns 403 for non-ORGANIZER
  - [ ] `POST .../confirm` with valid s3Key returns 200 with `teaserImageUrl`; DB has both columns set
  - [ ] `DELETE` returns 204; DB columns are null after deletion

### Phase 3: Backend — Flyway migration
- [ ] Create `services/event-management-service/src/main/resources/db/migration/V78__add_teaser_image_to_events.sql`:
  ```sql
  -- Story 10.22: Event Teaser Image for Moderator Presentation Page
  ALTER TABLE events ADD COLUMN teaser_image_s3_key TEXT;
  ALTER TABLE events ADD COLUMN teaser_image_url TEXT;
  ```
  - ⚠️ **Version warning**: If Story 10.21 (event-photos-gallery) is implemented first, it may claim V78. Check the latest migration file before coding and use the next available version.

### Phase 4: Backend — Domain & service layer (GREEN phase)
- [ ] Add to `Event.java`:
  ```java
  // Story 10.22: Teaser image for moderator presentation page
  @Column(name = "teaser_image_s3_key", columnDefinition = "TEXT")
  private String teaserImageS3Key;

  @Column(name = "teaser_image_url", length = 1000)
  private String teaserImageUrl;
  ```
- [ ] Create `EventTeaserImageService.java` in `services/event-management-service/src/main/java/ch/batbern/events/service/`:
  - `generateUploadUrl(String eventCode, String contentType, String fileName)` → `TeaserImageUploadUrlResponse`
  - `confirmUpload(String eventCode, String s3Key)` → `String` (teaserImageUrl)
  - `deleteTeaserImage(String eventCode)` → `void`
  - Pattern: identical to `SpeakerProfilePhotoService` (use `S3Presigner` + `S3Client`)
  - Inject `@Value("${aws.s3.bucket-name:batbern-development-company-logos}")` and `@Value("${aws.cloudfront.domain:https://cdn.batbern.ch}")`
  - `CloudFrontUrlBuilder.buildUrl(cloudFrontDomain, bucketName, s3Key)` for URL
  - S3 key pattern: `events/{eventCode}/teaser/{uuid}.{ext}`
  - Expiry: 900s (15 min), same as speaker photo
  - Allowed content types: `image/jpeg`, `image/png`, `image/webp`

### Phase 5: Backend — Controller
- [ ] Create `EventTeaserImageController.java` in `.../controller/`:
  - `@RestController @RequestMapping("/api/v1") @RequiredArgsConstructor`
  - `@PostMapping("/events/{eventCode}/teaser-image/upload-url") @PreAuthorize("hasRole('ORGANIZER')")`
  - `@PostMapping("/events/{eventCode}/teaser-image/confirm") @PreAuthorize("hasRole('ORGANIZER')")`
  - `@DeleteMapping("/events/{eventCode}/teaser-image") @PreAuthorize("hasRole('ORGANIZER')")`
  - No security/routing changes in API gateway needed (POST/DELETE to `/events/**` are already authenticated; no `permitAll` needed)

### Phase 6: Frontend — Presentation page (PresentationPage + usePresentationSections)
- [ ] Update `web-frontend/src/hooks/usePresentationSections.ts`:
  - Add `'teaser-image'` to the `SectionType` union type
  - Insert `{ type: 'teaser-image', key: 'teaser-image' }` after `topic-reveal` section, **only if `event.teaserImageUrl` is truthy**:
    ```ts
    // §4 Topic Reveal
    sections.push({ type: 'topic-reveal', key: 'topic-reveal' });

    // §4.5 Teaser Image (optional — only when teaserImageUrl is set)
    if (event.teaserImageUrl) {
      sections.push({ type: 'teaser-image', key: 'teaser-image' });
    }

    // §5 Agenda Preview
    sections.push({ type: 'agenda-preview', key: 'agenda-preview' });
    ```
- [ ] Create `web-frontend/src/pages/presentation/slides/TeaserImageSlide.tsx`:
  ```tsx
  // Full-screen teaser image with no text overlay
  // Props: { imageUrl: string }
  // Use: position absolute, inset 0, object-fit cover, no dark overlay (unlike TopicBackground)
  ```
- [ ] Update `PresentationPage.tsx` `SectionRenderer`:
  ```tsx
  case 'teaser-image':
    return data.event?.teaserImageUrl
      ? <TeaserImageSlide imageUrl={data.event.teaserImageUrl} />
      : null;
  ```
- [ ] `teaserImageUrl` is already available on `data.event` because `PresentationEventDetail` extends `components['schemas']['Event']` — after type regen, `event.teaserImageUrl` is typed automatically

### Phase 7: Frontend — EventSettingsTab upload UI
- [ ] Update `web-frontend/src/components/organizer/EventPage/EventSettingsTab.tsx`:
  - Add a "Teaser Image" `Paper` section (below Registration Capacity, above Notifications)
  - Use local state: `const [uploading, setUploading] = useState(false)` + `const [teaserError, setTeaserError] = useState<string | null>(null)`
  - Show thumbnail using `<Box component="img" src={event.teaserImageUrl} sx={{ maxWidth: 300, borderRadius: 1 }} />` when URL is set
  - **Upload flow**:
    ```ts
    // 1. POST /events/{eventCode}/teaser-image/upload-url
    //    body: { contentType: file.type, fileName: file.name }
    //    response: { uploadUrl, s3Key, expiresIn }
    // 2. fetch(uploadUrl, { method: 'PUT', body: file, headers: { 'Content-Type': file.type } })
    // 3. POST /events/{eventCode}/teaser-image/confirm
    //    body: { s3Key }
    //    response: { teaserImageUrl }
    // 4. invalidate event query keys → refetch → thumbnail appears
    ```
  - **Remove flow**: `DELETE /events/{eventCode}/teaser-image` → invalidate event query keys
  - Use `apiClient` (not raw fetch) for all BATbern API calls; use raw fetch only for the S3 presigned PUT
  - `event` prop type is `Event | EventDetailUI`; cast via `(event as EventDetailUI).teaserImageUrl` (will be typed after type regen)
  - Disable Upload/Remove buttons while `isArchived` (same guard used for capacity field)

### Phase 8: i18n
- [ ] Add to `web-frontend/public/locales/de/events.json`:
  ```json
  "teaserImage": {
    "sectionTitle": "Präsentationsbild",
    "sectionDesc": "Dieses Bild wird nach dem Thema und vor der Agenda auf dem Moderationsbildschirm angezeigt.",
    "uploadBtn": "Bild hochladen",
    "removeBtn": "Entfernen",
    "uploading": "Wird hochgeladen...",
    "uploadError": "Upload fehlgeschlagen. Bitte versuche es erneut.",
    "removeError": "Entfernen fehlgeschlagen. Bitte versuche es erneut."
  }
  ```
- [ ] Add equivalent keys in English to `web-frontend/public/locales/en/events.json`
- [ ] **Do NOT add to other locales** (only de + en required per AC 8; other locales fall back to key)

### Phase 9: Tests & validation
- [ ] Run failing unit tests → GREEN: `./gradlew :services:event-management-service:test --tests EventTeaserImageServiceTest`
- [ ] Run integration tests → GREEN: `./gradlew :services:event-management-service:test --tests EventTeaserImageControllerIntegrationTest`
- [ ] Update `usePresentationSections.test.ts`: add test that TEASER_IMAGE slide is inserted when `event.teaserImageUrl` is truthy and NOT inserted when null
- [ ] `cd web-frontend && npm run type-check` — zero errors
- [ ] `make verify` (lint + tests) — clean

## Dev Notes

### Flyway version — CRITICAL

**⚠️ Use V78** (current latest is V77 = `create_ai_generation_log.sql`):
- V75 = `add_deregistration_token.sql`
- V76 = `add_deregistration_link_to_confirmation_templates.sql`
- V77 = `create_ai_generation_log.sql`
- **V78 = this story** (unless Story 10.21 "event-photos-gallery" is implemented first — it also targets V78; if so, use V79)

Always run `ls services/event-management-service/src/main/resources/db/migration/V7*.sql` before writing the migration file.

### Backend — EventTeaserImageService pattern

Follow `SpeakerProfilePhotoService` exactly. Key pieces:

```java
@Service
@Transactional
@Slf4j
public class EventTeaserImageService {

    private final S3Presigner s3Presigner;       // for upload-url generation
    private final S3Client s3Client;             // for HeadObject (verify) + DeleteObject
    private final EventRepository eventRepository;
    private final String bucketName;
    private final String cloudFrontDomain;

    private static final long MAX_FILE_SIZE_BYTES = 10L * 1024 * 1024; // 10MB (larger than photo)
    private static final int PRESIGNED_URL_EXPIRATION_SECONDS = 900;   // 15 minutes
    private static final Set<String> ALLOWED_CONTENT_TYPES =
        Set.of("image/jpeg", "image/png", "image/webp");

    // S3 key pattern
    String s3Key = String.format("events/%s/teaser/%s.%s", eventCode, UUID.randomUUID(), ext);

    // Presigned PUT URL
    PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
        .signatureDuration(Duration.ofSeconds(PRESIGNED_URL_EXPIRATION_SECONDS))
        .putObjectRequest(PutObjectRequest.builder()
            .bucket(bucketName).key(s3Key).contentType(contentType).build())
        .build();
    String uploadUrl = s3Presigner.presignPutObject(presignRequest).url().toString();

    // Verify upload
    s3Client.headObject(HeadObjectRequest.builder().bucket(bucketName).key(s3Key).build());

    // Build CloudFront URL
    String cfUrl = CloudFrontUrlBuilder.buildUrl(cloudFrontDomain, bucketName, s3Key);

    // Delete S3 object
    s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucketName).key(s3Key).build());
}
```

**⚠️ Do NOT use `RestClient` (that's the AI pattern for OpenAI). Use `S3Presigner` + `S3Client`.**

### Backend — Controller pattern

```java
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class EventTeaserImageController {

    private final EventTeaserImageService teaserImageService;

    @PostMapping("/events/{eventCode}/teaser-image/upload-url")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<TeaserImageUploadUrlResponse> generateUploadUrl(
            @PathVariable String eventCode,
            @RequestBody TeaserImageUploadUrlRequest request) { ... }

    @PostMapping("/events/{eventCode}/teaser-image/confirm")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<TeaserImageConfirmResponse> confirmUpload(
            @PathVariable String eventCode,
            @RequestBody TeaserImageConfirmRequest request) { ... }

    @DeleteMapping("/events/{eventCode}/teaser-image")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<Void> deleteTeaserImage(@PathVariable String eventCode) { ... }
}
```

**No API gateway changes needed**: POST/DELETE to `/api/v1/events/**` already route to event-management-service and require auth. The new endpoints are organizer-only — no `permitAll` entries needed.

**EMS SecurityConfig** (`event-management-service`): Verify existing security config does not block these paths. Since they're `@PreAuthorize("hasRole('ORGANIZER')")`, they only need to not be blocked at the filter chain level. All existing paths under `/api/v1/events/**` pass through — no changes needed.

### OpenAPI spec — TeaserImageUploadUrlResponse position

Add **after** the existing `AiThemeImageResponse` schema and **after the `themeImageUrl` field** in `EventResponse`. Pattern to follow:

```yaml
# In EventResponse schema (around line 5646, after themeImageUploadId):
teaserImageUrl:
  type: string
  nullable: true
  format: uri
  maxLength: 1000
  description: |
    CloudFront URL for event teaser image shown on moderator presentation page.
    Story 10.22: Event Teaser Image — appears between topic-reveal and agenda slides.
```

New endpoint paths (add after `/events/{eventCode}/ai/theme-image` around line 4560):
```yaml
/events/{eventCode}/teaser-image/upload-url:
  post:
    tags: [Events]
    operationId: generateTeaserImageUploadUrl
    summary: Get presigned S3 PUT URL for teaser image upload (organizer-only)
    security:
      - bearerAuth: []
    ...

/events/{eventCode}/teaser-image/confirm:
  post:
    tags: [Events]
    operationId: confirmTeaserImageUpload
    ...

/events/{eventCode}/teaser-image:
  delete:
    tags: [Events]
    operationId: deleteTeaserImage
    ...
```

### Frontend — usePresentationSections.ts change (minimal)

The section order after the change:
```
welcome → about → committee → topic-reveal → [teaser-image?] → agenda-preview → sessions → ... → apero
```

The `'teaser-image'` type does NOT need a `session` property. `PresentationSection.session` is already optional.

The `isSession`, `isAgendaCenter`, `isBreakSection` flags in `PresentationPage.tsx` are unaffected — `teaser-image` evaluates to `false` for all three, so no sidebar or agenda is shown (correct: full-screen image only).

### Frontend — TeaserImageSlide.tsx (full-screen, no overlay)

```tsx
// Distinct from TopicBackground — that component has a dark overlay (rgba 0,0,0,0.65)
// and renders as a persistent layer. TeaserImageSlide fills the slide canvas with the
// image but NO overlay text and NO dark tint.
export function TeaserImageSlide({ imageUrl }: { imageUrl: string }): JSX.Element {
  return (
    <div style={{ position: 'absolute', inset: 0, overflow: 'hidden' }}>
      <img
        src={imageUrl}
        alt=""
        style={{
          width: '100%',
          height: '100%',
          objectFit: 'cover',
          display: 'block',
        }}
      />
    </div>
  );
}
```

**No animation** — teaser image slide should be static (no Ken Burns, no FLIP). Consistent with approach in 10.8a (animations deferred) and scope of this story.

### Frontend — EventSettingsTab upload inline flow

The `EventSettingsTab` props are `{ event: Event | EventDetailUI, eventCode: string }`. After type regen `event.teaserImageUrl` will be typed. Cast as needed: `(event as { teaserImageUrl?: string | null }).teaserImageUrl`.

Use `useQueryClient()` + `queryClient.invalidateQueries({ queryKey: ['event', eventCode] })` after upload/delete to trigger a refetch and update the thumbnail.

**File input**: Use a hidden `<input type="file" accept="image/jpeg,image/png,image/webp" ref={inputRef} />` triggered by the "Upload" button click. In the onChange handler, call the 3-phase upload function.

### Frontend — type regen commands

```bash
# Backend (generates DTOs in event-management-service)
./gradlew :services:event-management-service:openApiGenerateEvents

# Frontend
cd web-frontend && npm run generate:api-types
```

**Always do this after modifying events-api.openapi.yml.** The frontend `events-api.types.ts` is the single source of truth for event-related TypeScript types.

### Integration test pattern (from AbstractIntegrationTest)

```java
@Transactional
class EventTeaserImageControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired EventRepository eventRepository;
    @MockBean S3Presigner s3Presigner;
    @MockBean S3Client s3Client;

    // Mock S3Presigner to return a fake presigned URL
    // Mock s3Client.headObject to simulate object exists (or throw NoSuchKeyException)
    // Use @WithMockUser(roles = "ORGANIZER") for auth
}
```

### Test output to temp file pattern (CLAUDE.md mandate)

```bash
./gradlew :services:event-management-service:test --tests "EventTeaserImageServiceTest" 2>&1 | tee /tmp/test-10-22-service.log
grep -E "FAIL|ERROR|PASS|BUILD" /tmp/test-10-22-service.log
```

### Previous story learnings (10.21 "event-photos-gallery" — not yet implemented)

Story 10.21 targets the same event-management-service layer and also uses:
- S3 presigned URL upload (same infrastructure)
- `EventPhotoController` + `EventPhotoService` (new)
- V78 Flyway migration

**If 10.21 is implemented before 10.22**: This story's migration must use V79 instead of V78. Confirm by running:
```bash
ls services/event-management-service/src/main/resources/db/migration/V*.sql | sort -V | tail -5
```

### Recent relevant commits

From `git log --oneline`:
```
8809524 fix(10.16): replace Mockito star import with explicit imports
619ba1ad fix(10.16): correct double /api/v1 prefix and gateway routing for AI endpoints
99e34fca feat(10.16): AI-assisted event content creation
```

Story 10.16 (AI-assisted event content) is the most recent analogous backend story. It introduced:
- `AiAssistController` → `@PostMapping("/events/{eventCode}/ai/theme-image")` — uses `RestClient` NOT `S3Presigner` (AI uses a different pattern)
- `BatbernAiService` → downloads DALL-E image bytes and uploads directly to S3 via `S3Client.putObject()`

**For teaser image, do NOT use the AI pattern.** Use the `SpeakerProfilePhotoService` presigned URL pattern instead — the organizer uploads the file directly from the browser to S3.

### Project Structure Notes

**Key file paths:**
```
# Backend
services/event-management-service/src/main/resources/db/migration/V78__add_teaser_image_to_events.sql
services/event-management-service/src/main/java/ch/batbern/events/domain/Event.java
services/event-management-service/src/main/java/ch/batbern/events/service/EventTeaserImageService.java
services/event-management-service/src/main/java/ch/batbern/events/controller/EventTeaserImageController.java
services/event-management-service/src/test/java/ch/batbern/events/service/EventTeaserImageServiceTest.java
services/event-management-service/src/test/java/ch/batbern/events/controller/EventTeaserImageControllerIntegrationTest.java

# Frontend
web-frontend/src/pages/presentation/slides/TeaserImageSlide.tsx   (NEW)
web-frontend/src/hooks/usePresentationSections.ts                  (MODIFY — add 'teaser-image' section type)
web-frontend/src/pages/PresentationPage.tsx                        (MODIFY — SectionRenderer case)
web-frontend/src/components/organizer/EventPage/EventSettingsTab.tsx (MODIFY — upload UI)
web-frontend/public/locales/de/events.json                         (MODIFY — teaserImage.* keys)
web-frontend/public/locales/en/events.json                         (MODIFY — teaserImage.* keys)

# OpenAPI (commit FIRST)
docs/api/events-api.openapi.yml

# Generated (do not hand-edit)
web-frontend/src/types/generated/events-api.types.ts
```

**Package for new service/controller:**
```
ch.batbern.events.service.EventTeaserImageService
ch.batbern.events.controller.EventTeaserImageController
```

### References

- Epic 10 PRD story definition: [Source: docs/prd/epic-10-additional-stories.md#story-1022]
- SpeakerProfilePhotoService (presigned URL pattern): [Source: services/event-management-service/src/main/java/ch/batbern/events/service/SpeakerProfilePhotoService.java]
- AwsConfig (S3Presigner bean): [Source: services/event-management-service/src/main/java/ch/batbern/events/config/AwsConfig.java]
- Event.java domain entity: [Source: services/event-management-service/src/main/java/ch/batbern/events/domain/Event.java]
- EventController (routing context): [Source: services/event-management-service/src/main/java/ch/batbern/events/controller/EventController.java]
- PresentationPage.tsx (SectionRenderer): [Source: web-frontend/src/pages/PresentationPage.tsx:403]
- usePresentationSections.ts (section insertion point): [Source: web-frontend/src/hooks/usePresentationSections.ts:60-65]
- EventSettingsTab.tsx (UI pattern): [Source: web-frontend/src/components/organizer/EventPage/EventSettingsTab.tsx]
- presentationService.ts (PresentationEventDetail type): [Source: web-frontend/src/services/presentationService.ts:14]
- useFileUpload hook (upload pattern): [Source: web-frontend/src/hooks/useFileUpload/useFileUpload.ts]
- Story 10.8a (PresentationPage prerequisite): [Source: _bmad-output/implementation-artifacts/10-8a-moderator-presentation-page.md]
- ADR-006 (OpenAPI first): [Source: docs/architecture/coding-standards.md]
- CloudFrontUrlBuilder: [Source: shared-kernel/src/main/java/ch/batbern/shared/utils/CloudFrontUrlBuilder.java]

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

### Completion Notes List

### File List
